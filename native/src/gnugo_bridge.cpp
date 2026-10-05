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

    if (level <= 0) {
        level = 10;
    }
    set_level(level > 10 ? 10 : level);

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

    // 保险：board_size 决定 POS()/I()/J() 的可用范围，必须与棋盘一致
    board_size = g_boardsize;

    int placed = 0;
    for (int x = 0; x < g_boardsize; ++x) {
        for (int y = 0; y < g_boardsize; ++y) {
            const int v = cells[x * g_boardsize + y] & 0xff;
            const int pos = POS(x, y);
            if (pos < 0 || pos >= BOARDSIZE) {
                continue;
            }
            if (v == 1 || v == 2) {
                board[pos] = static_cast<Intersection>(toGnuColor(v));
                ++placed;
            } else {
                board[pos] = static_cast<Intersection>(EMPTY);
            }
        }
    }
    env->ReleaseByteArrayElements(flat, cells, JNI_ABORT);

    if (koX >= 0 && koY >= 0 && koX < g_boardsize && koY < g_boardsize) {
        board_ko_pos = POS(koX, koY);
    } else {
        board_ko_pos = NO_MOVE;
    }
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
    const int move = genmove(toGnuColor(modColor), &value, &resign);
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
