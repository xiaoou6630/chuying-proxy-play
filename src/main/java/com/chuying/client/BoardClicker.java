package com.chuying.client;

import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 纯客户端"模拟玩家右键棋盘交叉点"，替代自定义网络包。
 * <p>
 * 原理：用 {@link net.minecraft.client.multiplayer.MultiPlayerGameMode#useItemOn} 发送
 * 原版 {@code ServerboundUseItemOnPacket}，服务端视角就是玩家自己点击棋盘，
 * TLM 的 {@code Block.useItemOn} 照常处理落子。
 * 因此服务器无需安装本 mod，也不会再报"网络通道不匹配"。
 * <p>
 * 坐标换算逆推自 TLM 1.5.3（NeoForge 1.21.1）的棋盘点击解析逻辑
 * （BlockGomoku.getChessPos / CChessUtil.getClickPosition / WChessUtil.getClickPosition，
 * 常量已通过 javap 字节码核对一致）。
 */
public final class BoardClicker {
    /** 五子棋格距与命中区间（TLM getData 常量） */
    private static final double GOMOKU_STEP = 0.1316;
    private static final double GOMOKU_HIT_HALF = 0.035; // 0.07 区间中点

    /** 五子棋 9 个 part 的偏移参数（顺序：上行→中行→下行，每行左→中→右） */
    private record GPart(GomokuPart part, double xStart, int xIdxOff, double yStart, int yIdxOff) {
    }

    private static final GPart[] GOMOKU_PARTS = {
            new GPart(GomokuPart.LEFT_UP, 0.54, 0, 0.54, 0),
            new GPart(GomokuPart.UP, 0.08, 4, 0.54, 0),
            new GPart(GomokuPart.RIGHT_UP, -0.01, 11, 0.54, 0),
            new GPart(GomokuPart.LEFT_CENTER, 0.54, 0, 0.07, 4),
            new GPart(GomokuPart.CENTER, 0.08, 4, 0.07, 4),
            new GPart(GomokuPart.RIGHT_CENTER, -0.01, 11, 0.07, 4),
            new GPart(GomokuPart.LEFT_DOWN, 0.54, 0, 0.0, 11),
            new GPart(GomokuPart.DOWN, 0.08, 4, 0.0, 11),
            new GPart(GomokuPart.RIGHT_DOWN, -0.01, 11, 0.0, 11),
    };

    private BoardClicker() {
    }

    /**
     * 五子棋：构造点击棋盘 (i, j) 交叉点的命中结果（i/j 为 0~14 行列）。
     * 被点击方块按 (i, j) 落在棋盘 3x3 中的对应 part。
     */
    public static BlockHitResult gomokuHit(BlockPos center, int i, int j) {
        GPart gp = gomokuPartFor(i, j);
        BlockPos pos = center.offset(gp.part().getPosX(), 0, gp.part().getPosY());
        double x = gp.xStart() + (i - gp.xIdxOff()) * GOMOKU_STEP + GOMOKU_HIT_HALF;
        double z = gp.yStart() + (j - gp.yIdxOff()) * GOMOKU_STEP + GOMOKU_HIT_HALF;
        Vec3 hit = Vec3.atBottomCenterOf(pos).add(x - 0.5, 0, z - 0.5);
        return new BlockHitResult(hit, Direction.UP, pos, false);
    }

    /**
     * 中象/国象：构造点击 (file, rank) 格的命中结果（file/rank 为相对棋盘原点的下标，0 起）。
     * <p>
     * 推导：TLM 服务端把命中点按 part 偏移后 yRot 旋转得到棋盘坐标系 clickPos，
     * part 偏移在求世界坐标时会抵消，故被点击方块直接用棋盘中心方块（CENTER part）。
     */
    public static BlockHitResult chessHit(BlockPos center, Direction facing, int file, int rank, boolean cchess) {
        double cx = cchess ? file * 0.304 - 1.365 : file * 0.25 - 1;
        double cz = cchess ? rank * 0.304 - 1.370 : rank * 0.25 - 1;
        // 逆旋转：服务端做 local.yRot(angle)，故 local = clickPos.yRot(-angle)
        Vec3 local = new Vec3(cx, 0, cz).yRot(-facing.toYRot() * Mth.DEG_TO_RAD);
        Vec3 hit = new Vec3(center.getX() + 0.5, center.getY(), center.getZ() + 0.5).add(local);
        return new BlockHitResult(hit, Direction.UP, center, false);
    }

    /** 发送模拟右键（TLM 棋盘要求空手，非空手时忽略） */
    public static void sendUseItemOn(BlockHitResult bhr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            return;
        }
        if (!mc.player.getMainHandItem().isEmpty()) {
            return;
        }
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
    }

    /** 由行列选 part：x 左(0~3)/中(4~10)/右(11~14)，z 上(0~3)/中(4~10)/下(11~14) */
    private static GPart gomokuPartFor(int i, int j) {
        int ix = i <= 3 ? 0 : (i <= 10 ? 1 : 2);
        int iy = j <= 3 ? 0 : (j <= 10 ? 1 : 2);
        return GOMOKU_PARTS[iy * 3 + ix];
    }
}
