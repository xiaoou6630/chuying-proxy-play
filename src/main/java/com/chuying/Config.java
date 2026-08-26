package com.chuying;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

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
            .comment("总开关：默认开启；真正的启停用快捷键 K。")
            .define("enabled", true);

    /** 中国象棋 UCI 引擎（皮卡鱼等）路径，空 = 禁用 */
    public static final ModConfigSpec.ConfigValue<String> CCHESS_ENGINE = BUILDER
            .comment("中国象棋 UCI 引擎路径（如 pikafish.exe），留空 = 禁用。")
            .define("cchessEnginePath", "");

    /** 国际象棋 UCI 引擎（Stockfish 等）路径，空 = 禁用 */
    public static final ModConfigSpec.ConfigValue<String> WCHESS_ENGINE = BUILDER
            .comment("国际象棋 UCI 引擎路径（如 stockfish.exe），留空 = 禁用。")
            .define("wchessEnginePath", "");

    /** 五子棋引擎（Rapfi 等）路径，空 = 禁用 */
    public static final ModConfigSpec.ConfigValue<String> GOMOKU_ENGINE = BUILDER
            .comment("五子棋引擎路径（如 rapfi.exe），留空 = 禁用。")
            .define("gomokuEnginePath", "");

    /** 每步思考时间（毫秒） */
    public static final ModConfigSpec.IntValue THINK_TIME = BUILDER
            .comment("每步思考时间（毫秒）。")
            .defineInRange("thinkTime", 800, 100, 10000);

    /** 思考强度档位：实际思考时间 = thinkTime × multiplier */
    public enum Strength implements TranslatableEnum {
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

        @Override
        public Component getTranslatedName() {
            return Component.translatable("chuying.configuration.strength." + name());
        }
    }

    /** 思考强度：低 / 默认 / 高 / 极致。越高越强、越少失子 */
    public static final ModConfigSpec.EnumValue<Strength> STRENGTH = BUILDER
            .comment("思考强度：低 / 默认 / 高 / 极致。实际思考时间 = 每步思考时间 × 倍率。越高越强、越少失子。")
            .defineEnum("strength", Strength.DEFAULT);

    /** 避和强度：仅国际象棋（Stockfish 16.1+）生效，映射引擎 Aggressiveness 选项 */
    public enum AvoidDraw implements TranslatableEnum {
        /** 关闭 = 不干预，用引擎默认（Aggressiveness 100） */
        OFF(100),
        /** 温和 = 轻度求胜 */
        GENTLE(125),
        /** 激进 = 明显主动求胜、避免强制和棋 */
        ACTIVE(150),
        /** 极致 = 最高程度求胜（可能有冒险成分） */
        AGGRESSIVE(200);
        public final int aggressiveness;

        AvoidDraw(int aggressiveness) {
            this.aggressiveness = aggressiveness;
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("chuying.configuration.avoidDraw." + name());
        }
    }

    /** 避和强度（仅国际象棋）：让 Stockfish 主动求胜、避免和棋。越高越激进 */
    public static final ModConfigSpec.EnumValue<AvoidDraw> AVOID_DRAW = BUILDER
            .comment("避和强度（仅国际象棋 Stockfish 生效）：主动求胜、避免强制和棋。关闭 = 引擎默认。")
            .defineEnum("avoidDraw", AvoidDraw.ACTIVE);

    /** 调试：强制五子棋女仆最高难度 HELL（纯客户端 Mixin）。默认关闭，测试用 */
    public static final ModConfigSpec.BooleanValue DEBUG_FORCE_MAX_MAID = BUILDER
            .comment("调试：强制五子棋女仆用最高难度 HELL（纯客户端，不改任何服务端数据）。默认关闭。")
            .define("debugForceMaxMaid", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
