package com.chuying.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * HUD 提示：代打开启时在屏幕中上方显示状态。
 */
public class ProxyPlayOverlay implements LayeredDraw.Layer {
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (!ProxyPlayState.enabled) {
            return;
        }
        Component text = Component.translatable("hud.chuying.proxy_on");
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, text,
                guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() / 2 - 40, 0xFFFF55);
    }
}
