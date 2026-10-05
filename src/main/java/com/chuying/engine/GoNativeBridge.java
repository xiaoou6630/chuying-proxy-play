package com.chuying.engine;

/**
 * 围棋引擎（GNU Go）的 JNI 桥接：进程内直接调用引擎的 C API，零子进程、零管道。
 * <p>
 * 与前三个引擎（UCI/pbrain 文本协议）不同：GNU Go 是 C 程序，用的是 {@code stdin/stdout}
 * 而不是 {@code std::cin/std::cout}，链接进 JNI 库后没有可移植的办法把 C 流接到内存队列
 * （fopencookie 仅 glibc、funopen 仅 BSD/macOS，MinGW 两者皆无）。因此原生侧直接调用
 * 它的引擎 API：{@code init_gnugo()} → 整盘覆写 {@code board[]} → {@code genmove()}。
 * <p>
 * <b>每个引擎必须用自己独立的桥接类</b>：见 {@link CChessNativeBridge} 类注释。
 */
public final class GoNativeBridge {

    private boolean loaded = false;

    /** 加载原生库（绝对路径）。只能调用一次。 */
    public void load(String libraryPath) {
        if (loaded) {
            return;
        }
        System.load(libraryPath);
        loaded = true;
    }

    /**
     * 初始化引擎。
     *
     * @param cacheMb 引擎哈希表内存（MB，建议 16~64）
     * @param level   GNU Go 思考强度 1..10（10 最强、最慢）
     * @return 0 成功
     */
    public native int nativeInit(int cacheMb, int level);

    /**
     * 开新局。
     *
     * @param boardsize 棋盘路数（本模组为 15）
     * @param komi      贴目（本模组为 6.5）
     * @return 0 成功
     */
    public native int nativeNewGame(int boardsize, float komi);

    /**
     * 整盘注入棋面。
     *
     * @param flat boardsize*boardsize 的棋盘，索引 {@code [x * size + y]}，取值 0 空 / 1 黑 / 2 白
     *             （模组编码；原生侧会映射到 GNU Go 的 1=白 / 2=黑）
     * @param koX  劫点 x，-1 表示无劫
     * @param koY  劫点 y，-1 表示无劫
     * @return 落子数；失败返回 -1
     */
    public native int nativeSetPosition(byte[] flat, int koX, int koY);

    /**
     * 让引擎算一手。
     *
     * @param modColor 1=黑（玩家）2=白（女仆）
     * @return 打包坐标 {@code x * 100 + y}；-1 = 停一手（pass）；-2 = 认输（引擎已禁用认输）
     */
    public native int nativeGenmove(int modColor);

    /** 调整思考强度（1..10）；返回 0 成功 */
    public native int nativeSetLevel(int level);

    /** 引擎是否已初始化（GNU Go 没有反初始化接口，进程退出即释放） */
    public native boolean nativeIsReady();
}
