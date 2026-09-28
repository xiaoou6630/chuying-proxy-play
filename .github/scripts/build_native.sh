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

# Anonymous GitHub API calls are rate limited per IP; the shared runner IP is
# often already exhausted, which showed up as "curl: (56) ... 403" while fetching
# the Pikafish net and failed the whole build. Use the workflow token when present.
api_curl() {
    if [ -n "${GH_TOKEN:-}" ]; then
        curl -fsSL --retry 3 --retry-delay 2 -H "Authorization: Bearer $GH_TOKEN" "$@"
    else
        curl -fsSL --retry 3 --retry-delay 2 "$@"
    fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 7z extraction: Linux/macOS use p7zip, Windows uses the system bsdtar (tar.exe)
extract_7z() {
    local src="$1" dst="$2"
    mkdir -p "$dst"
    case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*) /c/Windows/system32/tar.exe -xf "$src" -C "$dst" ;;
        *) 7z x "$src" "-o$dst" -y >/dev/null ;;
    esac
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

# Stockfish embeds its NNUE nets at compile time (INCBIN); fetch them.
# Without them clang hard-errors and the engine cannot evaluate anyway.
log "downloading stockfish NNUE nets"
for net in $(grep -rhoE 'nn-[0-9a-f]{12}\.nnue' "$ENGINES/stockfish/src" | sort -u); do
    curl -fsSL "https://tests.stockfishchess.org/api/nn/$net" -o "$ENGINES/stockfish/src/$net"
    echo "$net $(stat -c%s "$ENGINES/stockfish/src/$net" 2>/dev/null || wc -c < "$ENGINES/stockfish/src/$net") bytes"
done
ls -la "$ENGINES/stockfish/src/"*.nnue

# Pikafish embeds its net as src/pikafish.nnue (EvalFileDefaultName) and refuses to
# start without it. No direct file URL exists, so take it from the release 7z.
log "downloading pikafish NNUE net"
PKF_URL="$(api_curl https://api.github.com/repos/official-pikafish/Pikafish/releases/latest \
  | grep -o '"browser_download_url": *"[^"]*\.7z"' | head -1 | sed 's/.*: *"//;s/"$//')"
[ -n "$PKF_URL" ] || { log "ERROR: pikafish release url not found"; exit 1; }
api_curl -L -o "$TMP/pikafish.7z" "$PKF_URL"
extract_7z "$TMP/pikafish.7z" "$TMP/pkf"
cp "$TMP/pkf/pikafish.nnue" "$ENGINES/pikafish/src/pikafish.nnue"
ls -la "$ENGINES/pikafish/src/pikafish.nnue"

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

# MSVC: std::_Unsigned128 (Pikafish's u128) lacks the implicit operator bool
# that gcc's unsigned __int128 has. static_cast<bool> is portable everywhere.
sed_inplace \
    's/bool kingAttacks = attackers & pieces(KING);/bool kingAttacks = static_cast<bool>(attackers \& pieces(KING));/' \
    "$ENGINES/pikafish/src/position.cpp"

# Windows: current Stockfish/Pikafish replace the argc/argv handed to their UCI
# engine with the *process* command line (GetCommandLineW()). Inside the JVM that
# is the Minecraft launcher's command line, which the engine then executes as a
# single UCI command (and quits, because "argc > 1" means one-shot). Disable the
# override so the engine keeps our argv: argc == 1 makes it read the redirected
# std::cin stream instead.
for f in "$ENGINES/stockfish/src/misc.cpp" "$ENGINES/pikafish/src/misc.cpp"; do
    [ -f "$f" ] || continue
    if grep -q 'CommandLineToArgvW(GetCommandLineW()' "$f"; then
        sed_inplace 's/CommandLineToArgvW(GetCommandLineW(), &wargc)/nullptr/' "$f"
        log "patched Windows process-command-line override: $f"
    fi
done

# ---------------------------------------------------------------------------
# 3. CMake configure + build (engine sources globbed by native/CMakeLists.txt)
# ---------------------------------------------------------------------------
CMAKE_ARGS=(-S native -B native/build -DCMAKE_BUILD_TYPE=Release)
# Rapfi: lock SIMD for portability (its own CMake auto-detects host with -march=native).
# SSE x86 only; on ARM everything off (POC correctness first).
if [ "$PLATFORM" = "macos" ]; then
    CMAKE_ARGS+=(-DUSE_SSE=OFF -DUSE_AVX2=OFF -DUSE_AVX512=OFF -DUSE_BMI2=OFF
                 -DUSE_VNNI=OFF -DUSE_NEON=OFF -DUSE_NEON_DOTPROD=OFF
                 -DUSE_WASM_SIMD=OFF -DUSE_WASM_SIMD_RELAXED=OFF
                 -DCMAKE_OSX_ARCHITECTURES=arm64)
else
    CMAKE_ARGS+=(-DUSE_SSE=ON -DUSE_AVX2=OFF -DUSE_AVX512=OFF -DUSE_BMI2=OFF
                 -DUSE_VNNI=OFF -DUSE_NEON=OFF -DUSE_NEON_DOTPROD=OFF
                 -DUSE_WASM_SIMD=OFF -DUSE_WASM_SIMD_RELAXED=OFF)
fi

# Windows: use MSYS2 MinGW-w64 gcc instead of MSVC.
# MSVC does not honour INCBIN (nets silently not embedded) and the resulting
# build crashes inside NNUE evaluation; it also breaks Pikafish's static init.
# MinGW has the same compiler semantics as the proven linux/macos builds.
if [ "$PLATFORM" = "windows" ]; then
    # setup-msys2 puts the toolchain on PATH; fall back to well-known locations.
    GXX_BIN=""
    for name in g++.exe g++; do
        if command -v "$name" >/dev/null 2>&1; then
            GXX_BIN="$(cd "$(dirname "$(command -v "$name")")" && pwd)"
            break
        fi
    done
    if [ -z "$GXX_BIN" ]; then
        for cand in /c/msys64/mingw64/bin /d/a/msys64/mingw64/bin /c/tools/msys64/mingw64/bin; do
            if [ -x "$cand/g++.exe" ]; then GXX_BIN="$cand"; break; fi
        done
    fi
    [ -n "$GXX_BIN" ] || { log "ERROR: MinGW-w64 g++ not found; PATH=$PATH"; exit 1; }
    log "mingw toolchain: $GXX_BIN"
    export PATH="$GXX_BIN:$PATH"
    CMAKE_ARGS+=(-G Ninja
                 -DCMAKE_C_COMPILER="$GXX_BIN/gcc.exe"
                 -DCMAKE_CXX_COMPILER="$GXX_BIN/g++.exe")
fi

log "cmake configure: ${CMAKE_ARGS[*]}"
cmake "${CMAKE_ARGS[@]}"
log "cmake build"
case "$PLATFORM" in
    windows) cmake --build native/build --config Release --parallel ;;
    *)       cmake --build native/build --parallel ;;
esac

# ---------------------------------------------------------------------------
# 4. Collect outputs, strip symbols, verify runtime dependencies
# ---------------------------------------------------------------------------
case "$PLATFORM" in
    windows) find native/build -name 'chuying_*.dll'   -exec cp {} native/dist/ \; ;;
    linux)   find native/build -name 'chuying_*.so'    -exec cp {} native/dist/ \; ;;
    macos)   find native/build -name 'chuying_*.dylib' -exec cp {} native/dist/ \; ;;
esac
[ -n "$(ls native/dist/chuying_* 2>/dev/null)" ] || { log "ERROR: no chuying_* outputs"; exit 1; }

log "stripping symbols"
case "$PLATFORM" in
    macos) find native/dist -maxdepth 1 -type f -name 'chuying_*' -exec strip -x {} \; || true ;;
    *)     find native/dist -maxdepth 1 -type f -name 'chuying_*' -exec strip --strip-unneeded {} \; || true ;;
esac

# The shipped library must not need the MinGW runtime DLLs at load time: they are
# absent both on the CI runners and on end users' machines, and a missing
# dependency makes System.load() fail inside the JVM.
if [ "$PLATFORM" = "windows" ]; then
    log "checking runtime dependencies"
    bad=0
    for f in native/dist/chuying_*.dll; do
        objdump -p "$f" | grep -i 'DLL Name' || true
        if objdump -p "$f" | grep -qiE 'libwinpthread|libstdc\+\+|libgcc'; then
            log "ERROR: $f still imports the MinGW runtime"
            bad=1
        fi
    done
    [ "$bad" -eq 0 ] || exit 1
fi

ls -la native/dist/

cat > native/dist/BUILD_INFO.txt <<EOF
platform: $PLATFORM
built_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)
stockfish: https://github.com/official-stockfish/Stockfish $SF_SHA (GPL-3.0); NNUE nets embedded at build time
pikafish:  https://github.com/official-pikafish/Pikafish $PF_SHA (GPL-3.0)
pikafish_net_sha256: $(sha256sum "$ENGINES/pikafish/src/pikafish.nnue" 2>/dev/null | cut -d' ' -f1)
rapfi:     https://github.com/dhbloo/rapfi $RF_SHA (GPL-3.0); built single-threaded (NO_MULTI_THREADING)
bridge:    native/src (GPL-3.0), patched engine_main linkage, in-process streams
EOF

log "done"
