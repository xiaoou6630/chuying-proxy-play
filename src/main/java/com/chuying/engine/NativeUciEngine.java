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

    private final NativeEngineBridge bridge = new NativeEngineBridge();
    private final String libPath;
    /** 启动时下发的 setoption（如 EvalFile → NNUE 绝对路径），保持插入顺序 */
    private final Map<String, String> initOptions;
    private volatile boolean started = false;
    /** 待生效的 Aggressiveness（国象避和强度），-1 = 不设置 */
    private int targetAggressiveness = -1;
    private int appliedAggressiveness = -1;

    public NativeUciEngine(String libPath, Map<String, String> initOptions) {
        this.libPath = libPath;
        this.initOptions = initOptions == null ? Map.of() : new LinkedHashMap<>(initOptions);
    }

    private synchronized boolean ensureStarted() {
        if (started) {
            return true;
        }
        try {
            bridge.load(libPath);
        } catch (UnsatisfiedLinkError e) {
            Chuying.LOGGER.error("原生引擎库加载失败: {}", libPath, e);
            return false;
        }
        if (bridge.start(new String[0]) != 0) {
            Chuying.LOGGER.error("原生引擎启动失败: {}", libPath);
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
        long deadline = System.currentTimeMillis() + thinkMs + STOP_GRACE_MS;
        String line;
        while ((line = readUntil(deadline)) != null) {
            if (line.startsWith("bestmove")) {
                String[] parts = line.split("\\s+");
                return parts.length >= 2 ? parts[1] : null;
            }
        }
        // 超时：通知引擎停止思考，再给一段宽限收 bestmove（进程内引擎不能杀，只能协商）
        Chuying.LOGGER.warn("原生 UCI 引擎思考超时，发送 stop");
        send("stop");
        deadline = System.currentTimeMillis() + STOP_GRACE_MS;
        while ((line = readUntil(deadline)) != null) {
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

    private boolean waitFor(String marker, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while ((line = readUntil(deadline)) != null) {
            if (marker.equalsIgnoreCase(line.trim())) {
                return true;
            }
        }
        return false;
    }

    private String readUntil(long deadline) {
        long wait = deadline - System.currentTimeMillis();
        if (wait <= 0) {
            return null;
        }
        String line = bridge.read((int) Math.min(wait, 500));
        if (line == null) {
            return null;
        }
        String lower = line.toLowerCase();
        if (lower.contains("not found") || lower.startsWith("error")) {
            Chuying.LOGGER.warn("[chuying] 原生引擎输出: {}", line);
        }
        return line;
    }

    private void send(String cmd) {
        if (bridge.send(cmd) != 0) {
            Chuying.LOGGER.warn("[chuying] 原生引擎已退出，命令未送达: {}", cmd);
        }
    }

    @Override
    public synchronized void close() {
        if (started) {
            send("quit");
            bridge.stop();
            started = false;
        }
    }
}
