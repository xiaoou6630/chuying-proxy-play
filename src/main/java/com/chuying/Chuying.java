package com.chuying;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

/**
 * 褚嬴代打 (Chuying Proxy Play) —— Fabric 入口（纯客户端）。
 * <p>
 * 为 Touhou Little Maid 的棋类（五子棋 / 中国象棋 / 国际象棋）接入外部强引擎自动落子。
 * 引擎在客户端算招后，模拟玩家右键棋盘交叉点走 TLM 原版交互，服务器无需安装本 mod。
 * <p>
 * 客户端配置用原生 JSON 持久化（无 ForgeConfigAPIPort 依赖），见 {@link Config}。
 */
public class Chuying implements ModInitializer {
    public static final String MODID = "chuying";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        // 加载客户端配置（config/chuying-client.json）
        Config.load();
        LOGGER.info("Chuying Proxy Play loaded (Fabric 1.20.1)");
    }
}
