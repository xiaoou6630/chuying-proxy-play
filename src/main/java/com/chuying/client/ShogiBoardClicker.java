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
 *   point = JChessUtil.getClickPosition(local)
 * </pre>
 * 由于 part 的世界偏移恰等于其 {@code PART} 的 (posX,posY)，式中的 part 偏移在求世界坐标时抵消，
 * 于是：{@code hitLoc.xz = center + (0.5,0.5) + local.yRot(-angle)}。
 * <p>
 * 格点号换算（逆推自 {@code JChessUtil.getClickPosition} 字节码）：
 * <ul>
 *   <li>棋盘格 0~80：{@code i = floor((lx+0.4744)/0.1055)}、{@code j = floor((lz+0.4744)/0.1055)}，{@code point = i + j*9}</li>
 *   <li>手驹 81~89：{@code i = floor((lx-0.512)/0.1167)}、{@code j = floor((lz-0.157)/0.1096)}，{@code point = 81 + i + j*3}</li>
 * </ul>
 */
public final class ShogiBoardClicker {
    /** 棋盘格宽/原点（TLM getClickPosition 常量） */
    private static final double STEP = 0.1055;
    private static final double ORIGIN = 0.4744;
    /** 手驹 3x3 区域起点与格距 */
    private static final double HAND_X0 = 0.512;
    private static final double HAND_DX = 0.1167;
    private static final double HAND_Z0 = 0.157;
    private static final double HAND_DZ = 0.1096;

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
            int i = idx % 3;
            int j = idx / 3;
            lx = HAND_X0 + (i + 0.5) * HAND_DX;
            lz = HAND_Z0 + (j + 0.5) * HAND_DZ;
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
        Vec3 hit = new Vec3(hx, center.getY(), hz);
        return new BlockHitResult(hit, Direction.UP, pos, false);
    }
}
