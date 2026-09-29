package com.chuying.engine;

import com.chuying.Chuying;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * UCI 协议原生引擎（进程内版，对应原 {@code UciEngine} 子进程实现）。
 * <p>
 * 中国象棋（皮卡鱼）/ 国际象棋（Stockfish）共用。每次走子发
 * <code>position fen ...</code> + <code>go movetime ...</code>，读 <code>bestmove</code>。
 * 启动后按构造参数下发 setoption（如 EvalFile 指向解压出的 NNUE 权重）。
 */
public final class NativeUciEngine implements AutoCloseable {
    private static final int HANDSHAKE_TIMEOUT_MS = 15000;
    private static final int STOP_GRACE_MS = 5000;
    /** 单次读取等待上限；读空只表示「暂时没输出」，不代表引擎出问题 */
    private static final int READ_SLICE_MS = 200;

    private final CChessNativeBridge cBridge;
    private final WChessNativeBridge wBridge;
    private final boolean isWChess;
    private final String libPath;
    /** 启动时下发的 setoption（如 EvalFile → NNUE 绝对路径），保持插入顺序 */
    private final Map<String, String> initOptions;
    private volatile boolean started = false;
    /** 待生效的 Aggressiveness（国象避和强度），-1 = 不设置 */
    private int targetAggressiveness = -1;
    private int appliedAggressiveness = -1;

    public NativeUciEngine(String libPath, Map<String, String> initOptions) {
        this(libPath, initOptions, false);
    }

    /** @param wchess true = 国际象棋（Stockfish），会绑定 WChessNativeBridge 的 JNI 符号 */
    public NativeUciEngine(String libPath, Map<String, String> initOptions, boolean wchess) {
        this.libPath = libPath;
        this.initOptions = initOptions == null ? Map.of() : new LinkedHashMap<>(initOptions);
        this.isWChess = wchess;
        this.cBridge = wchess ? null : new CChessNativeBridge();
        this.wBridge = wchess ? new WChessNativeBridge() : null;
    }

    private int start(String[] args) {
        return isWChess ? wBridge.start(args) : cBridge.start(args);
    }

    private void load() {
        if (isWChess) {
            wBridge.load(libPath);
        } else {
            cBridge.load(libPath);
        }
    }

    private int sendRaw(String cmd) {
        return isWChess ? wBridge.send(cmd) : cBridge.send(cmd);
    }

    private String readRaw(int timeoutMs) {
        return isWChess ? wBridge.read(timeoutMs) : cBridge.read(timeoutMs);
    }

    private void stopRaw() {
        if (isWChess) {
            wBridge.stop();
        } else {
            cBridge.stop();
        }
    }

    private synchronized boolean ensureStarted() {
        if (started) {
            return true;
        }
        try {
            load();
            // 注意：JNI 符号绑定是惰性的——System.load 成功不代表符号存在。
            // 若盘上是旧版 DLL（导出旧桥接类的符号），这里首次调用 start 会抛
            // UnsatisfiedLinkError；必须与 load 一起兜住，否则异常会在异步线程里被静默吞掉。
            if (start(new String[0]) != 0) {
                Chuying.LOGGER.error("原生引擎启动失败: {}", libPath);
                return false;
            }
        } catch (Throwable e) {
            Chuying.LOGGER.error("原生引擎库加载失败（库缺失/版本不匹配）: {}", libPath, e);
            return false;
        }
        send("uci");
        if (!waitFor("uciok", HANDSHAKE_TIMEOUT_MS)) {
            Chuying.LOGGER.error("原生引擎未在时限内返回 uciok: {}", libPath);
            return false;
        }
        for (Map.Entry<String, String> opt : initOptions.entrySet()) {
            send("setoption name " + opt.getKey() + " value " + opt.getValue());
        }
        send("isready");
        boolean ready = waitFor("readyok", HANDSHAKE_TIMEOUT_MS);
        started = ready;
        if (ready) {
            Chuying.LOGGER.info("[chuying] 原生引擎就绪: {}", libPath);
        }
        return ready;
    }

    /** 让引擎对给定 FEN 局面思考 thinkMs 毫秒，返回 UCI 走法（如 "e2e4"），失败返回 null */
    public synchronized String bestMove(String fen, int thinkMs) {
        if (!ensureStarted()) {
            return null;
        }
        applyAggressiveness();
        send("position fen " + fen);
        send("go movetime " + thinkMs);
        String move = awaitBestMove(System.currentTimeMillis() + thinkMs + STOP_GRACE_MS);
        if (move != null) {
            return move;
        }
        // 超时：通知引擎停止思考，再给一段宽限收 bestmove（进程内引擎不能杀，只能协商）
        Chuying.LOGGER.warn("原生 UCI 引擎思考超时，发送 stop");
        send("stop");
        return awaitBestMove(System.currentTimeMillis() + STOP_GRACE_MS);
    }

    /**
     * 读到 bestmove 或超时为止。
     * <p>
     * 注意：单次读超时（引擎正在搜索、暂时无输出）必须继续等，不能直接判失败——
     * 引擎加载 NNUE 或长考时出现超过一个读取间隔的空档是正常的。
     */
    private String awaitBestMove(long deadline) {
        while (System.currentTimeMillis() < deadline) {
            String line = readRaw(READ_SLICE_MS);
            if (line == null) {
                continue;
            }
            logIfError(line);
            if (line.startsWith("bestmove")) {
                String[] parts = line.split("\\s+");
                return parts.length >= 2 ? parts[1] : null;
            }
        }
        return null;
    }

    /** 设置 Aggressiveness（国象避和强度），每次走子前按需下发 */
    public synchronized void setAggressiveness(int value) {
        this.targetAggressiveness = value;
    }

    private void applyAggressiveness() {
        if (targetAggressiveness >= 0 && targetAggressiveness != appliedAggressiveness) {
            send("setoption name Aggressiveness value " + targetAggressiveness);
            appliedAggressiveness = targetAggressiveness;
            Chuying.LOGGER.info("[chuying] uci setoption Aggressiveness {}", targetAggressiveness);
        }
    }

    /** 等待某个完整标记行（如 uciok / readyok）；单次读超时不代表失败，会一直等到 deadline */
    private boolean waitFor(String marker, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String line = readRaw(READ_SLICE_MS);
            if (line == null) {
                continue;
            }
            logIfError(line);
            if (marker.equalsIgnoreCase(line.trim())) {
                return true;
            }
        }
        return false;
    }

    private void logIfError(String line) {
        String lower = line.toLowerCase();
        if (lower.contains("not found") || lower.startsWith("error")) {
            Chuying.LOGGER.warn("[chuying] 原生引擎输出: {}", line);
        }
    }

    private void send(String cmd) {
        if (sendRaw(cmd) != 0) {
            Chuying.LOGGER.warn("[chuying] 原生引擎已退出，命令未送达: {}", cmd);
        }
    }

    @Override
    public synchronized void close() {
        if (started) {
            send("quit");
            stopRaw();
            started = false;
        }
    }
}
