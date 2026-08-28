package com.chuying;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 客户端配置（Fabric 1.20.1，原生 JSON 持久化，零第三方配置依赖）。
 * <ul>
 *   <li>三个棋种的引擎可执行文件路径（空字符串 = 该棋种代打禁用）</li>
 *   <li>每步思考时间（毫秒）</li>
 *   <li>思考强度 / 避和强度 / 调试开关</li>
 * </ul>
 * 配置写入 {@code config/chuying-client.json}（Gson，MC 自带库）。
 * 配置界面由 Cloth Config 生成，经 ModMenu 的配置按钮打开。
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 总开关：默认开启，用快捷键 K 随时启停代打 */
    public static boolean enabled = true;
    /** 中国象棋 UCI 引擎（皮卡鱼等）路径，空 = 禁用 */
    public static String cchessEnginePath = "";
    /** 国际象棋 UCI 引擎（Stockfish 等）路径，空 = 禁用 */
    public static String wchessEnginePath = "";
    /** 五子棋引擎（Rapfi 等）路径，空 = 禁用 */
    public static String gomokuEnginePath = "";
    /** 每步思考时间（毫秒） */
    public static int thinkTime = 800;
    /** 思考强度档位：实际思考时间 = thinkTime × multiplier */
    public static Strength strength = Strength.DEFAULT;
    /** 避和强度（仅国际象棋）：让 Stockfish 主动求胜、避免和棋 */
    public static AvoidDraw avoidDraw = AvoidDraw.ACTIVE;
    /** 调试：强制五子棋女仆最高难度 HELL（纯客户端 Mixin）。默认关闭 */
    public static boolean debugForceMaxMaid = false;

    /** 思考强度档位：低 / 默认 / 高 / 极致。越高越强、越少失子 */
    public enum Strength {
        LOW(1), DEFAULT(3), HIGH(6), MAX(20);
        public final int multiplier;

        Strength(int multiplier) {
            this.multiplier = multiplier;
        }
    }

    /** 避和强度：仅国际象棋（Stockfish 16.1+）生效，映射引擎 Aggressiveness 选项 */
    public enum AvoidDraw {
        OFF(100), GENTLE(125), ACTIVE(150), AGGRESSIVE(200);
        public final int aggressiveness;

        AvoidDraw(int aggressiveness) {
            this.aggressiveness = aggressiveness;
        }
    }

    private Config() {
    }

    /** 配置文件路径：config/chuying-client.json */
    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/chuying-client.json");
    }

    /** 从磁盘加载；文件不存在时写入默认值 */
    public static void load() {
        Path p = path();
        if (!Files.exists(p)) {
            save();
            return;
        }
        try {
            Data d = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Data.class);
            if (d == null) {
                return;
            }
            enabled = d.enabled;
            cchessEnginePath = d.cchessEnginePath;
            wchessEnginePath = d.wchessEnginePath;
            gomokuEnginePath = d.gomokuEnginePath;
            thinkTime = d.thinkTime;
            strength = d.strength;
            avoidDraw = d.avoidDraw;
            debugForceMaxMaid = d.debugForceMaxMaid;
        } catch (IOException | RuntimeException e) {
            Chuying.LOGGER.error("读取配置失败，使用默认值: {}", p, e);
        }
    }

    /** 保存到磁盘 */
    public static void save() {
        Data d = new Data();
        d.enabled = enabled;
        d.cchessEnginePath = cchessEnginePath;
        d.wchessEnginePath = wchessEnginePath;
        d.gomokuEnginePath = gomokuEnginePath;
        d.thinkTime = thinkTime;
        d.strength = strength;
        d.avoidDraw = avoidDraw;
        d.debugForceMaxMaid = debugForceMaxMaid;
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Chuying.LOGGER.error("保存配置失败: {}", path(), e);
        }
    }

    /** 与磁盘交互的数据载体（仅序列化这些字段，避免把静态字段直接序列化） */
    private static class Data {
        boolean enabled = true;
        String cchessEnginePath = "";
        String wchessEnginePath = "";
        String gomokuEnginePath = "";
        int thinkTime = 800;
        Strength strength = Strength.DEFAULT;
        AvoidDraw avoidDraw = AvoidDraw.ACTIVE;
        boolean debugForceMaxMaid = false;
    }

    /** 生成 Cloth Config 配置界面（ModMenu → 配置按钮） */
    public static Screen createConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("chuying.configuration.title"));
        ConfigCategory cat = builder.getOrCreateCategory(
                Component.translatable("chuying.configuration.section.chuying.client.toml"));
        ConfigEntryBuilder eb = builder.entryBuilder();

        cat.addEntry(eb.startBooleanToggle(Component.translatable("chuying.configuration.enabled"), enabled)
                .setDefaultValue(true).setSaveConsumer(v -> enabled = v).build());
        cat.addEntry(eb.startStrField(Component.translatable("chuying.configuration.cchessEnginePath"), cchessEnginePath)
                .setDefaultValue("").setSaveConsumer(v -> cchessEnginePath = v).build());
        cat.addEntry(eb.startStrField(Component.translatable("chuying.configuration.wchessEnginePath"), wchessEnginePath)
                .setDefaultValue("").setSaveConsumer(v -> wchessEnginePath = v).build());
        cat.addEntry(eb.startStrField(Component.translatable("chuying.configuration.gomokuEnginePath"), gomokuEnginePath)
                .setDefaultValue("").setSaveConsumer(v -> gomokuEnginePath = v).build());
        cat.addEntry(eb.startIntField(Component.translatable("chuying.configuration.thinkTime"), thinkTime)
                .setDefaultValue(800).setSaveConsumer(v -> thinkTime = v).build());
        cat.addEntry(eb.startEnumSelector(Component.translatable("chuying.configuration.strength"), Strength.class, strength)
                .setDefaultValue(Strength.DEFAULT)
                .setEnumNameProvider(e -> Component.translatable("chuying.configuration.strength." + ((Strength) e).name()))
                .setSaveConsumer(v -> strength = v).build());
        cat.addEntry(eb.startEnumSelector(Component.translatable("chuying.configuration.avoidDraw"), AvoidDraw.class, avoidDraw)
                .setDefaultValue(AvoidDraw.ACTIVE)
                .setEnumNameProvider(e -> Component.translatable("chuying.configuration.avoidDraw." + ((AvoidDraw) e).name()))
                .setSaveConsumer(v -> avoidDraw = v).build());
        cat.addEntry(eb.startBooleanToggle(Component.translatable("chuying.configuration.debugForceMaxMaid"), debugForceMaxMaid)
                .setDefaultValue(false).setSaveConsumer(v -> debugForceMaxMaid = v).build());

        builder.setSavingRunnable(Config::save);
        return builder.build();
    }
}
