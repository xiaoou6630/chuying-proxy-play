package com.chuying;

import com.chuying.client.ProxyPlayClient;
import com.chuying.client.ProxyPlayKey;
import com.chuying.client.ProxyPlayOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * 客户端侧初始化（Fabric 1.20.1）：注册快捷键、HUD、每 tick 代打逻辑。
 */
public class ChuyingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 快捷键注册（Fabric 会把它挂到 设置→控制→按键绑定）
        KeyBindingHelper.registerKeyBinding(ProxyPlayKey.PROXY_KEY);
        // 每 tick：处理按键切换 + 代打主逻辑
        ClientTickEvents.END_CLIENT_TICK.register(ProxyPlayClient::tick);
        // HUD 提示
        HudRenderCallback.EVENT.register(ProxyPlayOverlay::render);
    }
}
