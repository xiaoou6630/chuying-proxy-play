package com.chuying;

import com.chuying.network.ProxyPayloads;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * 褚赢代打 (Chuying Proxy Play)
 * <p>
 * 为 Touhou Little Maid 的棋类（五子棋 / 中国象棋 / 国际象棋）接入外部强引擎自动落字。
 */
@Mod(Chuying.MODID)
public class Chuying {
    public static final String MODID = "chuying";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Chuying(IEventBus modEventBus, ModContainer modContainer) {
        // 客户端配置（引擎路径 / 开关 / 思考时间）
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        // 注册本 mod 的网络 payload（代理走子）
        modEventBus.addListener(ProxyPayloads::registerPayloads);
        LOGGER.info("Chuying Proxy Play loaded");
    }
}
