package com.chuying.compat;

import com.chuying.Chuying;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 棋圣 ChessPVP 联动软依赖封装：<b>全部反射调用</b>，不 import {@code com.zawu.chesspvp.*}。
 * <p>
 * 玩家未安装棋圣时 {@link #isAvailable()} 返回 false，整体禁用 PVP 联动，其余功能不受影响、不崩溃；
 * 探测失败只打一次警告。
 * <p>
 * PVP 状态存在棋盘方块实体的 persistent data（NeoForge {@link BlockEntity#getPersistentData()}）里：
 * {@code ChessPvpP1}（红方/玩家1）、{@code ChessPvpP2}（黑方/玩家2）、{@code ChessPvpTurn}（仅五子棋）。
 * 棋圣每次改状态都会 {@code refresh()} → {@code sendBlockUpdated()} → update tag，客户端因此能读到。
 */
public final class ChessPvpCompat {
    private static final String UTIL_CLASS = "com.zawu.chesspvp.util.ChessPvpUtil";

    /** 对局阵营：P1（象棋红/白、五子棋黑）/ P2（象棋黑、五子棋白）/ 未加入 */
    public enum Side { P1, P2, NONE }

    /** 从棋盘方块实体读到的 PVP 状态（不可变） */
    public record PvpState(boolean bothJoined, UUID p1, UUID p2, int gomokuTurn) {
    }

    // ---- 探测结果（懒加载、只探测一次） ----
    private static volatile boolean probed = false;
    private static boolean available = false;

    private static Method getP1Method;
    private static Method getP2Method;
    private static Method hasBothMethod;
    private static Method getTurnMethod;

    private ChessPvpCompat() {
    }

    /** 棋圣 ChessPVP 是否可用（未安装/不兼容时为 false，整体禁用 PVP 联动） */
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
        try {
            ClassLoader loader = ChessPvpCompat.class.getClassLoader();
            Class<?> util = Class.forName(UTIL_CLASS, false, loader);
            getP1Method = util.getMethod("getP1", CompoundTag.class);
            getP2Method = util.getMethod("getP2", CompoundTag.class);
            hasBothMethod = util.getMethod("hasBoth", CompoundTag.class);
            getTurnMethod = util.getMethod("getTurn", CompoundTag.class);
            available = true;
            Chuying.LOGGER.info("[chuying] 检测到棋圣 ChessPVP，PVP 代打已启用");
        } catch (Throwable t) {
            available = false;
            Chuying.LOGGER.warn("[chuying] 未检测到棋圣 ChessPVP（或版本不兼容），PVP 代打已禁用：{}", t.toString());
        }
    }

    /**
     * 读取棋盘方块实体的 PVP 状态；未装棋圣、非 PVP 对局、读取失败均返回 null。
     * 必须在主线程调用（客户端方块实体为服务端同步来的副本）。
     */
    public static PvpState read(BlockEntity te) {
        if (!isAvailable() || te == null) {
            return null;
        }
        try {
            CompoundTag tag = te.getPersistentData();
            if (tag == null || tag.isEmpty()) {
                return null;
            }
            UUID p1 = (UUID) getP1Method.invoke(null, tag);
            UUID p2 = (UUID) getP2Method.invoke(null, tag);
            if (p1 == null && p2 == null) {
                return null;
            }
            boolean both = (boolean) hasBothMethod.invoke(null, tag);
            int turn = (int) getTurnMethod.invoke(null, tag);
            return new PvpState(both, p1, p2, turn);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 我在该对局中的阵营；未加入返回 {@link Side#NONE} */
    public static Side mySide(PvpState state, UUID myUuid) {
        if (state == null || myUuid == null) {
            return Side.NONE;
        }
        if (myUuid.equals(state.p1())) {
            return Side.P1;
        }
        if (myUuid.equals(state.p2())) {
            return Side.P2;
        }
        return Side.NONE;
    }

    /**
     * 象棋（中国象棋/国际象棋）：是否轮到我方走。
     * 棋圣 PVP 复用 TLM 的 {@code isPlayerTurn()} 作为「P1 的回合」开关：true → P1 走、false → P2 走。
     */
    public static boolean isMyTurn(PvpState state, Side side, boolean tlmIsPlayerTurn) {
        if (state == null || side == Side.NONE) {
            return false;
        }
        return (side == Side.P1) == tlmIsPlayerTurn;
    }

    /** 五子棋：是否轮到我方走（棋圣用 {@code ChessPvpTurn}：0=P1 走、1=P2 走） */
    public static boolean isMyTurnGomoku(PvpState state, Side side) {
        if (state == null || side == Side.NONE) {
            return false;
        }
        return (side == Side.P1) == (state.gomokuTurn() == 0);
    }
}
