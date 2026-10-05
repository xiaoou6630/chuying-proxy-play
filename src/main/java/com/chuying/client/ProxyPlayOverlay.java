package com.chuying.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * HUD 提示：代打开启时在屏幕中上方显示状态；长按 J 举报一手时显示倒计时进度条 + 大字。
 */
public class ProxyPlayOverlay implements LayeredDraw.Layer {
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        var font = Minecraft.getInstance().font;
        int cx = guiGraphics.guiWidth() / 2;
        int cy = guiGraphics.guiHeight() / 2;

        // 长按中：进度条 + 剩余秒数（松开即取消，不计时）
        long holdStart = ProxyPlayState.reportHoldStart;
        if (holdStart != 0 && !ProxyPlayState.reportHoldFired) {
            float ratio = Math.min(1F, (System.currentTimeMillis() - holdStart)
                    / (float) ProxyPlayState.REPORT_HOLD_MS);
            int width = 120;
            int x = cx - width / 2;
            int y = cy - 74;
            guiGraphics.fill(x - 1, y - 1, x + width + 1, y + 6, 0x80000000);
            guiGraphics.fill(x, y, x + (int) (width * ratio), y + 5, 0xFFFF5555);
            Component countdown = Component.translatable("hud.chuying.report_hold",
                    String.format("%.1f", (1F - ratio) * ProxyPlayState.REPORT_HOLD_MS / 1000F));
            guiGraphics.drawCenteredString(font, countdown, cx, y - 12, 0xFFFFAA);
        }

        // 举报一手：无论代打开关都显示（整活优先）
        if (System.currentTimeMillis() < ProxyPlayState.reportFlashUntil) {
            Component report = Component.translatable("hud.chuying.report");
            int y = cy - 60;
            guiGraphics.drawCenteredString(font, report, cx + 1, y + 1, 0x550000);
            guiGraphics.drawCenteredString(font, report, cx, y, 0xFF5555);
        }
        if (!ProxyPlayState.enabled) {
            return;
        }
        Component text = Component.translatable("hud.chuying.proxy_on");
        guiGraphics.drawCenteredString(font, text, cx, cy - 40, 0xFFFF55);
    }
}
