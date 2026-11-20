package com.chuying.engine;

/**
 * 引擎的"协议族"——决定用哪个 JNI 桥接类、以何种方式驱动。
 * <p>
 * 加新引擎时，先看它属于哪一族：
 * <ul>
 *   <li>{@link #UCI}：标准 UCI 文本协议（Stockfish / Pikafish / 未来的大多数棋类引擎）</li>
 *   <li>{@link #GOMOKU}：pbrain / Gomocup 文本协议（Rapfi）</li>
 *   <li>{@link #GO}：直调引擎 C API，无文本协议（GNU Go）</li>
 * </ul>
 * 若新引擎属于前三族，只需要在 {@link EngineRegistry} 里加一条 {@link EngineSpec}；
 * 都不属于，才需要新增一个 {@code EngineKind} 常量 + 对应的包装类与原生桥。
 */
public enum EngineKind {
    /** 标准 UCI 文本协议：position/go/bestmove */
    UCI,
    /** pbrain（Gomocup）文本协议：START/BOARD/DONE */
    GOMOKU,
    /** 直调引擎 C API：整盘注入 + genmove，无文本流 */
    GO
}
