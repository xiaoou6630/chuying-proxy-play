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
    public static volatile int lastShogiCounter = -1;
    public static volatile int lastGoCounter = -1;
    /** "举报一手" 需要长按的时间（毫秒）：按住 J 到进度条走满才触发，松开即取消 */
    public static final long REPORT_HOLD_MS = 1500;
    /** 继续按住到这个时长 = "必胜连招"（点棋子盒重置 → 落一子 → 双停判胜） */
    public static final long REPORT_WIN_HOLD_MS = 3000;
    /** 本次长按是否已触发过必胜连招 */
    public static volatile boolean reportWinFired = false;
    /** "举报一手" 长按开始的时间戳（0 = 没按住） */
    public static volatile long reportHoldStart = 0;
    /** 本次长按是否已触发过（按住不放不会连续触发，需松开重按） */
    public static volatile boolean reportHoldFired = false;
    /**
     * 举报已"挂起"：长按完成后进入这个状态，等女仆下一次该走子时立刻把她的应手判成停一手。
     * 因为女仆应手是客户端算的、几十毫秒就轮回到玩家，靠"按的时候正好轮到女仆"是抓不到的。
     */
    public static volatile boolean reportArmed = false;
    /** "举报一手" HUD 大字显示到什么时候（毫秒时间戳） */
    public static volatile long reportFlashUntil = 0;
    /** "举报一手" 冷却结束时间戳，防止连点刷屏 */
    public static volatile long reportCooldownUntil = 0;

    private ProxyPlayState() {
    }
}
