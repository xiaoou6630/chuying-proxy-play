package com.chuying.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * HUD 提示：代打开启时在屏幕中上方显示状态；按 J 举报一手时闪一行大字。
 */
public class ProxyPlayOverlay implements LayeredDraw.Layer {
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        // 举报一手：无论代打开关都显示（整活优先）
        if (System.currentTimeMillis() < ProxyPlayState.reportFlashUntil) {
            Component report = Component.translatable("hud.chuying.report");
            int x = guiGraphics.guiWidth() / 2;
            int y = guiGraphics.guiHeight() / 2 - 60;
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, report, x + 1, y + 1, 0x550000);
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, report, x, y, 0xFF5555);
        }
        if (!ProxyPlayState.enabled) {
            return;
        }
        Component text = Component.translatable("hud.chuying.proxy_on");
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, text,
                guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() / 2 - 40, 0xFFFF55);
    }
}
