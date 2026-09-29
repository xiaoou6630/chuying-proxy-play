package com.chuying;

import com.chuying.client.ProxyPlayKey;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端侧初始化（Forge 1.20.1）：注册配置界面、快捷键、HUD。
 * 这些事件都在 MOD 事件总线（Bus.MOD）上。
 */
@Mod.EventBusSubscriber(modid = Chuying.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChuyingClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 注册配置界面：Forge Mod 列表 → 选中本 mod → 下方 Config 按钮打开 Cloth Config 界面
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> Config.createConfigScreen(parent)));
    }

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        ProxyPlayKey.registerKeyMapping(event);
    }

    @SubscribeEvent
    public static void onOverlayRegister(RegisterGuiOverlaysEvent event) {
        ProxyPlayKey.registerOverlay(event);
    }
}
