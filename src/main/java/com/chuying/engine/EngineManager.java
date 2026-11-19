package com.chuying.engine;

import com.chuying.Chuying;
import com.chuying.Config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 进程内引擎实例管理（懒加载、常驻、退出时统一关闭）。
 * <p>
 * 引擎的<b>声明</b>在 {@link EngineRegistry}，这里只负责<b>实例化</b>：
 * 按 {@link EngineSpec} 取出库路径与数据文件，交给对应的包装类。
 * <p>
 * 2.0 起引擎以 JNI 原生库形式随 jar 分发（零子进程），不再支持外置 exe 路径配置。
 * 3.0 起一个 jar 通吃所有系统（见 {@link EngineExtractor}）。
 */
public final class EngineManager {
    static {
        // 游戏进程退出时确保引擎线程退出
        Runtime.getRuntime().addShutdownHook(new Thread(EngineManager::shutdown, "chuying-engine-shutdown"));
    }

    private static NativeUciEngine cchess;
    private static NativeUciEngine wchess;
    private static NativeGomokuEngine gomoku;
    private static NativeGoEngine go;

    private EngineManager() {
    }

    /** 中国象棋（皮卡鱼，进程内 UCI） */
    public static synchronized NativeUciEngine cchess() {
        if (cchess == null) {
            String lib = libPath(EngineRegistry.CCHESS);
            if (lib == null) {
                return null;
            }
            Map<String, String> options = new LinkedHashMap<>();
            // Pikafish 的权重是必需项，路径不对会直接 std::exit 杀掉 JVM，必须显式下发
            String nnue = dataPath(EngineRegistry.CCHESS, "pikafish.nnue");
            if (nnue != null) {
                options.put("EvalFile", nnue);
            }
            cchess = new NativeUciEngine(lib, options, false);
        }
        return cchess;
    }

    /**
     * 国际象棋（Stockfish，进程内 UCI）；附带避和强度配置透传。
     * <p>
     * 3.0 起 CI 用 {@code -DNNUE_EMBEDDING_OFF} 编译，权重不再内嵌，
     * 因此这里必须显式下发 EvalFile / EvalFileSmall 两个路径
     * （Stockfish 17.1 有大小两个网，缺一不可）。
     */
    public static synchronized NativeUciEngine wchess() {
        if (wchess == null) {
            String lib = libPath(EngineRegistry.WCHESS);
            if (lib == null) {
                return null;
            }
            Map<String, String> options = new LinkedHashMap<>();
            String big = dataPath(EngineRegistry.WCHESS, "stockfish/nn-1c0000000000.nnue");
            String small = dataPath(EngineRegistry.WCHESS, "stockfish/nn-37f18f62d772.nnue");
            if (big != null) {
                options.put("EvalFile", big);
            }
            if (small != null) {
                options.put("EvalFileSmall", small);
            }
            if (big == null || small == null) {
                Chuying.warnOnce("stockfish-nets", "Stockfish 权重缺失，引擎将无法评估局面（棋力大幅下降）");
            }
            wchess = new NativeUciEngine(lib, options, true);
        }
        // 避和强度（仅国象 Stockfish 支持）：让引擎主动求胜、避免强制和棋，配置实时生效
        wchess.setAggressiveness(Config.AVOID_DRAW.get().aggressiveness);
        return wchess;
    }

    /** 五子棋（Rapfi，进程内 pbrain） */
    public static synchronized NativeGomokuEngine gomoku() {
        if (gomoku == null) {
            String lib = libPath(EngineRegistry.GOMOKU);
            String config = EngineExtractor.rapfiConfigPath();
            if (lib == null || config == null) {
                return null;
            }
            gomoku = new NativeGomokuEngine(lib, config);
        }
        return gomoku;
    }

    /** 围棋（GNU Go，进程内直调 C API） */
    public static synchronized NativeGoEngine go() {
        if (go == null) {
            String lib = libPath(EngineRegistry.GO);
            if (lib == null) {
                return null;
            }
            go = new NativeGoEngine(lib);
        }
        return go;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private static String libPath(EngineSpec spec) {
        return EngineExtractor.nativeLibPath(spec.libBase());
    }

    private static String dataPath(EngineSpec spec, String rel) {
        return EngineExtractor.dataFilePath(rel);
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
        if (go != null) {
            go.close();
            go = null;
        }
    }
}
