package com.chuying;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 褚嬴代打 (Chuying Proxy Play)
 * <p>
 * 为 Touhou Little Maid 的棋类（五子棋 / 中国象棋 / 国际象棋）接入外部强引擎自动落子。
 * 纯客户端：引擎在客户端算招后，模拟玩家右键棋盘交叉点走 TLM 原版交互，服务器无需安装本 mod。
 * <p>
 * Forge 1.20.1 版：无围棋（TouhouGO 只支持 NeoForge 1.21.1），无将棋扩展支持。
 */
@Mod(Chuying.MODID)
public class Chuying {
    public static final String MODID = "chuying";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 已提示过的告警键（避免每 tick 刷屏） */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * 同一个告警键只打一次日志。
     * <p>
     * 代打是每 tick 调用的，引擎缺库/缺权重这类"长期状态"如果每次都 warn，
     * 日志会被瞬间刷满、真正的问题反而看不见。
     */
    public static void warnOnce(String key, String message, Object... args) {
        if (WARNED.add(key)) {
            LOGGER.warn(message, args);
        }
    }

    /** 清空告警去重状态（换局/重载配置时可调用） */
    public static void resetWarnOnce() {
        WARNED.clear();
    }

    public Chuying() {
        // 客户端配置（开关 / 思考时间 / 强度）
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        LOGGER.info("Chuying Proxy Play loaded (Forge 1.20.1)");
    }
}
