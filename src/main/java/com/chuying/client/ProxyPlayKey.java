package com.chuying.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键定义（Fabric 1.20.1）。注册见 {@link com.chuying.ChuyingClient}。
 */
public class ProxyPlayKey {
    /** 默认键位 K：切换褚嬴代打 */
    public static final KeyMapping PROXY_KEY = new KeyMapping(
            "key.chuying.proxy_play",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.category.chuying");

    private ProxyPlayKey() {
    }
}
