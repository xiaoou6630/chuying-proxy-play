package com.chuying.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * HUD 提示：代打开启时在屏幕中上方显示状态（Forge 1.20.1）。
 */
public class ProxyPlayOverlay implements IGuiOverlay {
    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!ProxyPlayState.enabled) {
            return;
        }
        Component text = Component.translatable("hud.chuying.proxy_on");
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, text,
                screenWidth / 2, screenHeight / 2 - 40, 0xFFFF55);
    }
}
