package com.chuying.engine;

import com.chuying.Chuying;

/**
 * pbrain 协议五子棋引擎（Rapfi，进程内版，对应原 {@code PbrainGomokuEngine} 子进程实现）。
 * <p>
 * 流程：<code>START 15</code> -> 每次 <code>RESTART</code> -> <code>BOARD</code> + 棋谱行 + <code>DONE</code>
 * -> 引擎回 <code>x,y</code>。
 * <p>
 * 启动参数传 <code>--config &lt;解压出的 config.toml 绝对路径&gt;</code>（模型路径已在解压时改写为绝对路径）。
 * 强制 freestyle（无禁手）：与 TLM 女仆五子棋规则一致。
 */
public final class NativeGomokuEngine implements AutoCloseable {
    private static final int SIZE = 15;
    private static final int START_TIMEOUT_MS = 20_000;
    private static final int STOP_GRACE_MS = 3000;
    /** 单次读取等待上限；读空只表示「暂时没输出」（冷加载模型、长考都会出现空档） */
    private static final int READ_SLICE_MS = 200;

    private final NativeEngineBridge bridge = new NativeEngineBridge();
    private final String libPath;
    private final String configPath;
    private volatile boolean started = false;

    public NativeGomokuEngine(String libPath, String configPath) {
        this.libPath = libPath;
        this.configPath = configPath;
    }

    private synchronized boolean ensureStarted() {
        if (started) {
            return true;
        }
        try {
            bridge.load(libPath);
        } catch (UnsatisfiedLinkError e) {
            Chuying.LOGGER.error("原生五子棋引擎库加载失败: {}", libPath, e);
            return false;
        }
        // argv = [--config, 配置绝对路径]；argv[0] 由桥接层补（"chuying-engine"）。
        // 注意不能自己塞程序名占位：Rapfi 的 CLI 是 `rapfi [mode] [options]`，
        // 第一个位置参数会被当成运行模式（gomocup/bench/…），多塞一个就变成
        // "unknown mode xxx" 直接退出。
        String[] args = {"--config", configPath};
        if (bridge.start(args) != 0) {
            Chuying.LOGGER.error("原生五子棋引擎启动失败: {}", libPath);
            return false;
        }
        send("START " + SIZE);
        // 强制 freestyle（无禁手）规则：TLM 五子棋为无禁手。
        // Gomocup INFO rule 0 = FREESTYLE（Rapfi 源码 gomocup.cpp 确认）。
        send("INFO rule 0");
        // 首启冷加载模型可能较慢，给足 20 秒
        long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
        boolean ok = false;
        while (System.currentTimeMillis() < deadline) {
            String line = bridge.read(READ_SLICE_MS);
            if (line == null) {
                continue;
            }
            Chuying.LOGGER.info("[rapfi] {}", line);
            if (line.trim().equalsIgnoreCase("OK")) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            Chuying.LOGGER.warn("[rapfi] 启动后未等到 OK，仍尝试继续");
        }
        started = true;
        Chuying.LOGGER.info("[chuying] 原生五子棋引擎就绪: {}", libPath);
        return true;
    }

    /** 根据当前棋盘让引擎替玩家（黑）落子，返回 [x, y]；失败返回 null */
    public synchronized int[] bestMove(byte[][] board, int thinkMs) {
        if (!ensureStarted()) {
            return null;
        }
        // 每次查询前 RESTART，保证干净的对局状态
        send("RESTART");
        // 告知本步思考时限，否则 Rapfi 按 config 的 match_space 自主分配，单步可能思考 30 秒以上
        send("INFO timeout_turn " + thinkMs);
        send("INFO timeout_match " + Math.max(thinkMs * 50L, 10_000));
        send("INFO time_left " + Math.max(thinkMs * 50L, 10_000));
        send("BOARD");
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                if (board[x][y] != 0) {
                    send(x + "," + y + "," + board[x][y]);
                }
            }
        }
        send("DONE");
        long deadline = System.currentTimeMillis() + thinkMs + STOP_GRACE_MS;
        while (System.currentTimeMillis() < deadline) {
            String line = bridge.read(READ_SLICE_MS);
            if (line == null) {
                continue;
            }
            Chuying.LOGGER.info("[rapfi] {}", line);
            String trimmed = line.trim();
            if (trimmed.matches("\\d+\\s*,\\s*\\d+")) {
                String[] parts = trimmed.split(",");
                int x = Integer.parseInt(parts[0].trim());
                int y = Integer.parseInt(parts[1].trim());
                if (x >= 0 && x < SIZE && y >= 0 && y < SIZE) {
                    Chuying.LOGGER.info("[rapfi] 落子: {},{}", x, y);
                    return new int[]{x, y};
                }
            }
            if (trimmed.startsWith("ERROR")) {
                Chuying.LOGGER.warn("[rapfi] 引擎报错: {}", trimmed);
                return null;
            }
        }
        Chuying.LOGGER.warn("[rapfi] 思考超时");
        return null;
    }

    private void send(String cmd) {
        if (bridge.send(cmd) != 0) {
            Chuying.LOGGER.warn("[rapfi] 引擎已退出，命令未送达: {}", cmd);
        }
    }

    @Override
    public synchronized void close() {
        if (started) {
            send("END");
            bridge.stop();
            started = false;
        }
    }
}
