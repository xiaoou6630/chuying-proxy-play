package com.chuying.engine;

/**
 * JNI bridge to the in-process native engine library.
 * CI smoke-test copy; the mod integration will adapt package/loading as needed.
 */
public final class NativeEngineBridge {

    public void load(String libraryPath) {
        System.load(libraryPath);
    }

    /** Launch the engine thread. Returns 0 on success. */
    public native int start(String[] args);

    /** Queue one command line for the engine. Returns 0 on success. */
    public native int send(String cmd);

    /** Read one output line, or null on timeout / engine exit. */
    public native String read(int timeoutMs);

    /** Ask the engine to quit and wait for it to exit. */
    public native void stop();

    public native boolean isAlive();
}
