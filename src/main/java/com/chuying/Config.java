package com.chuying;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置。
 * <ul>
 *   <li>三个棋种的引擎可执行文件路径（空字符串 = 该棋种代打禁用）</li>
 *   <li>每步思考时间（毫秒）</li>
 * </ul>
 */
public final class Config {
    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 总开关：默认开启，用快捷键 K 随时启停代打 */
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Master switch for Chuying proxy play. Default on; use the K hotkey to toggle.",
                    "总开关：默认开启；真正的启停用快捷键 K。")
            .define("enabled", true);

    /** 中国象棋 UCI 引擎（皮卡鱼等）路径，空 = 禁用 */
    public static final ModConfigSpec.ConfigValue<String> CCHESS_ENGINE = BUILDER
            .comment("Path to the Chinese Chess UCI engine (e.g. pikafish.exe). Empty = disabled.")
            .define("cchessEnginePath", "");

    /** 国际象棋 UCI 引擎（Stockfish 等）路径，空 = 禁用 */
    public static final ModConfigSpec.ConfigValue<String> WCHESS_ENGINE = BUILDER
            .comment("Path to the Western Chess UCI engine (e.g. stockfish.exe). Empty = disabled.")
            .define("wchessEnginePath", "");

    /** 五子棋引擎（Rapfi 等）路径，空 = 禁用 */
    public static final ModConfigSpec.ConfigValue<String> GOMOKU_ENGINE = BUILDER
            .comment("Path to the Gomoku engine (e.g. rapfi.exe). Empty = disabled.")
            .define("gomokuEnginePath", "");

    /** 每步思考时间（毫秒） */
    public static final ModConfigSpec.IntValue THINK_TIME = BUILDER
            .comment("Engine think time per move in milliseconds.",
                    "每步思考时间（毫秒）。")
            .defineInRange("thinkTime", 800, 100, 10000);

    /** 思考强度档位：实际思考时间 = thinkTime × multiplier */
    public enum Strength {
        /** 低 = 原始基础时间（如 thinkTime=800ms 时即 800ms） */
        LOW(1),
        /** 默认 = 约 2.4 秒/步（thinkTime=800ms × 3） */
        DEFAULT(3),
        HIGH(6),
        MAX(20);
        public final int multiplier;

        Strength(int multiplier) {
            this.multiplier = multiplier;
        }
    }

    /** 思考强度：低 / 默认 / 高 / 极致。越高越强、越少失子 */
    public static final ModConfigSpec.EnumValue<Strength> STRENGTH = BUILDER
            .comment("Think strength: LOW / DEFAULT / HIGH / MAX. Actual think time = thinkTime x multiplier.",
                    "思考强度：低 / 默认 / 高 / 极致。实际思考时间 = 每步思考时间 × 倍率。越高越强、越少失子。")
            .defineEnum("strength", Strength.DEFAULT);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
