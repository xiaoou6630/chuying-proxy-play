package com.chuying;

import com.chuying.client.ProxyPlayKey;
import com.chuying.compat.ShogiCompat;
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

        // 启动时立刻探测可选模组，并把结果打进日志（含将棋 POI 诊断）。
        // 这样即使不进入世界，也能从日志判断 tlm_shogi 的 mixin 有没有生效。
        // 注意：构造期注册表可能还没建好，因此挂到 FMLClientSetupEvent 之后。
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) ->
                e.enqueueWork(() -> {
                    try {
                        ShogiCompat.isAvailable();
                    } catch (Throwable t) {
                        Chuying.LOGGER.warn("[chuying] 将棋探测异常（不影响运行）: {}", t.toString());
                    }
                }));
    }
}
