package com.chuying.engine;

/**
 * 中国象棋引擎（皮卡鱼）的 JNI 桥接（进程内运行，零子进程）。
 * <p>
 * <b>每个引擎必须用自己独立的桥接类</b>：三个原生库（chuying_stockfish /
 * chuying_pikafish / chuying_rapfi）若导出同名 JNI 函数，JVM 只会把它们绑定到
 * 先加载的那个库上。游戏里三个引擎常驻同一 JVM，若共用一个桥接类，后加载引擎的
 * start 会打到先加载引擎的单例上直接被拒（"原生引擎启动失败"，bestmove 恒为 null）。
 * 各自独立类名 → 各自独立 native 符号 → 各自独立 EngineBridge 单例，无串扰。
 * <p>
 * 库路径必须是解压后的绝对路径（{@link EngineExtractor} 提供），
 * 不用 System.loadLibrary，避免依赖 java.library.path。
 * 加载失败抛 {@link UnsatisfiedLinkError}，由上层捕获降级。
 */
public final class CChessNativeBridge {

    private boolean loaded = false;

    /** 加载原生库（绝对路径）。只能调用一次。 */
    public void load(String libraryPath) {
        if (loaded) {
            return;
        }
        System.load(libraryPath);
        loaded = true;
    }

    /** 启动引擎线程，返回 0 成功；args 传给引擎 main 的 argv[1..] */
    public native int start(String[] args);

    /** 向引擎发送一行命令，返回 0 成功（引擎已退出返回 1） */
    public native int send(String cmd);

    /** 读取一行引擎输出；超时（毫秒）或引擎已退出且无输出返回 null */
    public native String read(int timeoutMs);

    /** 请求引擎退出（发送 quit）并等待线程结束 */
    public native void stop();

    /** 引擎线程是否仍在运行 */
    public native boolean isAlive();
}
