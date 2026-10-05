package com.chuying.compat;

import com.chuying.Chuying;
import com.github.tartaricacid.touhoulittlemaid.api.block.IBoardGameBlock;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.block.BlockCChess;
import com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku;
import com.github.tartaricacid.touhoulittlemaid.block.BlockWChess;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * TouhouGO（车万女仆·围棋棋盘）的软依赖适配：**全部反射调用，不 import 它的任何类**，
 * 属于本仓库对可选模组的既定惯例（见 {@link ShogiCompat} / {@link ChessPvpCompat}）。
 * <p>
 * 未安装 TouhouGO 时 {@link #isAvailable()} 为 false，其它棋种完全不受影响。
 * <p>
 * 之所以不能用「包名写死」的方式探测：TouhouGO 的 modid 由 AutoForge 生成
 * （{@code touhou_go_board_1790384435}，带一串数字后缀），作者重新生成就会变。
 * 因此这里按「注册名/类名 + TLM 的 IBoardGameBlock 接口」来认棋盘，
 * 反射方法则直接从拿到的方块/方块实体实例的类上取。
 */
public final class GoCompat {

    /** 模组棋盘为 15 路（TouhouGO 的 GoRules.SIZE） */
    public static final int SIZE = 15;

    private static boolean probed = false;
    private static boolean available = false;
    private static EnumProperty<GomokuPart> partProperty;
    private static DirectionProperty facingProperty;

    /** 反射方法缓存：key = 类名 + 方法名（同一模组下只有一套类） */
    private static final Map<String, Method> METHODS = new HashMap<>();
    /** 已确认不存在的方法（避免每次调用都重新探测并刷日志） */
    private static final java.util.Set<String> MISSING = new java.util.HashSet<>();
    /** GoMovePayload(BlockPos, int, int) 的构造器（"举报一手"用） */
    private static Constructor<?> maidPassCtor;
    private static boolean maidPassProbed = false;

    private GoCompat() {
    }

    // ------------------------------------------------------------------
    // 探测
    // ------------------------------------------------------------------

    /** 是否装了 TouhouGO（首次调用会遍历方块注册表一次，之后走缓存） */
    public static boolean isAvailable() {
        probe();
        return available;
    }

    private static synchronized void probe() {
        if (probed) {
            return;
        }
        probed = true;
        try {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (looksLikeGoBoard(block)) {
                    available = true;
                    cacheProperties(block.getClass());
                    break;
                }
            }
        } catch (Throwable t) {
            // 注册表不可用（过早调用）等：保持 available=false，只提示一次
            Chuying.LOGGER.warn("[chuying] GoCompat 探测失败（围棋代打将不可用）", t);
        }
        if (available) {
            Chuying.LOGGER.info("[chuying] 检测到 TouhouGO 围棋棋盘，围棋代打可用");
        }
    }

    /**
     * 认棋盘：TLM 的棋盘接口 + 排除 TLM 自带三种棋（它们各有专用分支），
     * 再按注册名/类名确认是围棋棋盘。
     */
    private static boolean looksLikeGoBoard(Block block) {
        if (!(block instanceof IBoardGameBlock)) {
            return false;
        }
        if (block instanceof BlockGomoku || block instanceof BlockCChess || block instanceof BlockWChess) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String namespace = id == null ? "" : id.getNamespace().toLowerCase(Locale.ROOT);
        String path = id == null ? "" : id.getPath().toLowerCase(Locale.ROOT);
        String cls = block.getClass().getName().toLowerCase(Locale.ROOT);
        // namespace 前缀匹配兼容 AutoForge 重新生成的 modid
        return namespace.startsWith("touhou_go_board") || "go_board".equals(path) || cls.contains("blockgo");
    }

    private static void cacheProperties(Class<?> blockClass) {
        try {
            Field part = blockClass.getField("PART");
            Object value = part.get(null);
            if (value instanceof EnumProperty<?> property) {
                @SuppressWarnings("unchecked")
                EnumProperty<GomokuPart> cast = (EnumProperty<GomokuPart>) property;
                partProperty = cast;
            }
            Field facing = blockClass.getField("FACING");
            Object facingValue = facing.get(null);
            if (facingValue instanceof DirectionProperty property) {
                facingProperty = property;
            }
        } catch (Throwable t) {
            Chuying.LOGGER.warn("[chuying] GoCompat 读取棋盘属性失败", t);
        }
    }

    // ------------------------------------------------------------------
    // 方块 / 方块实体判定
    // ------------------------------------------------------------------

    public static boolean isGoBoard(Block block) {
        if (!isAvailable() || partProperty == null) {
            return false;
        }
        return looksLikeGoBoard(block);
    }

    public static boolean isGoTile(BlockEntity te) {
        if (te == null || !isAvailable()) {
            return false;
        }
        return looksLikeGoBoard(te.getBlockState().getBlock());
    }

    /** 九宫分块（TLM 的 GomokuPart；TouhouGO 直接复用），失败返回 null */
    public static GomokuPart part(BlockState state) {
        if (state == null || partProperty == null) {
            return null;
        }
        try {
            return state.getValue(partProperty);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 棋盘朝向，失败返回 null */
    public static Direction facing(BlockState state) {
        if (state == null || facingProperty == null) {
            return null;
        }
        try {
            return state.getValue(facingProperty);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 局面读取（全部反射，异常吞掉并降级）
    // ------------------------------------------------------------------

    private static Method method(BlockEntity te, String name, Class<?>... params) {
        String key = te.getClass().getName() + "#" + name;
        if (MISSING.contains(key)) {
            return null;
        }
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Method m = te.getClass().getMethod(name, params);
            m.setAccessible(true);
            METHODS.put(key, m);
            return m;
        } catch (Throwable t) {
            MISSING.add(key);
            Chuying.LOGGER.warn("[chuying] GoCompat 缺少方法 {}（TouhouGO 版本不兼容？）", name);
            return null;
        }
    }

    private static Object call(BlockEntity te, String name, Class<?>[] params, Object... args) {
        Method m = method(te, name, params);
        if (m == null) {
            return null;
        }
        try {
            return m.invoke(te, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 棋面快照（拷贝一份，避免把方块实体的活数组交给后台线程） */
    public static byte[][] board(BlockEntity te) {
        Object value = call(te, "getBoard", new Class<?>[0]);
        if (!(value instanceof byte[][] src) || src.length < SIZE) {
            return null;
        }
        byte[][] copy = new byte[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            if (src[x] == null || src[x].length < SIZE) {
                return null;
            }
            System.arraycopy(src[x], 0, copy[x], 0, SIZE);
        }
        return copy;
    }

    public static boolean isPlayerTurn(BlockEntity te) {
        Object value = call(te, "isPlayerTurn", new Class<?>[0]);
        return value instanceof Boolean b && b;
    }

    public static Statue statue(BlockEntity te) {
        Object value = call(te, "getStatue", new Class<?>[0]);
        return value instanceof Statue s ? s : Statue.IN_PROGRESS;
    }

    public static int moveCounter(BlockEntity te) {
        Object value = call(te, "getMoveCounter", new Class<?>[0]);
        return value instanceof Integer i ? i : -1;
    }

    public static int koX(BlockEntity te) {
        Object value = call(te, "getKoX", new Class<?>[0]);
        return value instanceof Integer i ? i : -1;
    }

    public static int koY(BlockEntity te) {
        Object value = call(te, "getKoY", new Class<?>[0]);
        return value instanceof Integer i ? i : -1;
    }

    public static int lastX(BlockEntity te) {
        Object value = call(te, "getLastX", new Class<?>[0]);
        return value instanceof Integer i ? i : -1;
    }

    public static int lastY(BlockEntity te) {
        Object value = call(te, "getLastY", new Class<?>[0]);
        return value instanceof Integer i ? i : -1;
    }

    // ------------------------------------------------------------------
    // "举报一手"（整活功能，独立于代打）
    // ------------------------------------------------------------------

    /**
     * 反射找 TouhouGO 的 {@code network.GoMovePayload}：它的构造器是 {@code (BlockPos, int, int)}，
     * 其中 x/y 为负表示"停一手"。包名不写死：从方块实体类名反推模组根包
     * （{@code <root>.blockentity.TileEntityGo} -> {@code <root>.network.GoMovePayload}）。
     */
    private static Constructor<?> maidPassConstructor(BlockEntity te) {
        if (maidPassProbed) {
            return maidPassCtor;
        }
        maidPassProbed = true;
        try {
            String name = te.getClass().getName();
            int cut = name.indexOf(".blockentity");
            if (cut > 0) {
                Class<?> payload = Class.forName(name.substring(0, cut) + ".network.GoMovePayload");
                maidPassCtor = payload.getConstructor(BlockPos.class, int.class, int.class);
            }
        } catch (Throwable t) {
            Chuying.LOGGER.warn("[chuying] 找不到 TouhouGO 的 GoMovePayload，举报一手不可用", t);
        }
        return maidPassCtor;
    }

    /**
     * 举报一手：以"女仆的应手"为名义回一个负坐标，服务端会执行 {@code go.pass(WHITE)}，
     * 即女仆被判停一手（服务端只校验"是否女仆回合"，不校验这是谁的决定）。
     * <p>
     * 注：这条走的是模组自己的 {@code go_to_server} 通道，和代打（原版右键模拟）是两条独立路线。
     *
     * @return 是否已把包发出去
     */
    public static boolean judgeMaidPass(BlockEntity te, BlockPos center) {
        Constructor<?> ctor = maidPassConstructor(te);
        if (ctor == null) {
            return false;
        }
        try {
            Object payload = ctor.newInstance(center, -1, -1);
            if (!(payload instanceof CustomPacketPayload custom)) {
                return false;
            }
            PacketDistributor.sendToServer(custom);
            return true;
        } catch (Throwable t) {
            Chuying.LOGGER.warn("[chuying] 举报一手发送失败", t);
            return false;
        }
    }
}
