package com.chuying.client;

/**
 * 客户端代打状态（纯客户端，不参与服务端逻辑）。
 */
public final class ProxyPlayState {
    /** 快捷键开关：当前是否启用代打 */
    public static volatile boolean enabled = false;
    /** 最近一次代打已处理的局面指纹，防止同一局面重复走子 */
    public static volatile String lastFen = "";
    /** 是否正在后台算招（防重入） */
    public static volatile boolean busy = false;
    /** 上次发送走子的时间戳，用于超时重试 */
    public static volatile long lastSentAt = 0;
    /** 上次提示"未配置引擎"的时间戳，防止刷屏 */
    public static volatile long lastNoEngineNotice = 0;
    /** 各棋种最近一次看到的回合计数，用于检测"对局被重置/换新"时清除局面去重 */
    public static volatile int lastCChessCounter = -1;
    public static volatile int lastWChessCounter = -1;
    public static volatile int lastGomokuCounter = -1;

    private ProxyPlayState() {
    }
}
