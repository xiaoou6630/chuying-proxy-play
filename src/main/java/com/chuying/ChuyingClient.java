package com.chuying;

import com.chuying.client.ProxyPlayKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端侧初始化：注册配置界面、快捷键、HUD。
 */
@Mod(value = Chuying.MODID, dist = Dist.CLIENT)
public class ChuyingClient {
    public ChuyingClient(ModContainer container, IEventBus modBus) {
        // 允许 NeoForge 创建配置界面（编辑引擎路径等）
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        // 快捷键 + HUD
        modBus.addListener(ProxyPlayKey::registerKeyMapping);
        modBus.addListener(ProxyPlayKey::registerOverlay);
    }
}
