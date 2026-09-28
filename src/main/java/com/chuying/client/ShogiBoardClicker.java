package com.chuying.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 将棋棋盘（tlm_shogi，5-part 十字布局）的「模拟玩家右键」坐标换算。
 * <p>
 * 换算公式与 {@link BoardClicker} 同一思路，逆推自 {@code BlockJChess.useItemOn} 字节码：
 * <pre>
 *   local = (hitLoc - clickedPartPos + (partX - 0.5, 0, partY - 0.5)).yRot(facing.toYRot())
 *   point = JChessUtil.getClickPosition(local, position)
 * </pre>
 * 由于 part 的世界偏移恰等于其 {@code PART} 的 (posX,posY)，式中的 part 偏移在求世界坐标时抵消，
 * 于是：{@code hitLoc.xz = center + (0.5,0.5) + local.yRot(-angle)}。
 * <p>
 * 格点号换算（常量取自正式版 {@code JChessUtil} 字节码）：
 * <ul>
 *   <li>棋盘格 0~80：{@code i = floor((lx+0.4744)/0.1055)}、{@code j = floor((lz+0.4744)/0.1055)}，{@code point = i + j*9}</li>
 *   <li>手驹 81~89：{@code col = floor((lx-0.539153125)/0.1167)}、{@code row = floor((lz-0.156425)/0.1096)}，{@code point = 81 + col + row*3}</li>
 * </ul>
 * <p>
 * 命中点的 <b>局部 y 必须为 {@code BOARD_SURFACE_Y}=0.625</b>：正式版
 * {@code JChessUtil.getPlayerHandPosition} 会校验 {@code y≈0.625}（否则手驹落子被判为无效）。
 * 棋盘格不使用 y，因此统一取 0.625 对两者都安全。
 */
public final class ShogiBoardClicker {
    /** 棋盘格宽/原点（正式版 JChessUtil.getClickPosition 常量） */
    private static final double STEP = 0.1055;
    private static final double ORIGIN = 0.4744;
    /** 命中点局部高度（正式版 JChessUtil.BOARD_SURFACE_Y） */
    private static final double BOARD_SURFACE_Y = 0.625;
    /** 手驹区 3x3：每格起点 + 格距 + 驹占据宽/深（正式版 JChessUtil 常量） */
    private static final double HAND_PIECE_MIN_X = 0.539153125;
    private static final double HAND_SLOT_WIDTH = 0.1167;
    private static final double HAND_PIECE_WIDTH = 0.09509375;
    private static final double HAND_PIECE_MIN_Z = 0.156425;
    private static final double HAND_SLOT_DEPTH = 0.1096;
    private static final double HAND_PIECE_DEPTH = 0.099078125;

    private ShogiBoardClicker() {
    }

    /**
     * 构造点击将棋某个格点号的命中结果。
     *
     * @param center  棋盘中心方块（CENTER part）
     * @param facing  中心/被点方块的朝向（与服务器 useItemOn 使用同一朝向）
     * @param pointNum TLM 格点号：0~80 棋盘、81~89 手驹
     */
    public static BlockHitResult shogiHit(BlockPos center, Direction facing, int pointNum) {
        double lx;
        double lz;
        if (pointNum < 81) {
            int i = pointNum % 9;
            int j = pointNum / 9;
            lx = (i + 0.5) * STEP - ORIGIN;
            lz = (j + 0.5) * STEP - ORIGIN;
        } else {
            int idx = pointNum - 81;
            int col = idx % 3;
            int row = idx / 3;
            lx = HAND_PIECE_MIN_X + col * HAND_SLOT_WIDTH + HAND_PIECE_WIDTH / 2.0;
            lz = HAND_PIECE_MIN_Z + row * HAND_SLOT_DEPTH + HAND_PIECE_DEPTH / 2.0;
        }
        // 逆旋转：服务端做 local.yRot(angle)，故 worldDelta = local.yRot(-angle)
        Vec3 delta = new Vec3(lx, 0, lz).yRot(-facing.toYRot() * Mth.DEG_TO_RAD);
        // 选一个命中点落在其范围内的 part 方块（5 块十字：中心 + 上下左右）
        int dx = Mth.clamp((int) Math.floor(0.5 + delta.x), -1, 1);
        int dz = Mth.clamp((int) Math.floor(0.5 + delta.z), -1, 1);
        if (dx != 0 && dz != 0) {
            // 十字布局没有对角块，取主轴保证命中点落在有效 part 内
            if (Math.abs(delta.x) >= Math.abs(delta.z)) {
                dz = 0;
            } else {
                dx = 0;
            }
        }
        BlockPos pos = center.offset(dx, 0, dz);
        double hx = center.getX() + 0.5 + delta.x;
        double hz = center.getZ() + 0.5 + delta.z;
        // 局部 y = hit.y - pos.y，需为 BOARD_SURFACE_Y（手驹落子校验要求）
        Vec3 hit = new Vec3(hx, pos.getY() + BOARD_SURFACE_Y, hz);
        return new BlockHitResult(hit, Direction.UP, pos, false);
    }
}
