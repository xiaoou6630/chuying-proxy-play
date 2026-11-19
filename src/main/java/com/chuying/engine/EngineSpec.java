package com.chuying.engine;

import java.util.List;

/**
 * 一个进程内引擎的声明式描述。
 * <p>
 * <b>这是"加引擎"的唯一入口</b>：新增引擎时只需在 {@link EngineRegistry} 里加一条，
 * {@link EngineExtractor}（解压哪些文件）和 {@link EngineManager}（怎么创建实例）
 * 都会自动跟着走，不再需要多处同步修改。
 *
 * @param id       逻辑标识（也用作配置键前缀），如 {@code pikafish}
 * @param libBase  原生库基名（不含平台后缀），如 {@code chuying_pikafish}
 * @param kind     协议族，决定用哪个包装类
 * @param dataFiles 需要从 {@code engines/shared/} 取用的数据文件（相对路径），如 {@code pikafish.nnue}、
 *                 {@code stockfish/nn-xxx.nnue}；空列表表示纯二进制、无外部权重
 */
public record EngineSpec(String id, String libBase, EngineKind kind, List<String> dataFiles) {

    public EngineSpec {
        dataFiles = dataFiles == null ? List.of() : List.copyOf(dataFiles);
    }

    public static EngineSpec of(String id, String libBase, EngineKind kind, String... dataFiles) {
        return new EngineSpec(id, libBase, kind, List.of(dataFiles));
    }

    /** 该引擎是否需要外部权重/模型文件 */
    public boolean hasData() {
        return !dataFiles.isEmpty();
    }
}
