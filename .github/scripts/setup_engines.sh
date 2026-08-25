#!/usr/bin/env bash
# 下载并组装内置引擎到 src/main/resources/engines/<platform>/ 与 shared/
# 用法: bash .github/scripts/setup_engines.sh <windows|linux|macos>
set -euo pipefail

PLATFORM="$1"
RES="src/main/resources/engines"
mkdir -p "$RES/$PLATFORM/rapfi" "$RES/shared/rapfi"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# 解压 7z：Linux/macOS 用 7z（已安装 p7zip），Windows 用自带 bsdtar(tar.exe)
extract_7z() {
  local src="$1" dst="$2"
  mkdir -p "$dst"
  case "$(uname -s)" in
    Darwin) 7z x "$src" "-o$dst" -y >/dev/null ;;
    Linux)  7z x "$src" "-o$dst" -y >/dev/null ;;
    MINGW*|MSYS*|CYGWIN*) tar -xf "$src" -C "$dst" ;;
    *) echo "unsupported system: $(uname -s)" >&2; exit 1 ;;
  esac
}

echo "==> [1/3] Pikafish (中国象棋)"
# 官方 latest release 只打包一个全平台 7z，动态取 URL
PKF_URL="$(curl -fsSL https://api.github.com/repos/official-pikafish/Pikafish/releases/latest \
  | grep -o '"browser_download_url": *"[^"]*\.7z"' | head -1 | sed 's/.*: *"//;s/"$//')"
[ -n "$PKF_URL" ] || { echo "Pikafish release URL not found" >&2; exit 1; }
curl -fL -o "$TMP/pikafish.7z" "$PKF_URL"
extract_7z "$TMP/pikafish.7z" "$TMP/pkf"
cp "$TMP/pkf/pikafish.nnue" "$RES/shared/pikafish.nnue"
case "$PLATFORM" in
  windows) cp "$TMP/pkf/Windows/pikafish-avx2.exe"     "$RES/windows/pikafish.exe" ;;
  linux)   cp "$TMP/pkf/Linux/pikafish-avx2"           "$RES/linux/pikafish" ;;
  macos)   cp "$TMP/pkf/MacOS/pikafish-apple-silicon"  "$RES/macos/pikafish" ;;
esac

echo "==> [2/3] Stockfish (国际象棋)"
case "$PLATFORM" in
  windows)
    curl -fL -o "$TMP/sf.zip" "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-windows-x86-64-avx2.zip"
    tar -xf "$TMP/sf.zip" -C "$TMP"
    cp "$TMP/stockfish/stockfish-windows-x86-64-avx2.exe" "$RES/windows/stockfish.exe" ;;
  linux)
    curl -fL -o "$TMP/sf.tar" "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-ubuntu-x86-64-avx2.tar"
    tar -xf "$TMP/sf.tar" -C "$TMP"
    cp "$TMP/stockfish/stockfish-ubuntu-x86-64-avx2" "$RES/linux/stockfish" ;;
  macos)
    curl -fL -o "$TMP/sf.tar" "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-macos-m1-apple-silicon.tar"
    tar -xf "$TMP/sf.tar" -C "$TMP"
    cp "$TMP/stockfish/stockfish-macos-m1-apple-silicon" "$RES/macos/stockfish" ;;
esac

echo "==> [3/3] Rapfi (五子棋)"
# 官方 250615 release 自带三平台二进制（含 macOS apple-silicon）
curl -fL -o "$TMP/rapfi.7z" "https://github.com/dhbloo/rapfi/releases/download/250615/Rapfi-engine.7z"
extract_7z "$TMP/rapfi.7z" "$TMP/rapfi7z"
cp "$TMP/rapfi7z"/config.toml "$TMP/rapfi7z"/model210901.bin "$TMP/rapfi7z"/mix9svq*.bin.lz4 "$RES/shared/rapfi/"
case "$PLATFORM" in
  windows) cp "$TMP/rapfi7z/pbrain-rapfi-windows-avx2.exe" "$RES/windows/rapfi/pbrain-rapfi.exe" ;;
  linux)   cp "$TMP/rapfi7z/pbrain-rapfi-linux-clang-avx2" "$RES/linux/rapfi/pbrain-rapfi" ;;
  macos)   cp "$TMP/rapfi7z/pbrain-rapfi-macos-apple-silicon" "$RES/macos/rapfi/pbrain-rapfi" ;;
esac

if [[ "$PLATFORM" != "windows" ]]; then
  chmod +x "$RES/$PLATFORM"/pikafish "$RES/$PLATFORM"/stockfish "$RES/$PLATFORM"/rapfi/pbrain-rapfi
fi

echo "==> 引擎组装完成: $PLATFORM"
