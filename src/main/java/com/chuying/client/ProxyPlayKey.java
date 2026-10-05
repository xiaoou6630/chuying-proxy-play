package com.chuying.client;

import com.chuying.Chuying;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键与 HUD 注册（仅客户端）。
 */
public class ProxyPlayKey {
    /** 默认键位 K：切换褚嬴代打 */
    public static final KeyMapping PROXY_KEY = new KeyMapping(
            "key.chuying.proxy_play",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.category.chuying");

    /**
     * 默认键位 J：举报一手（整活）—— 对着围棋棋盘按下去，把女仆这一手判成"停一手"。
     * 只对装了 TouhouGO 的围棋棋盘生效，且必须轮到女仆走子。
     */
    public static final KeyMapping REPORT_KEY = new KeyMapping(
            "key.chuying.report",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.category.chuying");

    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(PROXY_KEY);
        event.register(REPORT_KEY);
    }

    public static void registerOverlay(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR,
                ResourceLocation.fromNamespaceAndPath(Chuying.MODID, "proxy_play"),
                new ProxyPlayOverlay());
    }
}
