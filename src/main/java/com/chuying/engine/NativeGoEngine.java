package com.chuying.engine;

/**
 * 围棋引擎（GNU Go）封装：进程内直接调用引擎 C API，零子进程、零文本协议。
 * <p>
 * 与 {@link NativeUciEngine} / {@link NativeGomokuEngine} 不同，这里没有
 * send/read 对话：每次算招都是「整盘注入当前棋面 + genmove」，因此天然无状态 ——
 * 不会因为漏跟一手、或代打中途开关而算错局面。
 * <p>
 * GNU Go 没有走子时间参数，强度由 {@code level}(1..10) 决定（读取深度），
 * 所以复用配置里的四档「思考强度」映射到 level。
 */
public final class NativeGoEngine implements AutoCloseable {

    /** 模组棋盘为 15 路（TouhouGO 复用五子棋棋盘，GoRules.SIZE = 15） */
    public static final int BOARDSIZE = 15;
    /** 白方贴目（与模组 GoRules.KOMI 一致） */
    public static final float KOMI = 6.5f;

    /** 引擎返回：停一手（pass） */
    public static final int PASS = -1;
    /** 引擎返回：失败 / 库缺失 / 无棋可下 */
    public static final int ERROR = -3;

    private final GoNativeBridge bridge = new GoNativeBridge();
    private final Object lock = new Object();
    private boolean started = false;
    private int level = -1;

    public NativeGoEngine(String libraryPath) {
        bridge.load(libraryPath);
    }

    /**
     * 四档思考强度（{@code Config.Strength.multiplier}）-> GNU Go level。
     * level 10 比 8 平均多约 1.6 倍时间（GNU Go 文档），对女仆足够。
     */
    public static int levelFor(int multiplier) {
        if (multiplier <= 1) {
            return 6;
        }
        if (multiplier <= 3) {
            return 8;
        }
        if (multiplier <= 6) {
            return 9;
        }
        return 10;
    }

    private boolean ensureStarted(int wantLevel) {
        if (!started) {
            if (bridge.nativeInit(16, wantLevel) != 0) {
                return false;
            }
            if (bridge.nativeNewGame(BOARDSIZE, KOMI) != 0) {
                return false;
            }
            started = true;
            level = wantLevel;
            return true;
        }
        if (level != wantLevel) {
            bridge.nativeSetLevel(wantLevel);
            level = wantLevel;
        }
        return true;
    }

    /**
     * 为黑方（玩家/代打方）算一手。
     *
     * @param board   15x15 棋面，{@code board[x][y]}，0 空 / 1 黑 / 2 白（模组编码）
     * @param koX     劫点 x，-1 无劫
     * @param koY     劫点 y，-1 无劫
     * @param level   GNU Go 思考强度 1..10（见 {@link #levelFor(int)}）
     * @return 打包坐标 {@code x * 100 + y}；{@link #PASS} 停一手；{@link #ERROR} 失败
     */
    public int bestMove(byte[][] board, int koX, int koY, int level) {
        synchronized (lock) {
            if (!ensureStarted(level)) {
                return ERROR;
            }
            byte[] flat = new byte[BOARDSIZE * BOARDSIZE];
            for (int x = 0; x < BOARDSIZE; x++) {
                if (board[x] == null) {
                    return ERROR;
                }
                System.arraycopy(board[x], 0, flat, x * BOARDSIZE, BOARDSIZE);
            }
            if (bridge.nativeSetPosition(flat, koX, koY) < 0) {
                return ERROR;
            }
            final int packed = bridge.nativeGenmove(1); // 1 = 模组的黑
            if (packed == -1) {
                return PASS;
            }
            if (packed <= -2) {
                return ERROR;
            }
            return packed;
        }
    }

    @Override
    public void close() {
        // GNU Go 没有反初始化接口（init_gnugo 只能调一次）：引擎状态随 JVM 退出释放，
        // 原生侧用互斥锁保证不会有并发调用。
    }
}
