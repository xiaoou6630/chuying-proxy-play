package com.chuying.engine;

/**
 * 原生引擎库的 JNI 桥接（进程内运行，零子进程）。
 * <p>
 * 每个棋种一个独立原生库（chuying_stockfish / chuying_pikafish / chuying_rapfi），
 * 库内线程跑引擎主循环，stdin/stdout 被重定向到内存队列，Java 侧通过
 * {@link #send(String)} / {@link #read(int)} 收发行。
 * <p>
 * 库路径必须是解压后的绝对路径（{@link EngineExtractor} 提供），
 * 不用 System.loadLibrary，避免依赖 java.library.path。
 * 加载失败抛 {@link UnsatisfiedLinkError}，由上层捕获降级。
 */
public final class NativeEngineBridge {

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
