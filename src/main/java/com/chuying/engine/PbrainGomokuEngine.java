package com.chuying.engine;

import com.chuying.Chuying;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * pbrain 协议五子棋引擎（Rapfi）。
 * <p>
 * 流程：<code>START 15</code> -> 每次 <code>RESTART</code> -> <code>BOARD</code> + 棋谱行 + <code>DONE</code>
 * -> 引擎回 <code>x,y</code>。
 * <p>
 * 说明：超时给得很宽（首启冷加载模型可能较慢），并把引擎所有输出打到日志便于排障。
 */
public class PbrainGomokuEngine implements AutoCloseable {
    private static final int SIZE = 15;

    private final String command;
    private final List<String> args;
    private Process process;
    private BufferedWriter writer;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private volatile boolean alive = false;

    public PbrainGomokuEngine(String command, String... args) {
        this.command = command;
        this.args = List.of(args);
    }

    public synchronized boolean ensureStarted() {
        if (alive && process != null && process.isAlive()) {
            return true;
        }
        try {
            start();
            return true;
        } catch (IOException e) {
            Chuying.LOGGER.error("无法启动五子棋引擎: {}", command, e);
            return false;
        }
    }

    private void start() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        // 工作目录设为 exe 所在目录，让 Rapfi 能读到同目录 config.toml 与模型文件
        Path dir = Paths.get(command).toAbsolutePath().getParent();
        if (dir != null) {
            pb.directory(dir.toFile());
        }
        pb.redirectErrorStream(true);
        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        spawnReader();
        lines.clear();
        sendLine("START " + SIZE);
        // 强制 freestyle（无禁手）规则：TLM 五子棋为无禁手，而 Rapfi 默认可能按禁手规则
        // （standard/renju）算招，导致黑方避开双三/双四等进攻着法、只下散子防守、打满平局。
        // Gomocup INFO rule 0 = FREESTYLE（Rapfi 源码 gomocup.cpp 确认）。
        sendLine("INFO rule 0");
        // 首启冷加载模型可能较慢，给足 20 秒
        long deadline = System.currentTimeMillis() + 20_000;
        String line;
        boolean ok = false;
        while ((line = pollLine(deadline)) != null) {
            Chuying.LOGGER.info("[rapfi] {}", line);
            if (line.trim().equalsIgnoreCase("OK") || line.trim().equalsIgnoreCase("ok")) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            Chuying.LOGGER.warn("[rapfi] 启动后未等到 OK，仍尝试继续");
        }
        alive = true;
    }

    /** 根据当前棋盘让引擎替玩家（黑）落子，返回 [x, y]；失败返回 null */
    public synchronized int[] bestMove(byte[][] board, int thinkMs) {
        if (!ensureStarted()) {
            return null;
        }
        try {
            // 每次查询前 RESTART，保证干净的对局状态
            sendLine("RESTART");
            // 关键：告知引擎本步思考时限，否则 Rapfi 按 config 的 match_space 自主分配时间，
            // 单步可能思考 30 秒以上导致反复超时卡死。注意 Rapfi 只认老式 Gomocup 键 timeout_turn/timeout_match
            sendLine("INFO timeout_turn " + thinkMs);
            sendLine("INFO timeout_match " + Math.max(thinkMs * 50L, 10_000));
            sendLine("INFO time_left " + Math.max(thinkMs * 50L, 10_000));
            sendLine("BOARD");
            for (int x = 0; x < SIZE; x++) {
                for (int y = 0; y < SIZE; y++) {
                    if (board[x][y] != 0) {
                        sendLine(x + "," + y + "," + board[x][y]);
                    }
                }
            }
            sendLine("DONE");
            long deadline = System.currentTimeMillis() + thinkMs + 3_000L;
            String line;
            while ((line = pollLine(deadline)) != null) {
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
            Chuying.LOGGER.warn("[rapfi] 引擎 {} 思考超时，重启", command);
            kill();
            return null;
        } catch (IOException e) {
            Chuying.LOGGER.error("[rapfi] I/O 错误", e);
            kill();
            return null;
        }
    }

    private void sendLine(String line) throws IOException {
        if (writer == null) {
            throw new IOException("writer not ready");
        }
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private void spawnReader() {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.offer(line);
                }
            } catch (IOException ignored) {
            }
        }, "chuying-gomoku-reader");
        t.setDaemon(true);
        t.start();
    }

    private String pollLine(long deadline) {
        long wait = deadline - System.currentTimeMillis();
        if (wait <= 0) {
            return null;
        }
        try {
            return lines.poll(wait, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void kill() {
        alive = false;
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        lines.clear();
    }

    @Override
    public synchronized void close() {
        try {
            if (process != null && process.isAlive()) {
                sendLine("END");
            }
        } catch (IOException ignored) {
        }
        kill();
    }
}
