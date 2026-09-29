package com.chuying.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键与 HUD 注册（仅客户端，Forge 1.20.1）。
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

    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(PROXY_KEY);
    }

    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(),
                "proxy_play", new ProxyPlayOverlay());
    }
}
