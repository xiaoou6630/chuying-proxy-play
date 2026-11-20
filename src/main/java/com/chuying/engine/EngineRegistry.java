package com.chuying.engine;

import java.util.List;

/**
 * 全部进程内引擎的登记表 —— <b>加引擎只改这一个文件</b>。
 * <p>
 * 每个 {@link EngineSpec} 声明：库基名、协议族、依赖哪些数据文件。
 * {@link EngineExtractor} 据此决定解压哪些资源，{@link EngineManager} 据此创建实例。
 *
 * <h2>怎么加一个新引擎</h2>
 * <ol>
 *   <li>在 {@code native/CMakeLists.txt} 里加一个 {@code chuying_bridge(chuying_xxx ...)}
 *       或自定义目标，产出 {@code chuying_xxx} 原生库；</li>
 *   <li>在 {@code native/src/jni_exports.cpp} 加一个 {@code CHUYING_ENGINE_XXX} 分支；</li>
 *   <li>在这里加一条 {@link EngineSpec}；</li>
 *   <li>若协议族是 {@link EngineKind#UCI} 或 {@link EngineKind#GOMOKU}，
 *       {@link EngineManager} 里加一行 getter 即可；若是全新协议族，
 *       才需要新写一个 {@code NativeXxxEngine} 包装类。</li>
 * </ol>
 */
public final class EngineRegistry {

    /** 中国象棋：皮卡鱼（Pikafish，UCI，运行时加载 NNUE 权重） */
    public static final EngineSpec CCHESS =
            EngineSpec.of("pikafish", "chuying_pikafish", EngineKind.UCI, "pikafish.nnue");

    /**
     * 国际象棋：Stockfish（UCI）。
     * <p>
     * 3.0 起 CI 用 {@code -DNNUE_EMBEDDING_OFF} 编译，权重不再 INCBIN 进二进制，
     * 改由 {@code setoption name EvalFile} 指向解压出的文件 —— 三平台共用同一份权重，
     * 单库从 76 MB 降到 ~1 MB。
     */
    public static final EngineSpec WCHESS = EngineSpec.of("stockfish", "chuying_stockfish",
            EngineKind.UCI,
            "stockfish/nn-1c0000000000.nnue",
            "stockfish/nn-37f18f62d772.nnue");

    /** 五子棋：Rapfi（pbrain/Gomocup） */
    public static final EngineSpec GOMOKU = EngineSpec.of("rapfi", "chuying_rapfi",
            EngineKind.GOMOKU,
            "rapfi/config.toml",
            "rapfi/model210901.bin",
            "rapfi/mix9svqfreestyle_bsmix.bin.lz4");

    /** 围棋：GNU Go 3.8（直调 C API）—— 仅 NeoForge 1.21.1 版提供，1.20.1 无围棋棋盘 */

    /** 全部引擎（顺序即解压顺序，无关紧要） */
    public static final List<EngineSpec> ALL = List.of(CCHESS, WCHESS, GOMOKU);

    private EngineRegistry() {
    }

    /** 按 id 查引擎，找不到返回 null */
    public static EngineSpec byId(String id) {
        for (EngineSpec spec : ALL) {
            if (spec.id().equals(id)) {
                return spec;
            }
        }
        return null;
    }
}
