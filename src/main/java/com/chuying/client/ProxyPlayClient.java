package com.chuying.client;

import com.chuying.Config;
import com.chuying.engine.ChessConverters;
import com.chuying.engine.EngineManager;
import com.chuying.engine.PbrainGomokuEngine;
import com.chuying.engine.UciEngine;
import com.chuying.network.CChessProxyMovePayload;
import com.chuying.network.GomokuProxyMovePayload;
import com.chuying.network.WChessProxyMovePayload;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.block.BlockCChess;
import com.github.tartaricacid.touhoulittlemaid.block.BlockGomoku;
import com.github.tartaricacid.touhoulittlemaid.block.BlockWChess;
import com.github.tartaricacid.touhoulittlemaid.block.properties.GomokuPart;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/**
 * 客户端代打核心（纯客户端）：
 * <ul>
 *   <li>快捷键 K 切换开关</li>
 *   <li>每 tick 检测准星对准的棋盘，轮到玩家且局面变化时，用外挂引擎算招并发给服务端</li>
 * </ul>
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class ProxyPlayClient {
    /** 引擎走法被拒绝等导致局面卡住时，超过该时间重新允许走当前局面 */
    private static final long STUCK_TIMEOUT_MS = 10_000;

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && ProxyPlayKey.PROXY_KEY.matches(event.getKey(), event.getScanCode())) {
            ProxyPlayKey.PROXY_KEY.consumeClick();
            ProxyPlayState.enabled = !ProxyPlayState.enabled;
            if (!ProxyPlayState.enabled) {
                ProxyPlayState.lastFen = "";
                ProxyPlayState.lastSentAt = 0;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(
                        ProxyPlayState.enabled ? "hud.chuying.proxy_on" : "hud.chuying.proxy_off"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!Config.ENABLED.get() || !ProxyPlayState.enabled || ProxyPlayState.busy) {
            return;
        }
        // 卡住恢复：10 秒无进展则重新允许走当前局面
        if (ProxyPlayState.lastSentAt != 0 && System.currentTimeMillis() - ProxyPlayState.lastSentAt > STUCK_TIMEOUT_MS) {
            ProxyPlayState.lastFen = "";
            ProxyPlayState.lastSentAt = 0;
        }

        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr)) {
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();

        BlockPos center = null;
        if (block instanceof BlockCChess) {
            GomokuPart part = mc.level.getBlockState(pos).getValue(BlockCChess.PART);
            center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        } else if (block instanceof BlockWChess) {
            GomokuPart part = mc.level.getBlockState(pos).getValue(BlockWChess.PART);
            center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        } else if (block instanceof BlockGomoku) {
            GomokuPart part = mc.level.getBlockState(pos).getValue(BlockGomoku.PART);
            center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        }
        if (center == null) {
            return;
        }
        BlockEntity te = mc.level.getBlockEntity(center);
        if (te instanceof TileEntityCChess c) {
            tryCChess(mc, center, c);
        } else if (te instanceof TileEntityWChess w) {
            tryWChess(mc, center, w);
        } else if (te instanceof TileEntityGomoku g) {
            tryGomoku(mc, center, g);
        }
    }

    private static void tryCChess(Minecraft mc, BlockPos center, TileEntityCChess c) {
        if (!c.isPlayerTurn() || c.isCheckmate() || c.isMoveNumberLimit() || c.isRepeat()) {
            return;
        }
        String fen = c.getChessData().toFen();
        if (fen.equals(ProxyPlayState.lastFen)) {
            return;
        }
        UciEngine engine = EngineManager.cchess();
        if (engine == null) {
            noticeNoEngine("message.chuying.no_cchess_engine");
            return;
        }
        claimPosition(fen);
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        CompletableFuture.runAsync(() -> {
            try {
                String uci = engine.bestMove(fen, thinkMs);
                if (uci == null) {
                    return;
                }
                int move = ChessConverters.cchessUciToMove(uci);
                if (move == 0) {
                    return;
                }
                final int moveFinal = move;
                mc.execute(() -> PacketDistributor.sendToServer(new CChessProxyMovePayload(center, moveFinal)));
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    private static void tryWChess(Minecraft mc, BlockPos center, TileEntityWChess w) {
        if (!w.isPlayerTurn() || w.isCheckmate() || w.isMoveNumberLimit() || w.isRepeat()) {
            return;
        }
        String fen = w.getChessData().toFen();
        if (fen.equals(ProxyPlayState.lastFen)) {
            return;
        }
        UciEngine engine = EngineManager.wchess();
        if (engine == null) {
            noticeNoEngine("message.chuying.no_wchess_engine");
            return;
        }
        claimPosition(fen);
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        CompletableFuture.runAsync(() -> {
            try {
                String uci = engine.bestMove(fen, thinkMs);
                if (uci == null) {
                    return;
                }
                int move = ChessConverters.wchessUciToMove(uci);
                if (move == 0) {
                    return;
                }
                final int moveFinal = move;
                mc.execute(() -> PacketDistributor.sendToServer(new WChessProxyMovePayload(center, moveFinal)));
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    private static void tryGomoku(Minecraft mc, BlockPos center, TileEntityGomoku g) {
        if (!g.isPlayerTurn() || g.getStatue() != Statue.IN_PROGRESS) {
            return;
        }
        byte[][] board = g.getChessData();
        String fp = gomokuFingerprint(g);
        if (fp.equals(ProxyPlayState.lastFen)) {
            return;
        }
        PbrainGomokuEngine engine = EngineManager.gomoku();
        if (engine == null) {
            noticeNoEngine("message.chuying.no_gomoku_engine");
            return;
        }
        claimPosition(fp);
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        CompletableFuture.runAsync(() -> {
            try {
                int[] xy = engine.bestMove(board, thinkMs);
                if (xy == null) {
                    return;
                }
                Point point = new Point(xy[0], xy[1], Point.BLACK);
                final Point pointFinal = point;
                mc.execute(() -> PacketDistributor.sendToServer(new GomokuProxyMovePayload(center, pointFinal)));
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    /** 五子棋局面指纹：回合数 + 最近落子，用于判断是否是新回合 */
    private static String gomokuFingerprint(TileEntityGomoku g) {
        Point p = g.getLatestChessPoint();
        return "c" + g.getChessCounter() + ":" + p.x + "," + p.y;
    }

    private static void claimPosition(String fingerprint) {
        ProxyPlayState.lastFen = fingerprint;
        ProxyPlayState.lastSentAt = System.currentTimeMillis();
        ProxyPlayState.busy = true;
    }

    private static void noticeNoEngine(String key) {
        long now = System.currentTimeMillis();
        if (now - ProxyPlayState.lastNoEngineNotice < 5_000) {
            return;
        }
        ProxyPlayState.lastNoEngineNotice = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
