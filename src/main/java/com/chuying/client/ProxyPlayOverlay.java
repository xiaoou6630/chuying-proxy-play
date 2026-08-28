package com.chuying.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * HUD 提示：代打开启时在屏幕中上方显示状态（Fabric 1.20.1，HudRenderCallback）。
 */
public class ProxyPlayOverlay {
    private ProxyPlayOverlay() {
    }

    public static void render(GuiGraphics guiGraphics, float tickDelta) {
        if (!ProxyPlayState.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Component text = Component.translatable("hud.chuying.proxy_on");
        guiGraphics.drawCenteredString(mc.font, text,
                mc.getWindow().getGuiScaledWidth() / 2, mc.getWindow().getGuiScaledHeight() / 2 - 40, 0xFFFF55);
    }
}
