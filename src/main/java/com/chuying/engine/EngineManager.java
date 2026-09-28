package com.chuying.engine;

import com.chuying.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三个棋种的原生引擎实例管理（进程内运行、懒加载、常驻、退出时统一关闭）。
 * <p>
 * 2.0 起引擎以 JNI 原生库形式随 jar 分发（零子进程），不再支持外置 exe 路径配置。
 */
public final class EngineManager {
    static {
        // 游戏进程退出时确保引擎线程退出
        Runtime.getRuntime().addShutdownHook(new Thread(EngineManager::shutdown, "chuying-engine-shutdown"));
    }

    private static NativeUciEngine cchess;
    private static NativeUciEngine wchess;
    private static NativeGomokuEngine gomoku;

    private EngineManager() {
    }

    /** 中国象棋（皮卡鱼，进程内） */
    public static synchronized NativeUciEngine cchess() {
        if (cchess == null) {
            String lib = EngineExtractor.nativeLibPath("chuying_pikafish");
            if (lib == null) {
                return null;
            }
            Map<String, String> options = new LinkedHashMap<>();
            String nnue = EngineExtractor.dataFilePath("pikafish.nnue");
            if (nnue != null) {
                options.put("EvalFile", nnue);
            }
            cchess = new NativeUciEngine(lib, options);
        }
        return cchess;
    }

    /** 国际象棋（Stockfish，进程内）；附带避和强度配置透传 */
    public static synchronized NativeUciEngine wchess() {
        if (wchess == null) {
            String lib = EngineExtractor.nativeLibPath("chuying_stockfish");
            if (lib == null) {
                return null;
            }
            Map<String, String> options = new LinkedHashMap<>();
            String mainNet = EngineExtractor.dataFilePath("stockfish/nn-1c0000000000.nnue");
            String smallNet = EngineExtractor.dataFilePath("stockfish/nn-37f18f62d772.nnue");
            if (mainNet != null) {
                options.put("EvalFile", mainNet);
            }
            if (smallNet != null) {
                options.put("EvalFileSmall", smallNet);
            }
            wchess = new NativeUciEngine(lib, options);
        }
        // 避和强度（仅国象 Stockfish 支持）：让引擎主动求胜、避免强制和棋，配置实时生效
        wchess.setAggressiveness(Config.AVOID_DRAW.get().aggressiveness);
        return wchess;
    }

    /** 五子棋（Rapfi，进程内） */
    public static synchronized NativeGomokuEngine gomoku() {
        if (gomoku == null) {
            String lib = EngineExtractor.nativeLibPath("chuying_rapfi");
            String config = EngineExtractor.rapfiConfigPath();
            if (lib == null || config == null) {
                return null;
            }
            gomoku = new NativeGomokuEngine(lib, config);
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
