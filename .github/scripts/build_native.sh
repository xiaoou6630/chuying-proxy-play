#!/usr/bin/env bash
# Build the chuying JNI native engine libraries on CI (one lib per engine).
# Steps: clone engine sources -> rename main() -> compile sources directly
# into chuying_<engine> shared libs alongside the bridge (no engine CMake).
# No OS process is spawned; engines run in-process via redirected streams.
#
# 3.0: one jar for every OS. macOS builds a UNIVERSAL binary (arm64 + x86_64)
# so Intel Macs stop getting UnsatisfiedLinkError; the platform directory name
# is os-arch (macos-universal / windows-x86_64 / linux-x86_64) to match
# EngineExtractor.platform().
# Usage: bash .github/scripts/build_native.sh <windows|linux|macos>
set -euo pipefail

PLATFORM="${1:?platform required}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
ENGINES="native/engines"
mkdir -p "$ENGINES" native/dist

# 平台目录名（与 CMakeLists 的 CHUYING_PLATFORM 一致）
case "$PLATFORM" in
    windows) PLAT_DIR="windows-x86_64" ;;
    linux)   PLAT_DIR="linux-x86_64" ;;
    macos)   PLAT_DIR="macos-universal" ;;
    *) echo "ERROR: unknown platform $PLATFORM"; exit 1 ;;
esac
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
    if [ -z "$ref" ]; then
        git clone -q --depth 1 "$url" "$dir"
    elif [[ "$ref" =~ ^[0-9a-f]{40}$ ]]; then
        # 完整 40 位 SHA：--branch 不接受 commit 哈希（"Remote branch ... not found"），
        # 必须 init + fetch 指定 commit。上游未打 tag 时这是唯一固定版本的办法。
        mkdir -p "$dir"
        git -C "$dir" init -q
        git -C "$dir" remote add origin "$url"
        git -C "$dir" fetch -q --depth 1 origin "$ref"
        git -C "$dir" checkout -q FETCH_HEAD
    else
        # 分支名或 tag
        git clone -q --depth 1 --branch "$ref" "$url" "$dir"
    fi
    echo "$dir @ $(git -C "$dir" rev-parse HEAD)"
}

# ---------------------------------------------------------------------------
# 1. Clone engine sources (GPL-3.0; sources fetched at build time, not in git)
# ---------------------------------------------------------------------------
log "cloning engines"
SF_SHA=$(git_clone https://github.com/official-stockfish/Stockfish.git "$ENGINES/stockfish" sf_17.1 | awk '{print $NF}')
# Pikafish **必须固定 commit**：上游 master 随时会变 NNUE 架构，而 pikafish.nnue
# 权重是从 release 包里取的、架构与引擎强绑定。3.0 首次构建时就踩到了：
#   2.1 用 b562d6a（2026-09-23）→ 权重加载正常
#   3.0 拉到 8d8db92（2026-11-15）→ NNUE 架构不匹配，引擎在 isready 阶段卡死，
#                                整个中国象棋代打不可用
# 固定为 2.1 验证过的 b562d6a，保证引擎与权重配套。
PF_SHA=$(git_clone https://github.com/official-pikafish/Pikafish.git  "$ENGINES/pikafish"  b562d6aeac5401879e973dc53ddb56053f07bb6a | awk '{print $NF}')
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

# GNU Go（围棋，GPL-3.0-or-later）：稳定版 3.8 从 ftp.gnu.org 取 tar 包并锁定 sha256。
# 与另外三个引擎不同：它不进 native/CMakeLists 的 chuying_bridge()（文本协议桥），
# 而是编译成静态库，由 native/src/gnugo_bridge.cpp 直接调它的 C API（进程内、无子进程）。
log "fetching GNU Go 3.8"
GNUG0_TARBALL="https://ftp.gnu.org/gnu/gnugo/gnugo-3.8.tar.gz"
curl -fsSL --retry 3 --retry-delay 2 "$GNUG0_TARBALL" -o "$TMP/gnugo-3.8.tar.gz"
GNUG0_TAR_SHA=$(sha256sum "$TMP/gnugo-3.8.tar.gz" | cut -d' ' -f1)
rm -rf "$ENGINES/gnugo"
tar -xzf "$TMP/gnugo-3.8.tar.gz" -C "$ENGINES"
mv "$ENGINES/gnugo-3.8" "$ENGINES/gnugo"
GNUG0_SHA="gnugo-3.8 tar sha256=$GNUG0_TAR_SHA"

# 可执行文件 -> 静态库（GNU Go 用自己的大写 ADD_EXECUTABLE，不在下面那个小写 sed 的覆盖范围）
sed_inplace 's/ADD_EXECUTABLE(gnugo/ADD_LIBRARY(gnugo STATIC/' "$ENGINES/gnugo/interface/CMakeLists.txt"
grep -q 'ADD_LIBRARY(gnugo STATIC' "$ENGINES/gnugo/interface/CMakeLists.txt" \
    || { log "ERROR: gnugo ADD_EXECUTABLE -> STATIC patch failed"; exit 1; }

# CMake >= 3 禁止在 configure 期读取目标的 LOCATION 属性（CMP0026），而 GNU Go 3.8 正是
# 用它取那几个"生成 pattern 源码"的小工具（mkpat/mkeyes/mkmcpat/joseki/uncompress_fuseki）
# 的可执行文件路径。改成生成器表达式 $<TARGET_FILE:...>（ADD_CUSTOM_COMMAND 的 COMMAND
# 支持生成器表达式），语义完全一致。
sed_inplace -E \
    's/GET_TARGET_PROPERTY\(([A-Za-z0-9_]+)[[:space:]]+([A-Za-z0-9_]+)[[:space:]]+LOCATION\)/SET(\1 $<TARGET_FILE:\2>)/' \
    "$ENGINES/gnugo/patterns/CMakeLists.txt"
if grep -q 'GET_TARGET_PROPERTY' "$ENGINES/gnugo/patterns/CMakeLists.txt"; then
    log "ERROR: gnugo LOCATION(CMP0026) patch failed"
    exit 1
fi

# ---------------------------------------------------------------------------
# GNU Go 3.8 自身的三处老 C 缺陷（我们打的补丁；GPL-3.0-or-later，patch 脚本公开可复现）
#   实测：只在 macOS/arm64 上必现 abort —— Linux 只是靠"未初始化内存恰好是 0"侥幸通过。
#   用 CI 里的 execinfo 调用栈定位到：
#     findstones(垃圾位置) <- get_aa_value/get_aa_status <- gg_sort <- atari_atari
#   1) aa_init_moves() 只把 attacks[0].move 设成哨兵，target[] 其余槽位是未初始化栈内存，
#      而 aa_sort_moves()/get_aa_value() 会把它们当"目标位置"使用。
#   2) gg_sort()（自研 combsort）没有 nel < 2 的保护，nel == 0 时
#      end = base + width * (nel - 1) 会 size_t 下溢。
#   3) 防御：countstones/findstones/countlib 收到非法位置时返回 0，而不是 abortgo() 直接
#      abort —— 原生 abort 会连同整个 Minecraft JVM 一起杀掉（进程内引擎必须优雅降级）。
# awk 三平台都有；sed -i 的 GNU/BSD 差异太难缠。
# ---------------------------------------------------------------------------
awk_inplace() { # file awk-program
    local f="$1" prog="$2" tmpf="$TMP/awk.$$"
    awk "$prog" "$f" > "$tmpf" && mv "$tmpf" "$f"
}

log "patching GNU Go 3.8 latent bugs (aa_init_moves / gg_sort / position guards)"

awk_inplace "$ENGINES/gnugo/engine/combination.c" '
  /^aa_init_moves\(struct aa_move attacks\[AA_MAX_MOVES\]\)$/ { fn = 1 }
  fn && /^\{$/ {
    print "{"
    print "  int k, r;"
    print "  /* chuying: initialize the WHOLE array. upstream only sets attacks[0].move as"
    print "     the sentinel, leaving target[] slots as uninitialized stack memory, which"
    print "     aa_sort_moves()/get_aa_value() then treat as target positions. */"
    print "  for (k = 0; k < AA_MAX_MOVES; k++) {"
    print "    attacks[k].move = NO_MOVE;"
    print "    for (r = 0; r < AA_MAX_TARGETS_PER_MOVE; r++)"
    print "      attacks[k].target[r] = NO_MOVE;"
    print "  }"
    fn = 0; skip = 1; next
  }
  skip && /^  attacks\[0\]\.move = NO_MOVE;$/ { next }
  skip && /^\}$/ { print; skip = 0; next }
  { print }
'
grep -q 'initialize the WHOLE array' "$ENGINES/gnugo/engine/combination.c" \
    || { log "ERROR: aa_init_moves patch failed"; exit 1; }

awk_inplace "$ENGINES/gnugo/utils/gg_utils.c" '
  /^gg_sort\(void \*base, size_t nel, size_t width,$/ { fn = 1 }
  fn && /^\{$/ {
    print "{"
    print "  /* chuying: nel == 0 underflows the end pointer below (width * (nel - 1)). */"
    print "  if (nel < 2)"
    print "    return;"
    fn = 0; next
  }
  { print }
'
grep -q 'if (nel < 2)' "$ENGINES/gnugo/utils/gg_utils.c" \
    || { log "ERROR: gg_sort guard patch failed"; exit 1; }

patch_pos_guard() { # funcname
    awk_inplace "$ENGINES/gnugo/engine/board.c" "
      /^$1\\(int str\\)\$/ { fn = 1 }
      fn && /^\\{\$/ {
        print \"{\"
        print \"  /* chuying: never abort the JVM on an invalid position (in-process engine). */\"
        print \"  if (!ON_BOARD1(str) || !IS_STONE(board[str]))\"
        print \"    return 0;\"
        fn = 0; next
      }
      { print }
    "
    grep -q 'never abort the JVM on an invalid position' "$ENGINES/gnugo/engine/board.c" \
        || { log "ERROR: $1 guard patch failed"; exit 1; }
}
patch_pos_guard countlib
# findstones 有额外的 maxstones/stones 参数，单独匹配
awk_inplace "$ENGINES/gnugo/engine/board.c" '
  /^findstones\(int str, int maxstones, int \*stones\)$/ { fn = 1 }
  fn && /^\{$/ {
    print "{"
    print "  /* chuying: never abort the JVM on an invalid position (in-process engine). */"
    print "  if (!ON_BOARD1(str) || !IS_STONE(board[str]))"
    print "    return 0;"
    fn = 0; next
  }
  { print }
'
patch_pos_guard countstones
grep -c 'never abort the JVM on an invalid position' "$ENGINES/gnugo/engine/board.c" | grep -q '^3$' \
    || { log "ERROR: expected 3 position guards in board.c"; exit 1; }
log "patched GNU Go latent bugs"

# ---------------------------------------------------------------------------
# GNU Go 的 abortgo() -> abort() 必须干掉：进程内引擎里 abort() 会直接杀掉 JVM。
#   实测崩溃（3.0 首测，第 224 手、棋盘只剩 6 个空点）：
#     ***assertion failure: board.c:3042 - ON_BOARD1(pos) near PASS***
#     gnugo 3.8 (seed ...): You stepped on a bug.
#   即 GNU Go 把 PASS 哨兵(-1) 当坐标传进 is_ko() 一类函数。
#   这个 bug 在 2.1 里也存在（源码/补丁完全相同），只是没下到那么深。
#
#   修法：把 printutils.c 里 abortgo() 结尾的 `abort();` 换成调用桥接层提供的
#   chuying_gnugo_abortgo_exit()，后者 longjmp 回 nativeGenmove/nativeSetPosition
#   的 setjmp 点 —— 引擎内部踩 bug 退化为「这一手算不出来」，不崩游戏。
# ---------------------------------------------------------------------------
log "patching abortgo() to longjmp instead of abort()"
# 一步到位：插原型 + 换 abort()。分两次 awk 会因 NR==1 规则重复触发而出错。
awk_inplace "$ENGINES/gnugo/engine/printutils.c" '
  NR == 1 {
    print "/* chuying: longjmp exit provided by the JNI bridge (GPL-3.0)."
    print "   GNU Go is linked in-process, so abort() would kill the whole JVM."
    print "   Declared weak: the build-time tools (patterns/joseki,"
    print "   patterns/uncompress_fuseki) link libboard.a too but are not the"
    print "   chuying_gnugo shared library, so they must tolerate a null symbol. */"
    print "#if defined(_MSC_VER)"
    print "void chuying_gnugo_abortgo_exit(void);"
    print "#else"
    print "void chuying_gnugo_abortgo_exit(void) __attribute__((weak));"
    print "#endif"
    print ""
  }
  /^  abort\(\);  \/\* cause core dump \*\/$/ {
    print "  /* chuying: never abort the JVM (in-process engine). longjmp back to the"
    print "     JNI bridge setjmp point; see native/src/gnugo_bridge.cpp. */"
    print "  if (chuying_gnugo_abortgo_exit) chuying_gnugo_abortgo_exit();"
    print "  abort();  /* unreachable in the mod build; keeps the compiler happy */"
    next
  }
  { print }
'
grep -q 'chuying_gnugo_abortgo_exit' "$ENGINES/gnugo/engine/printutils.c" \
    || { log "ERROR: abortgo prototype insert failed"; exit 1; }
grep -q 'if (chuying_gnugo_abortgo_exit) chuying_gnugo_abortgo_exit();' "$ENGINES/gnugo/engine/printutils.c" \
    || { log "ERROR: abortgo() -> longjmp patch failed"; exit 1; }
log "patched abortgo()"

# ---------------------------------------------------------------------------
# GNU Go 里 **不止一处** abort()。实测 grep 到 7 处，其中 reading.c 的 3 处在
# default: 分支上，是读棋核心路径 —— 棋盘接近下满时最可能命中，而 abort() 会
# 直接杀掉 JVM。这里把所有会参与编译的 `abort();` 统一换成 longjmp 出口。
#   engine/printutils.c:297  abortgo()          （已在上面处理）
#   engine/reading.c:3239/3382/3476  default: 分支（读棋核心，最危险）
#   engine/oracle.c:166      error()
# 另外 2 处在 interface/gtp_examples/{metamachine,vanilla}.c，是 GTP 示例程序，
# 不参与 chuying_gnugo 的编译，无需处理。
# ---------------------------------------------------------------------------
log "patching remaining abort() calls in GNU Go engine"
for f in engine/reading.c engine/oracle.c; do
    src="$ENGINES/gnugo/$f"
    [ -f "$src" ] || { log "ERROR: $f not found"; exit 1; }
    # 顶部补原型（weak：构建期工具也要能链接）
    if ! grep -q 'chuying_gnugo_abortgo_exit' "$src"; then
        awk_inplace "$src" '
          NR == 1 {
            print "/* chuying: longjmp exit provided by the JNI bridge (GPL-3.0)."
            print "   weak so the build-time tools (joseki/uncompress_fuseki) link too. */"
            print "#if defined(_MSC_VER)"
            print "void chuying_gnugo_abortgo_exit(void);"
            print "#else"
            print "void chuying_gnugo_abortgo_exit(void) __attribute__((weak));"
            print "#endif"
            print ""
          }
          { print }
        '
    fi
    # 所有裸 abort(); 换成 longjmp 出口，保留原缩进
    awk_inplace "$src" '
      /^[ \t]*abort\(\);[ \t]*$/ {
        match($0, /^[ \t]*/)
        indent = substr($0, 1, RLENGTH)
        print indent "if (chuying_gnugo_abortgo_exit) chuying_gnugo_abortgo_exit();  /* chuying: never abort the JVM */"
        print indent "abort();  /* unreachable in the mod build */"
        next
      }
      { print }
    '
    if ! grep -q 'if (chuying_gnugo_abortgo_exit) chuying_gnugo_abortgo_exit();' "$src"; then
        log "ERROR: abort() patch failed in $f"
        exit 1
    fi
    echo "  patched $f"
done

# 兜底校验：每个裸 abort() 前面都必须有 weak hook 保护。
# 补丁后形如：
#   if (chuying_gnugo_abortgo_exit) chuying_gnugo_abortgo_exit();
#   abort();  /* unreachable in the mod build */
# 所以「abort() 数量」应等于「hook 调用数量」。
# ⚠️ set -euo pipefail：grep 无匹配返回 1 会直接杀掉脚本（实测踩过：
# 构建在 22 秒静默退出、cmake 根本没跑、四个引擎全部 UnsatisfiedLinkError）。
# 必须用 `|| true` 兜住 grep 的非零退出码。
abort_cnt=$( { grep -rhE '^[ \t]*abort\(\);[ \t]*$' "$ENGINES/gnugo/engine" --include='*.c' || true; } | wc -l)
hook_cnt=$( { grep -rh 'if (chuying_gnugo_abortgo_exit) chuying_gnugo_abortgo_exit();' "$ENGINES/gnugo/engine" --include='*.c' || true; } | wc -l)
abort_cnt=$((abort_cnt + 0))
hook_cnt=$((hook_cnt + 0))
if [ "$abort_cnt" -ne "$hook_cnt" ]; then
    log "ERROR: $abort_cnt abort() vs $hook_cnt weak-hook guards in gnugo/engine"
    grep -rnE '^[ \t]*abort\(\);[ \t]*$' "$ENGINES/gnugo/engine" --include='*.c' || true
    exit 1
fi
log "all abort() calls in GNU Go engine guarded ($hook_cnt hooks)"
echo "gnugo source @ $ENGINES/gnugo ($GNUG0_SHA)"

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

# Rapfi is built with NO_MULTI_THREADING (see native/CMakeLists.txt: its global
# HashTable clear spawns worker threads and joins them, which deadlocks inside the
# Windows loader lock). Upstream only ever used that switch for Emscripten, so one
# place still touches the now-absent std::thread member: in a single-threaded build
# there is nothing to wait for, so drop the self-check.
sed_inplace 's/th->thread\.get_id() != std::this_thread::get_id()/false/' \
    "$ENGINES/rapfi/Rapfi/search/searchthread.cpp"

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
if [ "$PLATFORM" = "macos" ]; then
    # 3.0: UNIVERSAL binary — arm64 (Apple Silicon) + x86_64 (Intel).
    # 之前只编 arm64（macos-latest 是 arm64 runner），Intel Mac 加载必 UnsatisfiedLinkError。
    #
    # 注意：**不能**用 CMAKE_OSX_ARCHITECTURES="arm64;x86_64" 一次性编。
    # Rapfi 的 eval/simdops.h 按编译宏选 NEON/SSE 类型，而 CMake 多架构构建时
    # 宏只展开一次，结果 x86_64 分片也在编译 NEON 代码：
    #   error: unknown type name 'float32x4_t'
    #   error: use of undeclared identifier 'vld1q_s8'
    # 正确做法：分两个架构各编一遍（各自带正确的 SIMD 开关），再用 lipo 合并。
    # 合并逻辑在下面 "universal 合并" 一节，这里先编 arm64。
    CMAKE_ARGS+=(-DUSE_SSE=OFF -DUSE_AVX2=OFF -DUSE_AVX512=OFF -DUSE_BMI2=OFF
                 -DUSE_VNNI=OFF -DUSE_NEON=ON -DUSE_NEON_DOTPROD=OFF
                 -DUSE_WASM_SIMD=OFF -DUSE_WASM_SIMD_RELAXED=OFF
                 -DCMAKE_OSX_ARCHITECTURES=arm64
                 -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0)
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
# 3b. macOS: 再编一遍 x86_64，然后 lipo 合并成 universal
#     （不能一次编两个架构，原因见上面 CMAKE_ARGS 处的注释）
# ---------------------------------------------------------------------------
if [ "$PLATFORM" = "macos" ]; then
    ARM_DIR="native/build-arm64"
    X86_DIR="native/build-x86_64"
    log "macOS: 复制 arm64 产物到 $ARM_DIR"
    rm -rf "$ARM_DIR"
    cp -R native/build "$ARM_DIR"

    log "macOS: 编译 x86_64 分片（USE_SSE=ON / USE_NEON=OFF）"
    X86_ARGS=(-S native -B "$X86_DIR" -DCMAKE_BUILD_TYPE=Release
              -DUSE_SSE=ON -DUSE_AVX2=OFF -DUSE_AVX512=OFF -DUSE_BMI2=OFF
              -DUSE_VNNI=OFF -DUSE_NEON=OFF -DUSE_NEON_DOTPROD=OFF
              -DUSE_WASM_SIMD=OFF -DUSE_WASM_SIMD_RELAXED=OFF
              -DCMAKE_OSX_ARCHITECTURES=x86_64
              -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0)
    cmake "${X86_ARGS[@]}"
    cmake --build "$X86_DIR" --parallel

    log "macOS: lipo 合并 arm64 + x86_64"
    mkdir -p native/dist
    for name in chuying_stockfish chuying_pikafish chuying_rapfi chuying_gnugo; do
        a="$ARM_DIR/$name.dylib"
        x="$X86_DIR/$name.dylib"
        if [ -f "$a" ] && [ -f "$x" ]; then
            lipo -create "$a" "$x" -output "native/dist/$name.dylib"
            echo "  merged $name.dylib -> $(lipo -archs native/dist/$name.dylib)"
        else
            log "ERROR: 缺少分片 $name (arm=$([ -f "$a" ] && echo ok || echo missing) x86=$([ -f "$x" ] && echo ok || echo missing))"
            exit 1
        fi
    done
fi

# ---------------------------------------------------------------------------
# 4. Collect outputs, strip symbols, verify runtime dependencies
# ---------------------------------------------------------------------------
if [ "$PLATFORM" != "macos" ]; then
    case "$PLATFORM" in
        windows) find native/build -name 'chuying_*.dll' -exec cp {} native/dist/ \; ;;
        linux)   find native/build -name 'chuying_*.so'  -exec cp {} native/dist/ \; ;;
    esac
fi
[ -n "$(ls native/dist/chuying_* 2>/dev/null)" ] || { log "ERROR: no chuying_* outputs"; exit 1; }

# macOS: verify both slices actually made it into every dylib. A silent
# arm64-only build is exactly the bug this fixes, so fail loudly instead.
if [ "$PLATFORM" = "macos" ]; then
    log "verifying universal (arm64 + x86_64) slices"
    for f in native/dist/chuying_*.dylib; do
        archs="$(lipo -archs "$f" 2>/dev/null || echo '?')"
        echo "  $(basename "$f"): $archs"
        case "$archs" in
            *arm64*x86_64*|*x86_64*arm64*) ;;
            *) log "ERROR: $f is not universal (archs=$archs)"; exit 1 ;;
        esac
    done
fi

log "stripping symbols"
case "$PLATFORM" in
    # strip -x on a universal binary keeps both slices (strip understands fat files)
    macos) find native/dist -maxdepth 1 -type f -name 'chuying_*' -exec strip -x {} \; || true ;;
    *)     find native/dist -maxdepth 1 -type f -name 'chuying_*' -exec strip --strip-unneeded {} \; || true ;;
esac

# Re-verify after strip: some strip builds silently drop slices.
if [ "$PLATFORM" = "macos" ]; then
    for f in native/dist/chuying_*.dylib; do
        archs="$(lipo -archs "$f" 2>/dev/null || echo '?')"
        case "$archs" in
            *arm64*x86_64*|*x86_64*arm64*) ;;
            *) log "ERROR: $f lost a slice during strip (archs=$archs)"; exit 1 ;;
        esac
    done
    log "universal slices intact after strip"
fi

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
platform: $PLATFORM ($PLAT_DIR)
built_utc: $(date -u +%Y-%m-%dT%H:%M:%SZ)
stockfish: https://github.com/official-stockfish/Stockfish $SF_SHA (GPL-3.0); built with NNUE_EMBEDDING_OFF (nets loaded at runtime via EvalFile/EvalFileSmall)
stockfish_big_net_sha256: $(sha256sum "$ENGINES/stockfish/src/nn-1c0000000000.nnue" 2>/dev/null | cut -d' ' -f1)
stockfish_small_net_sha256: $(sha256sum "$ENGINES/stockfish/src/nn-37f18f62d772.nnue" 2>/dev/null | cut -d' ' -f1)
pikafish:  https://github.com/official-pikafish/Pikafish $PF_SHA (GPL-3.0)
pikafish_net_sha256: $(sha256sum "$ENGINES/pikafish/src/pikafish.nnue" 2>/dev/null | cut -d' ' -f1)
rapfi:     https://github.com/dhbloo/rapfi $RF_SHA (GPL-3.0); built single-threaded (NO_MULTI_THREADING)
gnugo:     https://ftp.gnu.org/gnu/gnugo/gnugo-3.8.tar.gz ($GNUG0_SHA) (GPL-3.0-or-later); driven via its C API, in-process
bridge:    native/src (GPL-3.0), patched engine_main linkage, in-process streams
EOF

# 权重文件也放进 dist：CI 的打包 job 从这里取 shared/ 内容，避免三平台各下一遍
if [ -d "$ENGINES/stockfish/src" ]; then
    mkdir -p native/dist/nets
    for net in "$ENGINES"/stockfish/src/*.nnue; do
        [ -f "$net" ] && cp "$net" native/dist/nets/
    done
    [ -f "$ENGINES/pikafish/src/pikafish.nnue" ] && cp "$ENGINES/pikafish/src/pikafish.nnue" native/dist/nets/
fi

log "done"
