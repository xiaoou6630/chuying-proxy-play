#!/usr/bin/env bash
# Build the chuying JNI native engine libraries on CI (one lib per engine).
# Steps: clone engine sources -> rename main() -> link as static libs into
# chuying_<engine> shared libs alongside the bridge. No OS process involved.
# Usage: bash .github/scripts/build_native.sh <windows|linux|macos>
set -euo pipefail

PLATFORM="${1:?platform required}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
ENGINES="native/engines"
mkdir -p "$ENGINES" native/dist

log() { echo "==> $*"; }

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
# 2. Patch: add_executable -> add_library STATIC (link engines into our libs)
# ---------------------------------------------------------------------------
log "patching CMakeLists (add_executable -> add_library STATIC)"
find "$ENGINES" -name CMakeLists.txt | while read -r f; do
    if grep -q 'add_executable(' "$f"; then
        sed -i 's/add_executable(\([A-Za-z0-9_.-]*\)/add_library(\1 STATIC/g' "$f"
        echo "patched: $f"
    fi
done

# ---------------------------------------------------------------------------
# 3. Patch: int main(...) -> engine_main(...) so the engine can run in-thread.
#    Rapfi builds two mains (piskvork + pbrain): we keep the pbrain one.
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
            sed -i 's/int main(int argc, char\* argv\[\])/int engine_main(int argc, char* argv[])/g;
                    s/int main(int argc, char \*\*argv)/int engine_main(int argc, char** argv)/g;
                    s/int main(int argc, char\*\* argv)/int engine_main(int argc, char** argv)/g;
                    s/int main(int argc, char \*argv\[\])/int engine_main(int argc, char* argv[])/g;
                    s/int main()/int engine_main(int argc, char* argv[])/g' "$f"
            log "engine_main <- $f"
            assigned=1
        else
            sed -i 's/int main(int argc, char\* argv\[\])/int engine_main_disabled(int argc, char* argv[])/g;
                    s/int main(int argc, char \*\*argv)/int engine_main_disabled(int argc, char** argv)/g;
                    s/int main(int argc, char\*\* argv)/int engine_main_disabled(int argc, char** argv)/g;
                    s/int main(int argc, char \*argv\[\])/int engine_main_disabled(int argc, char* argv[])/g;
                    s/int main()/int engine_main_disabled(int argc, char* argv[])/g' "$f"
            log "engine_main_disabled <- $f"
        fi
    done
    [ "$assigned" -eq 1 ] || { log "ERROR: no main() patched in $dir"; exit 1; }
}

rename_mains "$ENGINES/stockfish" main
rename_mains "$ENGINES/pikafish"  main
rename_mains "$ENGINES/rapfi"     pbrain

# ---------------------------------------------------------------------------
# 4. Extract target names from patched CMakeLists into gen_targets.cmake
# ---------------------------------------------------------------------------
pick_target() { # dir prefer
    local all t hit=""
    all=$(find "$1" -name CMakeLists.txt -exec grep -hoE 'add_library\([A-Za-z0-9_.-]+ STATIC' {} + \
        | sed 's/add_library(//;s/ STATIC//' | sort -u)
    [ -n "$all" ] || { log "ERROR: no STATIC targets found in $1"; exit 1; }
    for t in $all; do
        case "$t" in *"$2"*) hit="$t"; break ;; esac
    done
    [ -n "$hit" ] || hit=$(echo "$all" | head -1)
    echo "$hit"
}

cat > native/gen_targets.cmake <<EOF
set(SF_TARGET "$(pick_target "$ENGINES/stockfish" stockfish)")
set(PF_TARGET "$(pick_target "$ENGINES/pikafish"  pikafish)")
set(RF_TARGET "$(pick_target "$ENGINES/rapfi"     pbrain)")
EOF
log "targets: $(tr '\n' ' ' < native/gen_targets.cmake)"

# ---------------------------------------------------------------------------
# 5. CMake configure + build
# ---------------------------------------------------------------------------
CMAKE_ARGS=(-S native -B native/build -DCMAKE_BUILD_TYPE=Release)
if [ "$PLATFORM" = "macos" ]; then
    # clang has no built-in OpenMP; rapfi links it. Homebrew libomp on arm64 runner.
    brew list libomp >/dev/null 2>&1 || brew install libomp
    OMP_PREFIX="$(brew --prefix libomp)"
    CMAKE_ARGS+=(
        -DOpenMP_CXX_FLAGS="-Xpreprocessor -fopenmp -I${OMP_PREFIX}/include"
        -DOpenMP_CXX_LIB_NAMES=omp
        -DOpenMP_omp_LIBRARY="${OMP_PREFIX}/lib/libomp.dylib"
    )
fi

log "cmake configure: ${CMAKE_ARGS[*]}"
cmake "${CMAKE_ARGS[@]}"
log "cmake build"
case "$PLATFORM" in
    windows) cmake --build native/build --config Release --parallel ;;
    *)       cmake --build native/build --parallel ;;
esac

# ---------------------------------------------------------------------------
# 6. Collect outputs + GPL build info
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
