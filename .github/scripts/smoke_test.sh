#!/usr/bin/env bash
# JVM smoke test for the built chuying_* native libraries.
# Loads the library in-process and runs a real protocol handshake + search, which
# is the only way to catch a broken NNUE build (a handshake alone passes anyway).
# rapfi is lenient: its model file is not available on the CI runners.
# Usage: bash .github/scripts/smoke_test.sh <windows|linux|macos> [stockfish|pikafish|rapfi]
set -uo pipefail

PLATFORM="${1:?platform required}"
ONLY="${2:-}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIST="$ROOT/native/dist"

case "$PLATFORM" in
    windows) EXT=dll ;;
    linux)   EXT=so ;;
    macos)   EXT=dylib ;;
    *) echo "unknown platform: $PLATFORM"; exit 1 ;;
esac

cd "$ROOT/native/java-test"
mkdir -p out
echo "==> javac"
javac -d out ../../src/main/java/com/chuying/engine/CChessNativeBridge.java ../../src/main/java/com/chuying/engine/WChessNativeBridge.java ../../src/main/java/com/chuying/engine/GomokuNativeBridge.java SmokeTest.java || exit 1

# Pikafish loads pikafish.nnue at runtime and refuses to initialise without it, so
# drop the net we downloaded for the build next to the test (the mod ships it in
# the jar). Stockfish embeds its nets, nothing to do there.
if [ -f "$ROOT/native/engines/pikafish/src/pikafish.nnue" ]; then
    cp "$ROOT/native/engines/pikafish/src/pikafish.nnue" ./pikafish.nnue
fi

run_one() { # engine strict
    local engine="$1" strict="$2" rc=0
    echo "==> smoke start: $engine ($DIST/chuying_$engine.$EXT)"
    java -cp out SmokeTest "$engine" "$DIST/chuying_$engine.$EXT"
    rc=$?
    echo "==> smoke end: $engine rc=$rc"
    if [ "$rc" -ne 0 ]; then
        if [ "$strict" = "strict" ]; then
            echo "::error::$engine smoke test failed (rc=$rc)"
            exit 1
        fi
        echo "::warning::$engine smoke test failed (rc=$rc) - lenient, continuing"
    fi
}

run_engine() { # engine strict
    [ -n "$ONLY" ] && [ "$ONLY" != "$1" ] && return 0
    run_one "$1" "$2"
}

run_engine stockfish strict
run_engine pikafish strict
run_engine rapfi lenient
echo "==> smoke tests done"
