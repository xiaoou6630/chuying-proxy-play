package com.chuying.engine;

import com.chuying.Config;

/**
 * 三个棋种的外部引擎实例管理（懒加载、常驻、退出时统一关闭）。
 */
public final class EngineManager {
    static {
        // 游戏进程退出时确保杀掉常驻的引擎子进程
        Runtime.getRuntime().addShutdownHook(new Thread(EngineManager::shutdown, "chuying-engine-shutdown"));
    }

    private static UciEngine cchess;
    private static UciEngine wchess;
    private static PbrainGomokuEngine gomoku;

    private EngineManager() {
    }

    public static synchronized UciEngine cchess() {
        String path = Config.cchessEnginePath;
        if (path == null || path.isBlank()) {
            // 配置留空时回落内置引擎（按平台自动选择，Windows 补 .exe）
            path = EngineExtractor.enginePath("pikafish");
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        if (cchess == null) {
            cchess = new UciEngine(path);
        }
        return cchess;
    }

    public static synchronized UciEngine wchess() {
        String path = Config.wchessEnginePath;
        if (path == null || path.isBlank()) {
            path = EngineExtractor.enginePath("stockfish");
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        if (wchess == null) {
            wchess = new UciEngine(path);
        }
        // 避和强度（仅国象 Stockfish 支持）：让引擎主动求胜、避免强制和棋，配置实时生效
        wchess.setAggressiveness(Config.avoidDraw.aggressiveness);
        return wchess;
    }

    public static synchronized PbrainGomokuEngine gomoku() {
        String path = Config.gomokuEnginePath;
        if (path == null || path.isBlank()) {
            path = EngineExtractor.enginePath("rapfi/pbrain-rapfi");
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        if (gomoku == null) {
            gomoku = new PbrainGomokuEngine(path);
        }
        return gomoku;
    }

    public static synchronized void shutdown() {
        if (cchess != null) {
            cchess.close();
            cchess = null;
        }
        if (wchess != null) {
            wchess.close();
            wchess = null;
        }
        if (gomoku != null) {
            gomoku.close();
            gomoku = null;
        }
    }
}
