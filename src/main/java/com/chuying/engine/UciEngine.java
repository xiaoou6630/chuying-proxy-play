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
 * 通用 UCI 协议引擎（中国象棋皮卡鱼 / 国际象棋 Stockfish）。
 * <p>
 * 子进程常驻，每次走子发 <code>position fen ...</code> + <code>go movetime ...</code>，
 * 读取 <code>bestmove &lt;move&gt;</code>。
 */
public class UciEngine implements AutoCloseable {
    private final String command;
    private final List<String> args;
    private Process process;
    private BufferedWriter writer;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private volatile boolean alive = false;

    public UciEngine(String command, String... args) {
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
            Chuying.LOGGER.error("无法启动 UCI 引擎: {}", command, e);
            return false;
        }
    }

    private void start() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);
        process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        spawnReader();
        lines.clear();
        sendLine("uci");
        waitFor("uciok", 5000);
        sendLine("isready");
        waitFor("readyok", 5000);
        alive = true;
    }

    /** 让引擎对给定 FEN 局面思考 thinkMs 毫秒，返回 UCI 走法字符串（如 "h2e2" / "e2e4"），失败返回 null */
    public synchronized String bestMove(String fen, int thinkMs) {
        if (!ensureStarted()) {
            return null;
        }
        try {
            sendLine("position fen " + fen);
            sendLine("go movetime " + thinkMs);
            long deadline = System.currentTimeMillis() + thinkMs + 5000L;
            String line;
            while ((line = pollLine(deadline)) != null) {
                if (line.startsWith("bestmove")) {
                    String[] parts = line.split("\\s+");
                    return parts.length >= 2 ? parts[1] : null;
                }
            }
            // 超时未返回 bestmove，杀掉重启
            Chuying.LOGGER.warn("UCI 引擎 {} 思考超时，重启", command);
            kill();
            return null;
        } catch (IOException e) {
            Chuying.LOGGER.error("UCI 引擎 I/O 错误", e);
            kill();
            return null;
        }
    }

    private void waitFor(String marker, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String line;
        while ((line = pollLine(deadline)) != null) {
            if (marker.equalsIgnoreCase(line.trim())) {
                return;
            }
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
        }, "chuying-uci-reader");
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
                sendLine("quit");
            }
        } catch (IOException ignored) {
        }
        kill();
    }
}
