package com.chuying;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * 褚嬴代打 (Chuying Proxy Play)
 * <p>
 * 为 Touhou Little Maid 的棋类（五子棋 / 中国象棋 / 国际象棋）接入外部强引擎自动落子。
 * 纯客户端：引擎在客户端算招后，模拟玩家右键棋盘交叉点走 TLM 原版交互，服务器无需安装本 mod。
 */
@Mod(Chuying.MODID)
public class Chuying {
    public static final String MODID = "chuying";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Chuying() {
        // 客户端配置（引擎路径 / 开关 / 思考时间 / 强度）
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        LOGGER.info("Chuying Proxy Play loaded (Forge 1.20.1)");
    }
}
