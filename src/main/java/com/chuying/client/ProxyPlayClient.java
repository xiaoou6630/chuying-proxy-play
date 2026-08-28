package com.chuying.client;

import com.chuying.Chuying;
import com.chuying.Config;
import com.chuying.engine.ChessConverters;
import com.chuying.engine.EngineManager;
import com.chuying.engine.PbrainGomokuEngine;
import com.chuying.engine.UciEngine;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;

/**
 * 客户端代打核心（纯客户端）：
 * <ul>
 *   <li>快捷键 K 切换开关</li>
 *   <li>每 tick 检测准星对准的棋盘，轮到玩家且局面变化时，用外挂引擎算招，
 *       再模拟玩家右键棋盘交叉点落子（走 TLM 原版交互，服务器无需安装本 mod）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Chuying.MODID, value = Dist.CLIENT)
public class ProxyPlayClient {
    /** 引擎走法被拒绝等导致局面卡住时，超过该时间重新允许走当前局面 */
    private static final long STUCK_TIMEOUT_MS = 10_000;

    /** 待执行的模拟点击（象棋需要"选子→落子"两步，间隔数 tick） */
    private static final ArrayDeque<PendingClick> PENDING_CLICKS = new ArrayDeque<>();
    /** 象棋两步点击的间隔 tick */
    private static final int CHESS_STEP_DELAY_TICKS = 2;

    private static final class PendingClick {
        final BlockHitResult hit;
        int delayTicks;

        PendingClick(BlockHitResult hit, int delayTicks) {
            this.hit = hit;
            this.delayTicks = delayTicks;
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS && ProxyPlayKey.PROXY_KEY.matches(event.getKey(), event.getScanCode())) {
            ProxyPlayKey.PROXY_KEY.consumeClick();
            ProxyPlayState.enabled = !ProxyPlayState.enabled;
            if (!ProxyPlayState.enabled) {
                ProxyPlayState.lastFen = "";
                ProxyPlayState.lastSentAt = 0;
                PENDING_CLICKS.clear();
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable(
                        ProxyPlayState.enabled ? "hud.chuying.proxy_on" : "hud.chuying.proxy_off"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // 优先执行排队的模拟点击（象棋两步间隔）
        processPendingClicks();

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
        Direction facing = null;
        if (block instanceof BlockCChess) {
            var state = mc.level.getBlockState(pos);
            GomokuPart part = state.getValue(BlockCChess.PART);
            center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
            facing = state.getValue(BlockCChess.FACING);
        } else if (block instanceof BlockWChess) {
            var state = mc.level.getBlockState(pos);
            GomokuPart part = state.getValue(BlockWChess.PART);
            center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
            facing = state.getValue(BlockWChess.FACING);
        } else if (block instanceof BlockGomoku) {
            var state = mc.level.getBlockState(pos);
            GomokuPart part = state.getValue(BlockGomoku.PART);
            center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
            facing = state.getValue(BlockGomoku.FACING);
        }
        if (center == null || facing == null) {
            return;
        }
        BlockEntity te = mc.level.getBlockEntity(center);
        if (te instanceof TileEntityCChess c) {
            tryCChess(mc, center, facing, c);
        } else if (te instanceof TileEntityWChess w) {
            tryWChess(mc, center, facing, w);
        } else if (te instanceof TileEntityGomoku g) {
            tryGomoku(mc, center, g);
        }
    }

    private static void processPendingClicks() {
        if (PENDING_CLICKS.isEmpty()) {
            return;
        }
        var it = PENDING_CLICKS.iterator();
        while (it.hasNext()) {
            PendingClick pc = it.next();
            if (pc.delayTicks-- <= 0) {
                BoardClicker.sendUseItemOn(pc.hit);
                it.remove();
            }
        }
    }

    private static void tryCChess(Minecraft mc, BlockPos center, Direction facing, TileEntityCChess c) {
        // 对局被重置/换新（回合计数回退）时清除局面去重，无需按 K 重启
        int counter = c.getChessCounter();
        if (counter < ProxyPlayState.lastCChessCounter) {
            ProxyPlayState.lastFen = "";
        }
        ProxyPlayState.lastCChessCounter = counter;

        if (!c.isPlayerTurn() || c.isCheckmate() || c.isMoveNumberLimit() || c.isRepeat()) {
            Chuying.LOGGER.info("[chuying] cchess skip: turn={} mate={} limit={} repeat={}",
                    c.isPlayerTurn(), c.isCheckmate(), c.isMoveNumberLimit(), c.isRepeat());
            return;
        }
        String fen = c.getChessData().toFen();
        if (fen.equals(ProxyPlayState.lastFen)) {
            return;
        }
        UciEngine engine = EngineManager.cchess();
        if (engine == null) {
            Chuying.LOGGER.warn("[chuying] cchess engine not available");
            noticeNoEngine("message.chuying.no_cchess_engine");
            return;
        }
        claimPosition(fen);
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        Chuying.LOGGER.info("[chuying] cchess trigger fen={} center={} facing={}", fen, center, facing);
        CompletableFuture.runAsync(() -> {
            try {
                String uci = engine.bestMove(fen, thinkMs);
                Chuying.LOGGER.info("[chuying] cchess bestmove={}", uci);
                if (uci == null) {
                    return;
                }
                int move = ChessConverters.cchessUciToMove(uci);
                if (move == 0) {
                    return;
                }
                int fromSq = Position.SRC(move);
                int toSq = Position.DST(move);
                mc.execute(() -> scheduleChessMove(center, facing, fromSq, toSq, true));
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    private static void tryWChess(Minecraft mc, BlockPos center, Direction facing, TileEntityWChess w) {
        // 对局被重置/换新（回合计数回退）时清除局面去重，无需按 K 重启
        int counter = w.getChessCounter();
        if (counter < ProxyPlayState.lastWChessCounter) {
            ProxyPlayState.lastFen = "";
        }
        ProxyPlayState.lastWChessCounter = counter;

        if (!w.isPlayerTurn() || w.isCheckmate() || w.isMoveNumberLimit() || w.isRepeat()) {
            Chuying.LOGGER.info("[chuying] wchess skip: turn={} mate={} limit={} repeat={}",
                    w.isPlayerTurn(), w.isCheckmate(), w.isMoveNumberLimit(), w.isRepeat());
            return;
        }
        String fen = w.getChessData().toFen();
        if (fen.equals(ProxyPlayState.lastFen)) {
            return;
        }
        UciEngine engine = EngineManager.wchess();
        if (engine == null) {
            Chuying.LOGGER.warn("[chuying] wchess engine not available");
            noticeNoEngine("message.chuying.no_wchess_engine");
            return;
        }
        claimPosition(fen);
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        Chuying.LOGGER.info("[chuying] wchess trigger fen={} center={} facing={}", fen, center, facing);
        CompletableFuture.runAsync(() -> {
            try {
                String uci = engine.bestMove(fen, thinkMs);
                Chuying.LOGGER.info("[chuying] wchess bestmove={}", uci);
                if (uci == null) {
                    return;
                }
                int move = ChessConverters.wchessUciToMove(uci);
                if (move == 0) {
                    return;
                }
                int fromSq = com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.SRC(move);
                int toSq = com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.DST(move);
                mc.execute(() -> scheduleChessMove(center, facing, fromSq, toSq, false));
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    private static void tryGomoku(Minecraft mc, BlockPos center, TileEntityGomoku g) {
        // 对局被重置/换新（回合计数回退）时清除局面去重，无需按 K 重启
        int counter = g.getChessCounter();
        if (counter < ProxyPlayState.lastGomokuCounter) {
            ProxyPlayState.lastFen = "";
        }
        ProxyPlayState.lastGomokuCounter = counter;

        if (!g.isPlayerTurn() || g.getStatue() != Statue.IN_PROGRESS) {
            return;
        }
        int[][] raw = g.getChessData();
        // TLM getChessData() 返回 int[][]，rapfi 引擎需要 byte[][]（0=空/1=黑/2=白）
        byte[][] board = new byte[raw.length][raw.length];
        for (int x = 0; x < raw.length; x++) {
            for (int y = 0; y < raw[x].length; y++) {
                board[x][y] = (byte) raw[x][y];
            }
        }
        // 诊断：统计客户端棋盘的黑/白子数，确认 rapfi 收到的局面是否完整
        int black = 0;
        int white = 0;
        StringBuilder occupied = new StringBuilder();
        for (int x = 0; x < board.length; x++) {
            for (int y = 0; y < board.length; y++) {
                if (board[x][y] == Point.BLACK) {
                    black++;
                    occupied.append('B').append(x).append(',').append(y).append(' ');
                } else if (board[x][y] == Point.WHITE) {
                    white++;
                    occupied.append('W').append(x).append(',').append(y).append(' ');
                }
            }
        }
        Chuying.LOGGER.info("[chuying] gomoku trigger black={} white={} board={}", black, white, occupied);
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
                int x = xy[0];
                int y = xy[1];
                mc.execute(() -> BoardClicker.sendUseItemOn(BoardClicker.gomokuHit(center, x, y)));
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    /** 象棋"选子→落子"两步模拟点击，先点起点格，间隔数 tick 再点终点格 */
    private static void scheduleChessMove(BlockPos center, Direction facing, int fromSq, int toSq, boolean cchess) {
        BlockHitResult fromHit = chessHit(center, facing, fromSq, cchess);
        BlockHitResult toHit = chessHit(center, facing, toSq, cchess);
        Chuying.LOGGER.info("[chuying] {} move fromSq={} toSq={} fromHit={} toHit={}",
                cchess ? "cchess" : "wchess", fromSq, toSq, fromHit.getLocation(), toHit.getLocation());
        PENDING_CLICKS.add(new PendingClick(fromHit, 0));
        PENDING_CLICKS.add(new PendingClick(toHit, CHESS_STEP_DELAY_TICKS));
    }

    private static BlockHitResult chessHit(BlockPos center, Direction facing, int sq, boolean cchess) {
        int file;
        int rank;
        if (cchess) {
            file = Position.FILE_X(sq) - Position.FILE_LEFT;
            rank = Position.RANK_Y(sq) - Position.RANK_TOP;
        } else {
            file = com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.FILE_X(sq)
                    - com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.FILE_LEFT;
            rank = com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.RANK_Y(sq)
                    - com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.RANK_TOP;
        }
        return BoardClicker.chessHit(center, facing, file, rank, cchess);
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
