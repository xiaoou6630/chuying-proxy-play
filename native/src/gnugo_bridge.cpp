// GNU Go（围棋）的进程内 JNI 桥。GPL-3.0-or-later（GNU Go 3.8）/ 本文件 GPL-3.0-only。
//
// 为什么不像其它三个引擎那样走「重命名 main() + 内存流」路线：
//   GNU Go 是 C 程序，用 stdin/stdout（FILE*）而不是 std::cin/std::cout；把它的 main 链进来
//   也没有可移植的办法在进程内把 C 流接到内存队列上（fopencookie 仅 glibc 有、funopen 仅
//   BSD/macOS 有，MinGW 两者皆无）。因此这里直接调用 GNU Go 自己的引擎 C API：
//     init_gnugo() -> 整盘覆写 board[]/board_ko_pos -> genmove()
//   全程零子进程、零管道、零文本协议。
//
// 关键实现点：
//   1. 颜色编码不同：模组 1=黑 / 2=白；GNU Go 1=白 / 2=黑（见 sgf/sgftree.h、patterns/dfa.h）。
//   2. genmove() 内部会先 reset_engine() 再 examine_position(EXAMINE_ALL)，从 board[] 完全
//      重建 worms/dragons/influence（engine/genmove.c:413/432），所以整盘覆写 board[] 安全。
//   3. GNU Go 全是全局状态且非线程安全：所有入口用一把互斥锁串行化。
//   4. 位置注入用「整盘覆写」而不是逐手 gnugo_play_move()：模组棋面是含提子结果的快照，
//      逐手重放会因顺序不同而算出错误的提子与劫。劫点直接写 board_ko_pos。

#include <jni.h>

#include <mutex>
#include <csetjmp>

// 注意：GNU Go 3.8 的 gnugo.h **不能**直接喂给 C++ —— 它在第 346 行就
// "enum dragon_status crude_status(int pos);" 这样使用未先声明的枚举（C 里只是
// 警告，C++ 是硬错误）。因此这里只包含它 C++ 干净的 board.h（boards/坐标/劫点等），
// 其余入口自己声明；下面的原型与 GNU Go 3.8 的 gnugo.h / clock.h / globals.c 一一对应。
extern "C" {
#include "board.h"   // POS/I/J、board[]、board_size、board_ko_pos、komi、EMPTY/WHITE/BLACK、PASS_MOVE

void init_gnugo(float memory, unsigned int random_seed);   // engine/interface.c
void gnugo_clear_board(int boardsize);                     // engine/interface.c
int genmove(int color, float *value, int *resign);         // engine/genmove.c
void set_level(int new_level);                             // engine/clock.c（声明于 clock.h）

extern int chinese_rules;    // engine/globals.c：数子
extern int resign_allowed;   // engine/globals.c：是否允许认输
extern int quiet;            // engine/globals.c：安静模式
extern int verbose;          // engine/globals.c
extern int debug;            // engine/globals.c
extern int showtime;         // engine/globals.c
extern int showscore;        // engine/globals.c
extern int capture_all_dead;   // engine/globals.c：追吃对方死子
extern int play_out_aftermath; // engine/globals.c：把局面彻底走净（不许闲着）
extern int movenum;            // board.h：手数（GNU Go 内部判断棋局阶段）

// ---- abortgo 的 longjmp 逃生通道 ----------------------------------------
// GNU Go 3.8 里所有内部一致性检查失败都会走 abortgo() -> abort()，那会**直接杀掉
// 整个 JVM**（进程内引擎不能这么做）。实测崩溃点：
//   gnugo/engine/board.c:3042  ASSERT_ON_BOARD1(pos) near PASS
// 即它把 PASS 哨兵(-1) 当坐标传进了 is_ko() 一类的函数，棋盘接近下满（第 224 手、
// 只剩 6 个空点）时必现。
//
// 处理办法：CI 构建时把 printutils.c 的 `abort()` 换成本文件的 chuying_gnugo_abortgo_exit()
// （见 .github/scripts/build_native.sh），它 longjmp 回 nativeGenmove 的 setjmp 点，
// 于是「引擎内部踩 bug」退化成「这一手算不出来」，返回 -3 给 Java 层跳过即可。
void chuying_gnugo_abortgo_exit(void);
}

// 当前正在执行的引擎调用是否处于 setjmp 保护区；jmp_buf 由 bridge 自己持有
namespace {
jmp_buf g_jmp;            // genmove / setPosition 的保护区
bool g_jmp_active = false;
jmp_buf g_jmp_boot;       // init_gnugo / clear_board 的保护区
bool g_jmp_boot_active = false;
}

// 必须 extern "C"：调用方 printutils.c 是 C 文件（CI 会把 abortgo() 的 abort()
// 换成这个函数），C++ 名字修饰会让链接找不到符号。
//
// __attribute__((weak)) 是必须的：GNU Go 里有几个**构建期小工具**
// （patterns/joseki、patterns/uncompress_fuseki）也链接 libboard.a / libutils.a，
// 但它们不是 chuying_gnugo 共享库、拿不到本符号，链接时报：
//   printutils.c:(.text+0x10ed): undefined reference to `chuying_gnugo_abortgo_exit'
//   gmake[2]: *** [gnugo_build/patterns/CMakeFiles/joseki.dir/...] Error 1
// 这些工具只在编译期生成定式数据库，运行时用不到。声明为 weak 后它们链接时
// 该符号解析为 0；只要运行时不真的调用就没有问题（运行时走的是 chuying_gnugo，
// 那里符号是强定义）。
#if defined(_MSC_VER)
#define CHUYING_ABORT_HOOK
#else
#define CHUYING_ABORT_HOOK __attribute__((weak))
#endif

extern "C" CHUYING_ABORT_HOOK void chuying_gnugo_abortgo_exit(void) {
    if (g_jmp_active) {
        g_jmp_active = false;
        longjmp(g_jmp, 1);
    }
    // 没有保护区（init_gnugo / clear_board 期间）：既不能 abort（会杀 JVM），
    // 也不能 throw（从 C 栈帧穿过未定义行为）。这里 longjmp 到 init 的保护区；
    // 若 init 也没设保护区，就只能是启动期，直接返回让调用方继续（此时
    // GNU Go 的全局状态未建立，后续 nativeGenmove 会因 g_inited 检查而失败）。
    if (g_jmp_boot_active) {
        g_jmp_boot_active = false;
        longjmp(g_jmp_boot, 1);
    }
}

namespace {

std::mutex g_mutex;      // GNU Go 全局状态，串行化所有入口
bool g_inited = false;
int g_boardsize = 0;

/** 模组颜色(1=黑,2=白) -> GNU Go 颜色(1=白,2=黑) */
inline int toGnuColor(int modColor) {
    return modColor == 1 ? BLACK : WHITE;
}

} // namespace

extern "C" {

/**
 * 初始化引擎。cacheMb = 哈希表内存(MB)，level = GNU Go 思考强度 1..10。
 * 返回 0 成功。重复调用无副作用。
 */
JNIEXPORT jint JNICALL
Java_com_chuying_engine_GoNativeBridge_nativeInit(JNIEnv*, jobject, jint cacheMb, jint level) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_inited) {
        return 0;
    }
    if (cacheMb < 4) {
        cacheMb = 4;
    }
    if (cacheMb > 256) {
        cacheMb = 256;
    }

    // 保护区：init_gnugo 内部也可能踩断言，不能让它 abort 掉 JVM
    if (setjmp(g_jmp_boot) != 0) {
        g_jmp_boot_active = false;
        g_inited = false;
        return 1;
    }
    g_jmp_boot_active = true;

    // 固定种子：同一局面给同一手，便于复现问题
    init_gnugo(static_cast<float>(cacheMb), 0x5eedu);

    // 安静：GNU Go 默认会往 stdout/stderr 打进度与调试信息
    quiet = 1;
    verbose = 0;
    debug = 0;
    showtime = 0;
    showscore = 0;

    // 规则对齐模组 GoRules：数子(area counting)、不允许认输（代打只落子/停手）
    chinese_rules = 1;
    resign_allowed = 0;

    // 关键：对手（女仆）永远不会主动停手，而 GNU Go 一旦"自认为大局已定"就会一直
    // 停手送分 —— 实测一局 237 手里有 82 手是 pass，直接把棋送输。
    //   play_out_aftermath：把局面彻底走净（该填的填、该收的收），不许闲着；
    //   capture_all_dead：继续追吃对方的死子。
    play_out_aftermath = 1;
    capture_all_dead = 1;

    if (level <= 0) {
        level = 10;
    }
    set_level(level > 10 ? 10 : level);

    g_jmp_boot_active = false;
    g_inited = true;
    return 0;
}

/** 开新局：清空棋盘、设置棋盘尺寸与贴目。返回 0 成功。 */
JNIEXPORT jint JNICALL
Java_com_chuying_engine_GoNativeBridge_nativeNewGame(JNIEnv*, jobject, jint boardsize,
                                                    jfloat komiValue) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited) {
        return 1;
    }
    if (boardsize < 7 || boardsize > MAX_BOARD) {
        return 1;
    }
    g_boardsize = boardsize;
    gnugo_clear_board(boardsize);
    komi = komiValue;        // board.h: extern float komi
    board_ko_pos = NO_MOVE;
    return 0;
}

/**
 * 把模组棋面整盘注入引擎。
 * flat 为 boardsize*boardsize 的棋盘，索引 [x * size + y]，取值 0 空 / 1 黑 / 2 白（模组编码）。
 * koX/koY 为模组记录的劫点，-1 表示无劫。
 * 返回落子数，失败返回 -1。
 *
 * 注意：不能用 `board[pos] = color` 直接写棋盘数组 —— GNU Go 的棋串/气/哈希表数据
 * 由 add_stone()/remove_stone() 维护，直接赋值会留下不自洽的状态，genmove 里
 * 立刻踩断言（实测：board.c:1518 countlib(NO_MOVE) "You stepped on a bug"）。
 * 这里走 GNU Go 自己的摆子入口（gnugo_play_sgfnode 处理 SGF AB/AW 用的也是它）：
 * 先 gnugo_clear_board() 复位，再逐个 add_stone()。
 */
JNIEXPORT jint JNICALL
Java_com_chuying_engine_GoNativeBridge_nativeSetPosition(JNIEnv* env, jobject, jbyteArray flat,
                                                         jint koX, jint koY) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited || g_boardsize <= 0 || flat == nullptr) {
        return -1;
    }
    const jsize len = env->GetArrayLength(flat);
    if (len != g_boardsize * g_boardsize) {
        return -1;
    }
    jbyte* cells = env->GetByteArrayElements(flat, nullptr);
    if (cells == nullptr) {
        return -1;
    }

    // 保护区：add_stone() 内部也可能踩 GNU Go 的断言（棋盘满/重复落子等）。
    if (setjmp(g_jmp) != 0) {
        g_jmp_active = false;
        env->ReleaseByteArrayElements(flat, cells, JNI_ABORT);
        return -1;
    }
    g_jmp_active = true;

    // 复位棋盘（同时清掉劫点/手数/提子数；komi 是独立全局量，不受影响）
    gnugo_clear_board(g_boardsize);

    int placed = 0;
    for (int x = 0; x < g_boardsize; ++x) {
        for (int y = 0; y < g_boardsize; ++y) {
            const int v = cells[x * g_boardsize + y] & 0xff;
            if (v != 1 && v != 2) {
                continue;
            }
            const int pos = POS(x, y);
            if (pos < 0 || pos >= BOARDSIZE) {
                continue;
            }
            add_stone(pos, toGnuColor(v));
            ++placed;
        }
    }
    env->ReleaseByteArrayElements(flat, cells, JNI_ABORT);

    if (koX >= 0 && koY >= 0 && koX < g_boardsize && koY < g_boardsize) {
        board_ko_pos = POS(koX, koY);
    } else {
        board_ko_pos = NO_MOVE;
    }
    // 让 GNU Go 知道棋局走到哪一步了（每次都是 clear_board + 摆子，手数会被清零）
    movenum = placed;
    g_jmp_active = false;
    return placed;
}

/**
 * 让引擎为 modColor（1=黑,2=白）算一手。
 * 返回打包坐标 x*100+y（x,y ∈ [0,18]）；-1 = 停一手（pass）；-2 = 认输（已禁用，防御性保留）。
 */
JNIEXPORT jint JNICALL
Java_com_chuying_engine_GoNativeBridge_nativeGenmove(JNIEnv*, jobject, jint modColor) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited || g_boardsize <= 0) {
        return -1;
    }
    float value = 0.0f;
    int resign = 0;
    int move = 0;

    // 保护区：GNU Go 内部断言失败时 abortgo() 会 longjmp 回这里，
    // 而不是 abort() 杀掉 JVM（详见文件顶部 chuying_gnugo_abortgo_exit 的说明）。
    if (setjmp(g_jmp) != 0) {
        g_jmp_active = false;
        // 返回 -3 = ERROR：Java 侧跳过这一手，等下一回合重新注入棋面再算。
        // 每次 genmove 都是「整盘重注入」的无状态设计，跳过一手不会污染后续计算。
        return -3;
    }
    g_jmp_active = true;
    move = genmove(toGnuColor(modColor), &value, &resign);
    g_jmp_active = false;

    if (resign) {
        return -2;
    }
    if (move <= PASS_MOVE) {   // PASS_MOVE == NO_MOVE == 0
        return -1;
    }
    return I(move) * 100 + J(move);
}

/** 调整思考强度（1..10）。返回 0 成功。 */
JNIEXPORT jint JNICALL
Java_com_chuying_engine_GoNativeBridge_nativeSetLevel(JNIEnv*, jobject, jint level) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_inited) {
        return 1;
    }
    set_level(level <= 0 ? 10 : (level > 10 ? 10 : level));
    return 0;
}

/** 引擎是否已初始化（GNU Go 没有反初始化接口，进程退出即释放）。 */
JNIEXPORT jboolean JNICALL
Java_com_chuying_engine_GoNativeBridge_nativeIsReady(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_inited ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
