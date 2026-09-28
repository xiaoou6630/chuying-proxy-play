#!/usr/bin/env bash
# Build the chuying JNI native engine libraries on CI (one lib per engine).
# Steps: clone engine sources -> rename main() -> compile sources directly
# into chuying_<engine> shared libs alongside the bridge (no engine CMake).
# No OS process is spawned; engines run in-process via redirected streams.
# Usage: bash .github/scripts/build_native.sh <windows|linux|macos>
set -euo pipefail

PLATFORM="${1:?platform required}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
ENGINES="native/engines"
mkdir -p "$ENGINES" native/dist

log() { echo "==> $*"; }

# portable in-place sed: GNU needs -i, BSD (macOS) needs -i ''
sed_inplace() {
    if sed --version >/dev/null 2>&1; then /usr/bin/sed -i "$@"; else /usr/bin/sed -i '' "$@"; fi
}

git_clone() { # url dir [ref]
    local url="$1" dir="$2" ref="${3:-}"
    rm -rf "$dir"
    if [ -n "$ref" ]; then
        git clone -q --depth 1 --branch "$ref" "$url" "$dir"
    else
        git clone -q --depth 1 "$url" "$dir"
    fi
    echo "$dir @ $(git -C "$dir" rev-parse HEAD)"
}

# ---------------------------------------------------------------------------
# 1. Clone engine sources (GPL-3.0; sources fetched at build time, not in git)
# ---------------------------------------------------------------------------
log "cloning engines"
SF_SHA=$(git_clone https://github.com/official-stockfish/Stockfish.git "$ENGINES/stockfish" sf_17.1 | awk '{print $NF}')
PF_SHA=$(git_clone https://github.com/official-pikafish/Pikafish.git  "$ENGINES/pikafish"  ""        | awk '{print $NF}')
RF_SHA=$(git_clone https://github.com/dhbloo/rapfi.git                "$ENGINES/rapfi"      250615    | awk '{print $NF}')

# ---------------------------------------------------------------------------
# 2. Patch CMakeLists: add_executable -> add_library STATIC (Rapfi uses its
#    own CMakeLists with bundled externals; Stockfish/Pikafish have none)
# ---------------------------------------------------------------------------
log "patching CMakeLists (add_executable -> add_library STATIC)"
find "$ENGINES" -name CMakeLists.txt | while read -r f; do
    if grep -q 'add_executable(' "$f"; then
        sed_inplace 's/add_executable(\([A-Za-z0-9_.-]*\)/add_library(\1 STATIC/g' "$f"
        echo "patched: $f"
    fi
done

# ---------------------------------------------------------------------------
# 3. Patch: int main(...) -> engine_main(...) so the engine can run in-thread.
#    Pikafish keeps extra universal-entry mains -> disable them.
# ---------------------------------------------------------------------------
rename_mains() { # dir want_substring
    local dir="$1" want="$2" files=() f assigned=0
    while IFS= read -r f; do files+=("$f"); done < <(grep -rl 'int main(' "$dir" --include='*.cpp' || true)
    # prefer the file whose path/name matches want (e.g. pbrain)
    local ordered=()
    for f in "${files[@]:-}"; do [ -n "$f" ] || continue; [[ "$f" == *"$want"* ]] && ordered+=("$f"); done
    for f in "${files[@]:-}"; do [ -n "$f" ] || continue; [[ "$f" == *"$want"* ]] || ordered+=("$f"); done
    for f in "${ordered[@]:-}"; do
        if [ "$assigned" -eq 0 ]; then
            sed_inplace 's/int main(int argc, char\* argv\[\])/int engine_main(int argc, char* argv[])/g;
                    s/int main(int argc, char \*\*argv)/int engine_main(int argc, char** argv)/g;
                    s/int main(int argc, char\*\* argv)/int engine_main(int argc, char** argv)/g;
                    s/int main(int argc, char \*argv\[\])/int engine_main(int argc, char* argv[])/g;
                    s/int main()/int engine_main(int argc, char* argv[])/g' "$f"
            log "engine_main <- $f"
            assigned=1
        else
            sed_inplace 's/int main(int argc, char\* argv\[\])/int engine_main_disabled(int argc, char* argv[])/g;
                    s/int main(int argc, char \*\*argv)/int engine_main_disabled(int argc, char** argv)/g;
                    s/int main(int argc, char\*\* argv)/int engine_main_disabled(int argc, char** argv)/g;
                    s/int main(int argc, char \*argv\[\])/int engine_main_disabled(int argc, char* argv[])/g;
                    s/int main()/int engine_main_disabled(int argc, char* argv[])/g' "$f"
            log "engine_main_disabled <- $f"
        fi
    done
    [ "$assigned" -eq 1 ] || { log "ERROR: no main() patched in $dir"; exit 1; }
}

rename_mains "$ENGINES/stockfish/src" main
rename_mains "$ENGINES/pikafish/src"  main.cpp
rename_mains "$ENGINES/rapfi/Rapfi"   pbrain

# ---------------------------------------------------------------------------
# 3. CMake configure + build (engine sources globbed by native/CMakeLists.txt)
# ---------------------------------------------------------------------------
CMAKE_ARGS=(-S native -B native/build -DCMAKE_BUILD_TYPE=Release)
# Rapfi: lock SIMD to SSE baseline (its own CMake auto-detects host with -march=native)
CMAKE_ARGS+=(-DUSE_SSE=ON -DUSE_AVX2=OFF -DUSE_AVX512=OFF -DUSE_BMI2=OFF
             -DUSE_VNNI=OFF -DUSE_NEON=OFF -DUSE_NEON_DOTPROD=OFF
             -DUSE_WASM_SIMD=OFF -DUSE_WASM_SIMD_RELAXED=OFF)
if [ "$PLATFORM" = "macos" ]; then
    CMAKE_ARGS+=(-DCMAKE_OSX_ARCHITECTURES=arm64)
fi

log "cmake configure: ${CMAKE_ARGS[*]}"
cmake "${CMAKE_ARGS[@]}"
log "cmake build"
case "$PLATFORM" in
    windows) cmake --build native/build --config Release --parallel ;;
    *)       cmake --build native/build --parallel ;;
esac

# ---------------------------------------------------------------------------
# 4. Collect outputs + GPL build info
# ---------------------------------------------------------------------------
case "$PLATFORM" in
    windows) find native/build -name 'chuying_*.dll'   -exec cp {} native/dist/ \; ;;
    linux)   find native/build -name 'chuying_*.so'    -exec cp {} native/dist/ \; ;;
    macos)   find native/build -name 'chuying_*.dylib' -exec cp {} native/dist/ \; ;;
esac
ls -la native/dist/
[ -n "$(ls native/dist/chuying_* 2>/dev/null)" ] || { log "ERROR: no chuying_* outputs"; exit 1; }

cat > native/dist/BUILD_INFO.txt <<EOF
platform: $PLATFORM
built_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)
stockfish: https://github.com/official-stockfish/Stockfish $SF_SHA (GPL-3.0)
pikafish:  https://github.com/official-pikafish/Pikafish $PF_SHA (GPL-3.0)
rapfi:     https://github.com/dhbloo/rapfi $RF_SHA (GPL-3.0)
bridge:    native/src (GPL-3.0), patched engine_main linkage, in-process streams
EOF
log "done"
