package com.chuying.client;

import com.chuying.Chuying;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
     * part 偏移在求世界坐标时会抵消，故坐标换算与被点击方块无关。
     * 但服务端会校验"命中点距被点击方块不能太远"，因此这里动态选择最靠近命中点的
     * part 方块（3x3 之一）作为被点击方块，保证边缘格也能通过校验。
     */
    public static BlockHitResult chessHit(BlockPos center, Direction facing, int file, int rank, boolean cchess) {
        double cx = cchess ? file * 0.304 - 1.365 : file * 0.25 - 1;
        double cz = cchess ? rank * 0.304 - 1.370 : rank * 0.25 - 1;
        // 逆旋转：服务端做 local.yRot(angle)，故 local = clickPos.yRot(-angle)
        Vec3 local = new Vec3(cx, 0, cz).yRot(-facing.toYRot() * Mth.DEG_TO_RAD);
        // 命中点 = center + (0.5,0,0.5) + local
        double hx = center.getX() + 0.5 + local.x();
        double hz = center.getZ() + 0.5 + local.z();
        // 选一个使命中点落进方块范围内的 part（局部坐标 0.5+local ±0.5）
        int px = Mth.clamp((int) Math.floor(0.5 + local.x()), -1, 1);
        int pz = Mth.clamp((int) Math.floor(0.5 + local.z()), -1, 1);
        BlockPos pos = center.offset(px, 0, pz);
        Vec3 hit = new Vec3(hx, center.getY(), hz);
        return new BlockHitResult(hit, Direction.UP, pos, false);
    }

    /** 发送模拟右键（TLM 棋盘要求空手操作，主手非空时提示玩家清空并忽略） */
    public static void sendUseItemOn(BlockHitResult bhr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            return;
        }
        if (!mc.player.getMainHandItem().isEmpty()) {
            // TLM 棋盘要求空手，不自动改动玩家装备，仅提示
            mc.player.displayClientMessage(Component.translatable("message.chuying.need_empty_hand"), true);
            return;
        }
        Chuying.LOGGER.info("[chuying] click pos={} hit={}", bhr.getBlockPos(), bhr.getLocation());
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
    }

    /** 由行列选 part：x 左(0~3)/中(4~10)/右(11~14)，z 上(0~3)/中(4~10)/下(11~14) */
    private static GPart gomokuPartFor(int i, int j) {
        int ix = i <= 3 ? 0 : (i <= 10 ? 1 : 2);
        int iy = j <= 3 ? 0 : (j <= 10 ? 1 : 2);
        return GOMOKU_PARTS[iy * 3 + ix];
    }
}
