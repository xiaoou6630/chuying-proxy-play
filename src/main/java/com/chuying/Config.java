package com.chuying;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端配置（Forge 1.20.1）。
 * <ul>
 *   <li>三个棋种的引擎可执行文件路径（空字符串 = 该棋种代打禁用）</li>
 *   <li>每步思考时间（毫秒）</li>
 *   <li>思考强度 / 避和强度 / 调试开关</li>
 * </ul>
 * 配置界面由 Cloth Config 生成，通过 Forge Mod 列表的 Config 按钮打开。
 */
public final class Config {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 总开关：默认开启，用快捷键 K 随时启停代打 */
    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("总开关：默认开启；真正的启停用快捷键 K。")
            .define("enabled", true);

    /** 中国象棋 UCI 引擎（皮卡鱼等）路径，空 = 禁用 */
    public static final ForgeConfigSpec.ConfigValue<String> CCHESS_ENGINE = BUILDER
            .comment("中国象棋 UCI 引擎路径（如 pikafish.exe），留空 = 禁用。")
            .define("cchessEnginePath", "");

    /** 国际象棋 UCI 引擎（Stockfish 等）路径，空 = 禁用 */
    public static final ForgeConfigSpec.ConfigValue<String> WCHESS_ENGINE = BUILDER
            .comment("国际象棋 UCI 引擎路径（如 stockfish.exe），留空 = 禁用。")
            .define("wchessEnginePath", "");

    /** 五子棋引擎（Rapfi 等）路径，空 = 禁用 */
    public static final ForgeConfigSpec.ConfigValue<String> GOMOKU_ENGINE = BUILDER
            .comment("五子棋引擎路径（如 rapfi.exe），留空 = 禁用。")
            .define("gomokuEnginePath", "");

    /** 每步思考时间（毫秒） */
    public static final ForgeConfigSpec.IntValue THINK_TIME = BUILDER
            .comment("每步思考时间（毫秒）。")
            .defineInRange("thinkTime", 800, 100, 10000);

    /** 思考强度档位：实际思考时间 = thinkTime × multiplier */
    public enum Strength {
        LOW(1), DEFAULT(3), HIGH(6), MAX(20);
        public final int multiplier;

        Strength(int multiplier) {
            this.multiplier = multiplier;
        }
    }

    /** 思考强度：低 / 默认 / 高 / 极致。越高越强、越少失子 */
    public static final ForgeConfigSpec.EnumValue<Strength> STRENGTH = BUILDER
            .comment("思考强度：低 / 默认 / 高 / 极致。实际思考时间 = 每步思考时间 × 倍率。越高越强、越少失子。")
            .defineEnum("strength", Strength.DEFAULT);

    /** 避和强度：仅国际象棋（Stockfish 16.1+）生效，映射引擎 Aggressiveness 选项 */
    public enum AvoidDraw {
        OFF(100), GENTLE(125), ACTIVE(150), AGGRESSIVE(200);
        public final int aggressiveness;

        AvoidDraw(int aggressiveness) {
            this.aggressiveness = aggressiveness;
        }
    }

    /** 避和强度（仅国际象棋）：让 Stockfish 主动求胜、避免和棋。越高越激进 */
    public static final ForgeConfigSpec.EnumValue<AvoidDraw> AVOID_DRAW = BUILDER
            .comment("避和强度（仅国际象棋 Stockfish 生效）：主动求胜、避免强制和棋。关闭 = 引擎默认。")
            .defineEnum("avoidDraw", AvoidDraw.ACTIVE);

    /** 调试：强制五子棋女仆最高难度 HELL（纯客户端 Mixin）。默认关闭，测试用 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_FORCE_MAX_MAID = BUILDER
            .comment("调试：强制五子棋女仆用最高难度 HELL（纯客户端，不改任何服务端数据）。默认关闭。")
            .define("debugForceMaxMaid", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private Config() {
    }

    /** 生成 Cloth Config 配置界面（Forge Mod 列表 → Config 按钮） */
    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("chuying.configuration.title"));
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("chuying.configuration.section.chuying.client.toml"));
        ConfigEntryBuilder eb = builder.entryBuilder();

        cat.addEntry(eb.startBooleanToggle(Component.translatable("chuying.configuration.enabled"), ENABLED.get())
                .setDefaultValue(true).setSaveConsumer(ENABLED::set).build());
        cat.addEntry(eb.startStrField(Component.translatable("chuying.configuration.cchessEnginePath"), CCHESS_ENGINE.get())
                .setDefaultValue("").setSaveConsumer(CCHESS_ENGINE::set).build());
        cat.addEntry(eb.startStrField(Component.translatable("chuying.configuration.wchessEnginePath"), WCHESS_ENGINE.get())
                .setDefaultValue("").setSaveConsumer(WCHESS_ENGINE::set).build());
        cat.addEntry(eb.startStrField(Component.translatable("chuying.configuration.gomokuEnginePath"), GOMOKU_ENGINE.get())
                .setDefaultValue("").setSaveConsumer(GOMOKU_ENGINE::set).build());
        cat.addEntry(eb.startIntField(Component.translatable("chuying.configuration.thinkTime"), THINK_TIME.get())
                .setDefaultValue(800).setSaveConsumer(THINK_TIME::set).build());
        cat.addEntry(eb.startEnumSelector(Component.translatable("chuying.configuration.strength"), Strength.class, STRENGTH.get())
                .setDefaultValue(Strength.DEFAULT)
                .setEnumNameProvider(e -> Component.translatable("chuying.configuration.strength." + ((Strength) e).name()))
                .setSaveConsumer(STRENGTH::set).build());
        cat.addEntry(eb.startEnumSelector(Component.translatable("chuying.configuration.avoidDraw"), AvoidDraw.class, AVOID_DRAW.get())
                .setDefaultValue(AvoidDraw.ACTIVE)
                .setEnumNameProvider(e -> Component.translatable("chuying.configuration.avoidDraw." + ((AvoidDraw) e).name()))
                .setSaveConsumer(AVOID_DRAW::set).build());
        cat.addEntry(eb.startBooleanToggle(Component.translatable("chuying.configuration.debugForceMaxMaid"), DEBUG_FORCE_MAX_MAID.get())
                .setDefaultValue(false).setSaveConsumer(DEBUG_FORCE_MAX_MAID::set).build());

        builder.setSavingRunnable(Config.SPEC::save);
        return builder.build();
    }
}
