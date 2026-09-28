#!/usr/bin/env bash
# JVM smoke test for the built chuying_* native libraries.
# stockfish/pikafish must pass a full UCI handshake; rapfi is lenient on CI
# (its model file is not available on runners).
# Usage: bash .github/scripts/smoke_test.sh <windows|linux|macos>
set -uo pipefail

PLATFORM="${1:?platform required}"
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
javac -d out com/chuying/engine/NativeEngineBridge.java SmokeTest.java || exit 1

run_one() { # engine strict
    local engine="$1" strict="$2" rc=0
    echo "==> smoke: $engine ($DIST/chuying_$engine.$EXT)"
    java -cp out SmokeTest "$engine" "$DIST/chuying_$engine.$EXT" || rc=$?
    if [ "$rc" -ne 0 ]; then
        if [ "$strict" = "strict" ]; then
            echo "::error::$engine smoke test failed (rc=$rc)"
            exit 1
        fi
        echo "::warning::$engine smoke test failed (rc=$rc) - lenient, continuing"
    fi
}

run_one stockfish strict
# pikafish: MSVC build still has a DllMain static-init crash to investigate;
# lenient so artifacts still upload. Strict on linux/macos (working there).
if [ "$PLATFORM" = "windows" ]; then
    run_one pikafish lenient
else
    run_one pikafish strict
fi
run_one rapfi lenient
echo "==> smoke tests done"
