package com.chuying.compat;

import com.chuying.Chuying;
import com.chuying.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

/**
 * tlm_shogi（将棋扩展）软依赖封装：<b>全部反射调用</b>，不 import 该扩展的任何类。
 * <p>
 * 玩家未安装 tlm_shogi 时 {@link #isAvailable()} 返回 false，整体禁用将棋代打，
 * 其余棋种与 mod 其它功能不受影响、不崩溃；探测失败只打一次警告。
 * <p>
 * 探测按<b>正式版（1.0.0）</b>类名进行，并逐个方法容错：任意类/方法缺失都不会让
 * 探测抛异常（先前第一处 {@code Class.forName} 就抛 CNFE 的问题已消除）。
 * 正式版与 beta 的差异已在 javap 字节码层面核实：
 * <ul>
 *   <li>局面类是 {@code engine.core.Position}（beta 为 {@code api.game.jchess.Position}），
 *       SFEN 提取方法为 {@code toSfen()}（beta 为 {@code toUSI()}）；</li>
 *   <li>坐标/手驹换算在 {@code util.JChessUiAdapter}：{@code Square.parseSfen} +
 *       {@code pointFromSquare} 得到格点号，{@code handIndex(pos, Turn.BLACK, type)} 得到手驹槽位；</li>
 *   <li>{@code JChessUtil.getClickPosition(Vec3, Position)} 是「命中点→格点号」，
 *       本类只负责「读局面 → 算招 → USI → 格点号 → 交给模拟右键」。</li>
 * </ul>
 * <p>
 * 升变（promote）例外：走法存在「升/不升」两种合法选择时，服务器向本客户端弹
 * {@code JChessPromoteOpenPackage} → 客户端弹出 {@code JChessPromoteScreen}。
 * 代打必须在 {@link #autoAnswerPromote} 里自动应答 {@code JChessPromoteResultPackage}，否则会卡住。
 */
public final class ShogiCompat {
    private static final String TE_CLASS = "com.github.sangeeeee.tlm_shogi.tileentity.TileEntityJChess";
    private static final String BLOCK_CLASS = "com.github.sangeeeee.tlm_shogi.block.BlockJChess";
    private static final String PART_CLASS = "com.github.sangeeeee.tlm_shogi.block.properties.ShogiPart";
    /** 正式版在 {@code engine.core}，beta 在 {@code api.game.jchess}；两者都探测，哪个在就用哪个 */
    private static final String[] POSITION_CLASSES = {
            "com.github.sangeeeee.tlm_shogi.engine.core.Position",
            "com.github.sangeeeee.tlm_shogi.api.game.jchess.Position",
    };
    private static final String SQUARE_CLASS = "com.github.sangeeeee.tlm_shogi.engine.core.Square";
    private static final String TURN_CLASS = "com.github.sangeeeee.tlm_shogi.engine.core.Turn";
    private static final String PIECE_TYPE_CLASS = "com.github.sangeeeee.tlm_shogi.engine.core.PieceType";
    private static final String UI_ADAPTER_CLASS = "com.github.sangeeeee.tlm_shogi.util.JChessUiAdapter";
    private static final String SUNFISH_RESOURCES = "com.github.sangeeeee.tlm_shogi.engine.SunfishResources";
    private static final String SUNFISH_ENGINE = "com.github.sangeeeee.tlm_shogi.engine.SunfishEngine";
    private static final String SEARCH_LIMITS = "com.github.sangeeeee.tlm_shogi.engine.SearchLimits";
    private static final String SEARCH_REQUEST = "com.github.sangeeeee.tlm_shogi.engine.SearchRequest";
    private static final String SEARCH_RESULT = "com.github.sangeeeee.tlm_shogi.engine.SearchResult";
    private static final String CANCELLATION_TOKEN = "com.github.sangeeeee.tlm_shogi.engine.CancellationToken";
    private static final String PROMOTE_SCREEN = "com.github.sangeeeee.tlm_shogi.client.gui.game.JChessPromoteScreen";
    private static final String PROMOTE_RESULT = "com.github.sangeeeee.tlm_shogi.network.message.JChessPromoteResultPackage";

    /** 引擎资源目录（在 tlm_shogi jar 内的 classpath 路径） */
    private static final String RESOURCE_DIR = "assets/tlm_shogi/sunfish";

    /** 手驹点击区域起点格点号（0~80 棋盘、81~89 手驹） */
    private static final int HAND_POINT_BASE = 81;

    // ---- 探测结果（懒加载、只探测一次） ----
    private static volatile boolean probed = false;
    private static boolean available = false;

    private static Class<?> teClass;
    private static Class<?> blockClass;
    private static Class<?> positionClass;
    private static Class<?> partClass;
    private static Class<?> promoteScreenClass;
    private static Class<?> promoteResultClass;
    private static Property<?> partProperty;
    private static Property<?> facingProperty;

    private static Method getChessDataMethod;
    private static Method positionToSfenMethod;
    private static Method squareParseSfenMethod;
    private static Method pointFromSquareMethod;
    private static Method handIndexMethod;
    private static Object turnBlack;
    private static Class<?> pieceTypeClass;
    private static Method partGetPosXMethod;
    private static Method partGetPosYMethod;

    private static Method teIsPlayerTurnMethod;
    private static Method teIsCheckmateMethod;
    private static Method teIsRepeatMethod;
    private static Method teIsMoveNumberLimitMethod;
    private static Method teGetChessCounterMethod;

    private static Field screenChessPosField;
    private static Field screenExpectedSfenField;
    private static Field screenFromPosField;
    private static Field screenToPosField;
    private static Constructor<?> promoteResultCtor;

    // ---- 升变自动应答：本次代打是否升变（由 USI 尾字符 '+' 决定） ----
    private static volatile boolean pendingPromote = false;

    // ---- 独立引擎实例（懒加载、只建一次、可复用；不复用 ShogiEngineInteractor，避免与女仆同档） ----
    private static volatile Object engine;
    private static volatile boolean engineReady = false;
    private static boolean engineFailed = false;

    private static Constructor<?> searchLimitsCtor;
    private static Method searchRequestCurrentMethod;
    private static Object cancellationNone;
    private static Method engineInitializeMethod;
    private static Method engineSearchMethod;
    private static Method resultBestMoveMethod;
    private static Method resultOutcomeMethod;

    private ShogiCompat() {
    }

    /** 将棋扩展是否可用（未安装/不兼容时为 false，整体禁用将棋代打） */
    public static boolean isAvailable() {
        if (!probed) {
            probe();
        }
        return available;
    }

    private static synchronized void probe() {
        if (probed) {
            return;
        }
        probed = true;
        ClassLoader loader = ShogiCompat.class.getClassLoader();
        try {
            teClass = findClass(loader, TE_CLASS);
            blockClass = findClass(loader, BLOCK_CLASS);
            partClass = findClass(loader, PART_CLASS);
            positionClass = findClass(loader, POSITION_CLASSES);
            promoteScreenClass = findClass(loader, PROMOTE_SCREEN);
            promoteResultClass = findClass(loader, PROMOTE_RESULT);

            // 必需类缺失：判定为未安装/不兼容，只警告一次并禁用将棋
            if (teClass == null || blockClass == null || partClass == null || positionClass == null) {
                Chuying.LOGGER.warn("[chuying] 未检测到 tlm_shogi（或版本不兼容），将棋代打已禁用");
                return;
            }

            partProperty = (Property<?>) blockClass.getField("PART").get(null);
            facingProperty = (Property<?>) blockClass.getField("FACING").get(null);
            partGetPosXMethod = partClass.getMethod("getPosX");
            partGetPosYMethod = partClass.getMethod("getPosY");
            getChessDataMethod = teClass.getMethod("getChessData");
            // 正式版 toSfen()；beta 为 toUSI()
            positionToSfenMethod = methodOrNull(positionClass, "toSfen");
            if (positionToSfenMethod == null) {
                positionToSfenMethod = methodOrNull(positionClass, "toUSI");
            }

            teIsPlayerTurnMethod = teClass.getMethod("isPlayerTurn");
            teIsCheckmateMethod = teClass.getMethod("isCheckmate");
            teIsRepeatMethod = teClass.getMethod("isRepeat");
            teIsMoveNumberLimitMethod = teClass.getMethod("isMoveNumberLimit");
            teGetChessCounterMethod = teClass.getMethod("getChessCounter");

            bindPromote(loader);
            bindCoordinates(loader);

            available = true;
            Chuying.LOGGER.info("[chuying] 检测到 tlm_shogi，将棋代打已启用");
        } catch (Throwable t) {
            available = false;
            Chuying.LOGGER.warn("[chuying] 未检测到 tlm_shogi（或版本不兼容），将棋代打已禁用：{}", t.toString());
        }
    }

    /** 升变自动应答相关（正式版与 beta 结构不同，找不到就只打日志不崩） */
    private static void bindPromote(ClassLoader loader) {
        try {
            screenChessPosField = promoteScreenClass.getDeclaredField("chessPos");
            screenChessPosField.setAccessible(true);
            screenFromPosField = promoteScreenClass.getDeclaredField("fromPos");
            screenFromPosField.setAccessible(true);
            screenToPosField = promoteScreenClass.getDeclaredField("toPos");
            screenToPosField.setAccessible(true);
            // 正式版新增 expectedSfen 字段与网络包参数
            screenExpectedSfenField = fieldOrNull(promoteScreenClass, "expectedSfen");
            if (screenExpectedSfenField != null) {
                screenExpectedSfenField.setAccessible(true);
                promoteResultCtor = promoteResultClass.getConstructor(
                        BlockPos.class, String.class, int.class, int.class, int.class);
            } else {
                promoteResultCtor = promoteResultClass.getConstructor(
                        BlockPos.class, int.class, int.class, int.class);
            }
        } catch (Throwable t) {
            Chuying.LOGGER.warn("[chuying] 将棋升变界面结构不兼容，升变时应答会跳过：{}", t.toString());
        }
    }

    /** 坐标/手驹换算相关（正式版在 JChessUiAdapter + Square） */
    private static void bindCoordinates(ClassLoader loader) {
        try {
            Class<?> squareClass = findClass(loader, SQUARE_CLASS);
            Class<?> uiAdapterClass = findClass(loader, UI_ADAPTER_CLASS);
            Class<?> turnClass = findClass(loader, TURN_CLASS);
            pieceTypeClass = findClass(loader, PIECE_TYPE_CLASS);
            if (squareClass == null || uiAdapterClass == null || pieceTypeClass == null) {
                return;
            }
            squareParseSfenMethod = squareClass.getMethod("parseSfen", String.class);
            pointFromSquareMethod = uiAdapterClass.getMethod("pointFromSquare", squareClass);
            handIndexMethod = uiAdapterClass.getMethod("handIndex", positionClass, turnClass, pieceTypeClass);
            turnBlack = turnClass.getField("BLACK").get(null);
        } catch (Throwable t) {
            Chuying.LOGGER.warn("[chuying] 将棋坐标换算接口不兼容：{}", t.toString());
        }
    }

    private static Class<?> findClass(ClassLoader loader, String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, loader);
            } catch (Throwable ignored) {
                // 该类不存在：继续尝试下一个候选名
            }
        }
        return null;
    }

    private static Method methodOrNull(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Field fieldOrNull(Class<?> owner, String name) {
        try {
            return owner.getDeclaredField(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 是否将棋棋盘方块（5-part 中的任意一块） */
    public static boolean isShogiBoard(Block block) {
        return isAvailable() && block != null && blockClass.isInstance(block);
    }

    /** 是否将棋棋盘方块实体 */
    public static boolean isShogiTile(BlockEntity te) {
        return isAvailable() && te != null && teClass.isInstance(te);
    }

    /** 由方块状态里的 {@code PART} 反推该 part 相对中心块的偏移 {posX, posY} */
    public static int[] partOffset(BlockState state) {
        try {
            Object part = propValue(state, partProperty);
            int x = (int) partGetPosXMethod.invoke(part);
            int y = (int) partGetPosYMethod.invoke(part);
            return new int[]{x, y};
        } catch (Throwable t) {
            return new int[]{0, 0};
        }
    }

    /** 方块朝向（与服务器 useItemOn 使用同一朝向换算命中点） */
    public static Direction facingOf(BlockState state) {
        try {
            Object d = propValue(state, facingProperty);
            return d instanceof Direction dir ? dir : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object propValue(BlockState state, Property<?> prop) {
        return state.getValue((Property) prop);
    }

    // ---- 棋盘状态读取（主线程调用） ----

    public static String sfenOf(Object te) {
        if (positionToSfenMethod == null) {
            return null;
        }
        try {
            return (String) positionToSfenMethod.invoke(getChessDataMethod.invoke(te));
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isPlayerTurn(Object te) {
        return (boolean) callQuietly(teIsPlayerTurnMethod, te, false);
    }

    public static boolean isCheckmate(Object te) {
        return (boolean) callQuietly(teIsCheckmateMethod, te, false);
    }

    public static boolean isRepeat(Object te) {
        return (boolean) callQuietly(teIsRepeatMethod, te, false);
    }

    public static boolean isMoveNumberLimit(Object te) {
        return (boolean) callQuietly(teIsMoveNumberLimitMethod, te, false);
    }

    public static int chessCounter(Object te) {
        return (int) callQuietly(teGetChessCounterMethod, te, -1);
    }

    private static Object callQuietly(Method m, Object target, Object fallback) {
        if (m == null) {
            return fallback;
        }
        try {
            return m.invoke(target);
        } catch (Throwable t) {
            return fallback;
        }
    }

    // ---- USI → TLM 格点号 ----

    /** USI 两字符格（如 "7f"）→ TLM 格点号（0~80 棋盘），失败返回 -1 */
    public static int usiSquareToPoint(String square) {
        if (squareParseSfenMethod == null || pointFromSquareMethod == null) {
            return -1;
        }
        try {
            Object parsed = squareParseSfenMethod.invoke(null, square);
            Object sq = parsed instanceof Optional<?> opt ? opt.orElse(null) : parsed;
            if (sq == null) {
                return -1;
            }
            return (int) pointFromSquareMethod.invoke(null, sq);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * 打驹落子：按手驹种类（USI 大写字母，如 'P'）在自己（黑方）手驹列表里找到槽位，
     * 返回对应的点击格点号（81 + 槽位下标）；找不到返回 -1。
     */
    public static int dropPoint(Object te, char pieceChar) {
        if (handIndexMethod == null || turnBlack == null) {
            return -1;
        }
        try {
            Object pieceType = resolvePieceType(pieceChar);
            if (pieceType == null) {
                return -1;
            }
            Object position = getChessDataMethod.invoke(te);
            int idx = (int) handIndexMethod.invoke(null, position, turnBlack, pieceType);
            return idx < 0 ? -1 : HAND_POINT_BASE + idx;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static Object resolvePieceType(char pieceChar) {
        String fieldName = switch (Character.toUpperCase(pieceChar)) {
            case 'P' -> "PAWN";
            case 'L' -> "LANCE";
            case 'N' -> "KNIGHT";
            case 'S' -> "SILVER";
            case 'G' -> "GOLD";
            case 'B' -> "BISHOP";
            case 'R' -> "ROOK";
            default -> null;
        };
        if (fieldName == null || pieceTypeClass == null) {
            return null;
        }
        try {
            return pieceTypeClass.getField(fieldName).get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** USI 走法是否为升变（尾字符 '+'） */
    public static boolean usiPromotes(String usi) {
        return usi != null && usi.endsWith("+");
    }

    /** 记录本次代打走法是否升变，供升变界面自动应答使用 */
    public static void setPendingPromote(boolean promote) {
        pendingPromote = promote;
    }

    // ---- 引擎搜索（后台线程调用；引擎懒加载、只建一次） ----

    /**
     * 用独立 {@code SunfishEngine} 实例对 SFEN 局面算招，返回 USI 走法（如 "7g7f"、"7g7f+"、"P*5e"）；
     * 引擎不可用或搜索失败返回 null。档位对齐 {@link Config.Strength}，明显强于女仆（3s/depth8/30k）。
     */
    public static String think(String sfen) {
        if (sfen == null || !isAvailable()) {
            return null;
        }
        try {
            Object e = ensureEngine();
            if (e == null) {
                return null;
            }
            Config.Strength strength = Config.STRENGTH.get();
            Duration moveTime;
            int depth;
            long nodes;
            int ttMiB;
            switch (strength) {
                case LOW -> {
                    moveTime = Duration.ofSeconds(5);
                    depth = 12;
                    nodes = 200_000L;
                    ttMiB = 128;
                }
                case HIGH -> {
                    moveTime = Duration.ofSeconds(20);
                    depth = 32;
                    nodes = 5_000_000L;
                    ttMiB = 512;
                }
                case MAX -> {
                    moveTime = Duration.ofSeconds(40);
                    depth = 64;
                    nodes = Long.MAX_VALUE;
                    ttMiB = 1024;
                }
                default -> {
                    moveTime = Duration.ofSeconds(10);
                    depth = 20;
                    nodes = 1_000_000L;
                    ttMiB = 256;
                }
            }
            // threads 固定 1，避免抢占游戏线程资源
            Object limits = searchLimitsCtor.newInstance(moveTime, depth, nodes, ttMiB, 1);
            Object request = searchRequestCurrentMethod.invoke(null, sfen, limits);
            Object result = engineSearchMethod.invoke(e, request, cancellationNone);
            Optional<?> best = (Optional<?>) resultBestMoveMethod.invoke(result);
            if (best == null || best.isEmpty()) {
                Chuying.LOGGER.warn("[chuying] 将棋引擎无可用走法，outcome={}", resultOutcomeMethod.invoke(result));
                return null;
            }
            return String.valueOf(best.get());
        } catch (Throwable t) {
            Chuying.LOGGER.warn("[chuying] 将棋引擎搜索失败：{}", t.toString());
            return null;
        }
    }

    private static Object ensureEngine() {
        if (engineReady) {
            return engine;
        }
        if (engineFailed) {
            return null;
        }
        synchronized (ShogiCompat.class) {
            if (engineReady) {
                return engine;
            }
            if (engineFailed) {
                return null;
            }
            try {
                Chuying.LOGGER.info("[chuying] 正在初始化将棋引擎（首次使用，加载 eval/book）…");
                ClassLoader loader = ShogiCompat.class.getClassLoader();
                Class<?> resourcesClass = Class.forName(SUNFISH_RESOURCES, false, loader);
                Class<?> engineClass = Class.forName(SUNFISH_ENGINE, false, loader);
                Class<?> limitsClass = Class.forName(SEARCH_LIMITS, false, loader);
                Class<?> requestClass = Class.forName(SEARCH_REQUEST, false, loader);
                Class<?> resultClass = Class.forName(SEARCH_RESULT, false, loader);
                Class<?> tokenClass = Class.forName(CANCELLATION_TOKEN, false, loader);

                searchLimitsCtor = limitsClass.getConstructor(Duration.class, int.class, long.class, int.class, int.class);
                searchRequestCurrentMethod = requestClass.getMethod("currentPosition", String.class, limitsClass);
                cancellationNone = tokenClass.getField("NONE").get(null);
                engineInitializeMethod = engineClass.getMethod("initialize");
                engineSearchMethod = engineClass.getMethod("search", requestClass, tokenClass);
                resultBestMoveMethod = resultClass.getMethod("bestMove");
                resultOutcomeMethod = resultClass.getMethod("outcome");

                Method fromClasspath = resourcesClass.getMethod("fromClasspath", ClassLoader.class, String.class);
                Object resources = fromClasspath.invoke(null, loader, RESOURCE_DIR);
                engine = engineClass.getConstructor(resourcesClass).newInstance(resources);
                engineInitializeMethod.invoke(engine);
                engineReady = true;
                Chuying.LOGGER.info("[chuying] 将棋引擎已就绪");
            } catch (Throwable t) {
                engineFailed = true;
                Chuying.LOGGER.warn("[chuying] 将棋引擎初始化失败，将棋代打已禁用：{}", t.toString());
            }
        }
        return engine;
    }

    // ---- 升变界面自动应答（主线程调用） ----

    /**
     * 若当前屏幕是 tlm_shogi 的升变选择界面，则依据本次走法是否升变自动回包并关闭；
     * 否则什么都不做。必须在主线程（客户端 tick）调用。
     */
    public static void autoAnswerPromote(Minecraft mc) {
        if (!isAvailable() || mc == null || promoteScreenClass == null) {
            return;
        }
        Screen screen = mc.screen;
        if (screen == null || !promoteScreenClass.isInstance(screen)) {
            return;
        }
        try {
            BlockPos chessPos = (BlockPos) screenChessPosField.get(screen);
            int fromPos = screenFromPosField.getInt(screen);
            int toPos = screenToPosField.getInt(screen);
            // choice：1=升变、2=不升变（0=取消，服务器忽略）
            int choice = pendingPromote ? 1 : 2;
            Object payload;
            if (screenExpectedSfenField != null) {
                String expectedSfen = (String) screenExpectedSfenField.get(screen);
                payload = promoteResultCtor.newInstance(chessPos, expectedSfen, fromPos, toPos, choice);
            } else {
                payload = promoteResultCtor.newInstance(chessPos, fromPos, toPos, choice);
            }
            PacketDistributor.sendToServer((CustomPacketPayload) payload);
            mc.setScreen(null);
            Chuying.LOGGER.info("[chuying] 将棋升变自动应答：from={} to={} promote={}", fromPos, toPos, pendingPromote);
        } catch (Throwable t) {
            Chuying.LOGGER.warn("[chuying] 将棋升变自动应答失败：{}", t.toString());
        }
    }
}
