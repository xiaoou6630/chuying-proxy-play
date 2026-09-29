package com.chuying.engine;

/**
 * 五子棋引擎（Rapfi）的 JNI 桥接（进程内运行，零子进程）。
 * <p>
 * <b>每个引擎必须用自己独立的桥接类</b>：见 {@link CChessNativeBridge} 类注释。
 * 同名 JNI 函数会被 JVM 绑定到先加载的库，导致后加载的引擎 start 直接失败。
 */
public final class GomokuNativeBridge {

    private boolean loaded = false;

    /** 加载原生库（绝对路径）。只能调用一次。 */
    public void load(String libraryPath) {
        if (loaded) {
            return;
        }
        System.load(libraryPath);
        loaded = true;
    }

    /** 启动引擎线程，返回 0 成功；args 传给引擎 main 的 argv[1..]（如 rapfi 的 --config） */
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
