package com.chuying.client;

import com.chuying.Chuying;
import com.chuying.Config;
import com.chuying.compat.ChessPvpCompat;
import com.chuying.compat.GoCompat;
import com.chuying.compat.ShogiCompat;
import com.chuying.engine.ChessConverters;
import com.chuying.engine.EngineManager;
import com.chuying.engine.NativeGoEngine;
import com.chuying.engine.NativeGomokuEngine;
import com.chuying.engine.NativeUciEngine;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
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
@EventBusSubscriber(value = Dist.CLIENT)
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
        /** PVP 代打：本轮点击需服务端处于潜行态才生效 */
        final boolean sneak;

        PendingClick(BlockHitResult hit, int delayTicks) {
            this(hit, delayTicks, false);
        }

        PendingClick(BlockHitResult hit, int delayTicks, boolean sneak) {
            this.hit = hit;
            this.delayTicks = delayTicks;
            this.sneak = sneak;
        }
    }

    /** PVP 判定结果 */
    private enum PvpDecision {
        /** 非 PVP 对局（未装棋圣/未开启/非 PVP 棋盘）：走原有 isPlayerTurn 逻辑 */
        NON_PVP,
        /** PVP 对局且轮到我方：代打需带潜行 */
        MY_TURN,
        /** PVP 对局但未轮到/未双方加入/我只是旁观者：不代打 */
        SKIP
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        // 注：举报一手改成"长按 J 到进度条走满"，在 onClientTick 里轮询按键状态
        //（KeyMapping 的按下事件会带按键重复，用事件做长按计时不可靠）
        if (ProxyPlayKey.PROXY_KEY.matches(event.getKey(), event.getScanCode())) {
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

    /**
     * 举报一手：按住 J 累计到 {@link ProxyPlayState#REPORT_HOLD_MS} 才触发，松开即取消。
     * HUD 上的进度条与倒计时由 {@link ProxyPlayOverlay} 画。
     */
    private static void tickReportKey() {
        Minecraft mc = Minecraft.getInstance();
        boolean holding = ProxyPlayKey.REPORT_KEY.isDown() && mc.screen == null && mc.player != null;
        long now = System.currentTimeMillis();
        if (!holding) {
            // 松开（或本来没按）：清掉长按状态，下次必须重新按满
            ProxyPlayState.reportHoldStart = 0;
            ProxyPlayState.reportHoldFired = false;
            return;
        }
        if (ProxyPlayState.reportHoldStart == 0) {
            ProxyPlayState.reportHoldStart = now;
            ProxyPlayState.reportHoldFired = false;
        }
        if (!ProxyPlayState.reportHoldFired
                && now - ProxyPlayState.reportHoldStart >= ProxyPlayState.REPORT_HOLD_MS) {
            ProxyPlayState.reportHoldFired = true;
            reportOneMove(mc);
        }
    }

    /**
     * 举报一手（整活，和代打彼此独立）：对着围棋棋盘按 J，把女仆这一手判成"停一手"。
     * <p>
     * 视觉/听觉：屏幕中央闪一行大字 + 叮一声；功能上向服务端回一个负坐标的应手
     * （模组自己的 {@code go_to_server} 通道），服务端只校验"是否女仆回合"，因此会执行
     * {@code go.pass(WHITE)}。没对着棋盘 / 不是女仆回合时只表演，不产生任何影响。
     */
    private static void reportOneMove(Minecraft mc) {
        long now = System.currentTimeMillis();
        ProxyPlayState.reportFlashUntil = now + 2500;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F));
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (now < ProxyPlayState.reportCooldownUntil) {
            return; // 连点只表演，不重复发包
        }
        ProxyPlayState.reportCooldownUntil = now + 1500;

        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            reportNotice(mc, "message.chuying.report_need_board");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        Block block = mc.level.getBlockState(pos).getBlock();
        if (!Config.GO_ENABLED.get() || !GoCompat.isGoBoard(block)) {
            reportNotice(mc, "message.chuying.report_need_board");
            return;
        }
        GomokuPart part = GoCompat.part(mc.level.getBlockState(pos));
        if (part == null) {
            reportNotice(mc, "message.chuying.report_need_board");
            return;
        }
        BlockPos center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
        BlockEntity te = mc.level.getBlockEntity(center);
        if (!GoCompat.isGoTile(te)) {
            reportNotice(mc, "message.chuying.report_need_board");
            return;
        }
        // 服务端只接受"轮到女仆（白）"的应手包；isPlayerTurn 为 true 说明该你走
        if (GoCompat.isPlayerTurn(te)) {
            reportNotice(mc, "message.chuying.report_not_maid_turn");
            return;
        }
        if (GoCompat.judgeMaidPass(te, center)) {
            Chuying.LOGGER.info("[chuying] report: 举报一手 -> 女仆被判停一手 @ {}", center);
            reportNotice(mc, "message.chuying.report_done");
        } else {
            reportNotice(mc, "message.chuying.no_go_engine");
        }
    }

    private static void reportNotice(Minecraft mc, String key) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable(key), true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 优先执行排队的模拟点击（象棋两步间隔）
        processPendingClicks();
        // 举报一手：长按计时（与代打开关无关）
        tickReportKey();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!Config.ENABLED.get() || !ProxyPlayState.enabled) {
            return;
        }
        // 将棋升变：服务器弹出升变界面时自动应答（必须早于 busy 判断，界面出现时并不在算招）
        if (Config.SHOGI_ENABLED.get()) {
            ShogiCompat.autoAnswerPromote(mc);
        }
        if (ProxyPlayState.busy) {
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
        if (Config.SHOGI_ENABLED.get() && ShogiCompat.isShogiBoard(block)) {
            // 将棋 5-part：按 PART 的 (posX,posY) 反推中心块
            var state = mc.level.getBlockState(pos);
            int[] off = ShogiCompat.partOffset(state);
            center = pos.subtract(new Vec3i(off[0], 0, off[1]));
            facing = ShogiCompat.facingOf(state);
        } else if (BoardClicker.isSkipped(block)) {
            return;
        } else if (block instanceof BlockCChess) {
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
        } else if (Config.GO_ENABLED.get() && GoCompat.isGoBoard(block)) {
            // 围棋（TouhouGO）：九宫结构与五子棋一致，只是 PART/FACING 在它自己的类里
            var state = mc.level.getBlockState(pos);
            GomokuPart part = GoCompat.part(state);
            if (part == null) {
                return;
            }
            Direction goFacing = GoCompat.facing(state);
            center = pos.subtract(new Vec3i(part.getPosX(), 0, part.getPosY()));
            facing = goFacing != null ? goFacing : Direction.NORTH;
        }
        if (center == null || facing == null) {
            return;
        }
        BlockEntity te = mc.level.getBlockEntity(center);
        if (ShogiCompat.isShogiTile(te)) {
            tryShogi(mc, center, facing, te);
        } else if (te instanceof TileEntityCChess c) {
            tryCChess(mc, center, facing, c);
        } else if (te instanceof TileEntityWChess w) {
            tryWChess(mc, center, facing, w);
        } else if (te instanceof TileEntityGomoku g) {
            tryGomoku(mc, center, g);
        } else if (Config.GO_ENABLED.get() && GoCompat.isGoTile(te)) {
            tryGo(mc, center, te);
        }
    }

    private static void processPendingClicks() {
        if (PENDING_CLICKS.isEmpty()) {
            // 队列清空即结束 PVP 潜行会话，还原玩家的 shift 状态
            BoardClicker.endPvpSneak();
            return;
        }
        // 队列里只要还有 PVP 点击，就保持服务端处于潜行态
        for (PendingClick pc : PENDING_CLICKS) {
            if (pc.sneak) {
                BoardClicker.beginPvpSneak();
                break;
            }
        }
        var it = PENDING_CLICKS.iterator();
        while (it.hasNext()) {
            PendingClick pc = it.next();
            if (pc.delayTicks-- <= 0) {
                if (pc.sneak) {
                    // 每个点击发送前都确保服务端潜行（两步点击可能跨 tick）
                    BoardClicker.beginPvpSneak();
                }
                BoardClicker.sendUseItemOn(pc.hit);
                it.remove();
            }
        }
        if (PENDING_CLICKS.isEmpty()) {
            BoardClicker.endPvpSneak();
        }
    }

    /**
     * PVP 判定（象棋：中国象棋/国际象棋）：非 PVP 返回 {@link PvpDecision#NON_PVP}；
     * PVP 且轮到我方返回 {@code MY_TURN}，否则 {@code SKIP}。
     */
    private static PvpDecision pvpDecision(Minecraft mc, BlockEntity te, boolean tlmIsPlayerTurn) {
        if (!Config.PVP_ENABLED.get() || !ChessPvpCompat.isAvailable() || mc.player == null) {
            return PvpDecision.NON_PVP;
        }
        ChessPvpCompat.PvpState state = ChessPvpCompat.read(te);
        if (state == null) {
            return PvpDecision.NON_PVP;
        }
        if (!state.bothJoined()) {
            return PvpDecision.SKIP;
        }
        ChessPvpCompat.Side side = ChessPvpCompat.mySide(state, mc.player.getUUID());
        return ChessPvpCompat.isMyTurn(state, side, tlmIsPlayerTurn) ? PvpDecision.MY_TURN : PvpDecision.SKIP;
    }

    /** PVP 判定（五子棋：用 {@code ChessPvpTurn} 判断回合，不用 isPlayerTurn） */
    private static PvpDecision pvpDecisionGomoku(Minecraft mc, BlockEntity te) {
        if (!Config.PVP_ENABLED.get() || !ChessPvpCompat.isAvailable() || mc.player == null) {
            return PvpDecision.NON_PVP;
        }
        ChessPvpCompat.PvpState state = ChessPvpCompat.read(te);
        if (state == null) {
            return PvpDecision.NON_PVP;
        }
        if (!state.bothJoined()) {
            return PvpDecision.SKIP;
        }
        ChessPvpCompat.Side side = ChessPvpCompat.mySide(state, mc.player.getUUID());
        return ChessPvpCompat.isMyTurnGomoku(state, side) ? PvpDecision.MY_TURN : PvpDecision.SKIP;
    }

    private static void tryShogi(Minecraft mc, BlockPos center, Direction facing, Object te) {
        // 对局被重置/换新（回合计数回退）时清除局面去重，无需按 K 重启
        int counter = ShogiCompat.chessCounter(te);
        if (counter < ProxyPlayState.lastShogiCounter) {
            ProxyPlayState.lastFen = "";
        }
        ProxyPlayState.lastShogiCounter = counter;

        if (!ShogiCompat.isPlayerTurn(te) || ShogiCompat.isCheckmate(te)
                || ShogiCompat.isMoveNumberLimit(te) || ShogiCompat.isRepeat(te)) {
            Chuying.LOGGER.info("[chuying] shogi skip: turn={} mate={} limit={} repeat={}",
                    ShogiCompat.isPlayerTurn(te), ShogiCompat.isCheckmate(te),
                    ShogiCompat.isMoveNumberLimit(te), ShogiCompat.isRepeat(te));
            return;
        }
        String sfen = ShogiCompat.sfenOf(te);
        if (sfen == null || sfen.equals(ProxyPlayState.lastFen)) {
            return;
        }
        claimPosition(sfen);
        Chuying.LOGGER.info("[chuying] shogi trigger sfen={} center={} facing={}", sfen, center, facing);
        CompletableFuture.runAsync(() -> {
            try {
                String usi = ShogiCompat.think(sfen);
                Chuying.LOGGER.info("[chuying] shogi bestmove={}", usi);
                if (usi == null || usi.isEmpty()) {
                    mc.execute(() -> noticeNoEngine("message.chuying.no_shogi_engine"));
                    return;
                }
                mc.execute(() -> scheduleShogiMove(center, facing, te, usi));
            } catch (Throwable t) {
                Chuying.LOGGER.error("[chuying] shogi 代打异常", t);
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    /** 将棋"选子→落子"两步模拟点击：USI 走法（棋盘走子 "7g7f"/"7g7f+" 或打驹 "P*5e"）→ 两个格点 */
    private static void scheduleShogiMove(BlockPos center, Direction facing, Object te, String usi) {
        boolean promote = ShogiCompat.usiPromotes(usi);
        int fromPoint;
        int toPoint;
        if (usi.length() >= 4 && usi.charAt(1) == '*') {
            // 打驹：第一下点手驹槽位，第二下点落点
            fromPoint = ShogiCompat.dropPoint(te, usi.charAt(0));
            toPoint = ShogiCompat.usiSquareToPoint(usi.substring(2, 4));
        } else {
            fromPoint = ShogiCompat.usiSquareToPoint(usi.substring(0, 2));
            toPoint = ShogiCompat.usiSquareToPoint(usi.substring(2, 4));
        }
        if (fromPoint < 0 || toPoint < 0) {
            Chuying.LOGGER.warn("[chuying] shogi 无法换算格点：usi={} from={} to={}", usi, fromPoint, toPoint);
            return;
        }
        ShogiCompat.setPendingPromote(promote);
        Chuying.LOGGER.info("[chuying] shogi move usi={} fromPoint={} toPoint={} promote={}",
                usi, fromPoint, toPoint, promote);
        PENDING_CLICKS.add(new PendingClick(ShogiBoardClicker.shogiHit(center, facing, fromPoint), 0));
        PENDING_CLICKS.add(new PendingClick(ShogiBoardClicker.shogiHit(center, facing, toPoint), CHESS_STEP_DELAY_TICKS));
    }

    private static void tryCChess(Minecraft mc, BlockPos center, Direction facing, TileEntityCChess c) {
        // 对局被重置/换新（回合计数回退）时清除局面去重，无需按 K 重启
        int counter = c.getChessCounter();
        if (counter < ProxyPlayState.lastCChessCounter) {
            ProxyPlayState.lastFen = "";
        }
        ProxyPlayState.lastCChessCounter = counter;

        // PVP：棋圣对局只在轮到本客户端那一方时动手（象棋用 isPlayerTurn 表示 P1 回合）
        PvpDecision pvp = pvpDecision(mc, c, c.isPlayerTurn());
        if (pvp == PvpDecision.SKIP) {
            return;
        }
        boolean myTurn = pvp == PvpDecision.MY_TURN || c.isPlayerTurn();
        if (!myTurn || c.isCheckmate() || c.isMoveNumberLimit() || c.isRepeat()) {
            Chuying.LOGGER.info("[chuying] cchess skip: turn={} mate={} limit={} repeat={}",
                    c.isPlayerTurn(), c.isCheckmate(), c.isMoveNumberLimit(), c.isRepeat());
            return;
        }
        String fen = c.getChessData().toFen();
        if (fen.equals(ProxyPlayState.lastFen)) {
            return;
        }
        NativeUciEngine engine = EngineManager.cchess();
        if (engine == null) {
            Chuying.LOGGER.warn("[chuying] cchess engine not available");
            noticeNoEngine("message.chuying.no_cchess_engine");
            return;
        }
        claimPosition(fen);
        // PVP 下引擎拿到的是「轮走方」的局面（FEN 走子方与棋圣回合一致），故 bestmove 即为该方着法
        final boolean sneak = pvp == PvpDecision.MY_TURN;
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        Chuying.LOGGER.info("[chuying] cchess trigger fen={} center={} facing={} pvp={}", fen, center, facing, sneak);
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
                mc.execute(() -> scheduleChessMove(center, facing, fromSq, toSq, true, sneak));
            } catch (Throwable t) {
                Chuying.LOGGER.error("[chuying] cchess 代打异常", t);
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

        // PVP：棋圣对局只在轮到本客户端那一方时动手（国象用 isPlayerTurn 表示 P1 回合）
        PvpDecision pvp = pvpDecision(mc, w, w.isPlayerTurn());
        if (pvp == PvpDecision.SKIP) {
            return;
        }
        boolean myTurn = pvp == PvpDecision.MY_TURN || w.isPlayerTurn();
        if (!myTurn || w.isCheckmate() || w.isMoveNumberLimit() || w.isRepeat()) {
            Chuying.LOGGER.info("[chuying] wchess skip: turn={} mate={} limit={} repeat={}",
                    w.isPlayerTurn(), w.isCheckmate(), w.isMoveNumberLimit(), w.isRepeat());
            return;
        }
        String fen = w.getChessData().toFen();
        if (fen.equals(ProxyPlayState.lastFen)) {
            return;
        }
        NativeUciEngine engine = EngineManager.wchess();
        if (engine == null) {
            Chuying.LOGGER.warn("[chuying] wchess engine not available");
            noticeNoEngine("message.chuying.no_wchess_engine");
            return;
        }
        claimPosition(fen);
        // PVP 下引擎拿到的是「轮走方」的局面（FEN 走子方与棋圣回合一致），故 bestmove 即为该方着法
        final boolean sneak = pvp == PvpDecision.MY_TURN;
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        Chuying.LOGGER.info("[chuying] wchess trigger fen={} center={} facing={} pvp={}", fen, center, facing, sneak);
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
                mc.execute(() -> scheduleChessMove(center, facing, fromSq, toSq, false, sneak));
            } catch (Throwable t) {
                Chuying.LOGGER.error("[chuying] wchess 代打异常", t);
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

        // PVP：棋圣五子棋用 ChessPvpTurn 判回合（P1 黑 / P2 白），不用 isPlayerTurn
        PvpDecision pvp = pvpDecisionGomoku(mc, g);
        if (pvp == PvpDecision.SKIP) {
            return;
        }
        boolean myTurn = pvp == PvpDecision.MY_TURN || g.isPlayerTurn();
        if (!myTurn || g.getStatue() != Statue.IN_PROGRESS) {
            return;
        }
        byte[][] board = g.getChessData();
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
        NativeGomokuEngine engine = EngineManager.gomoku();
        if (engine == null) {
            noticeNoEngine("message.chuying.no_gomoku_engine");
            return;
        }
        claimPosition(fp);
        final boolean sneak = pvp == PvpDecision.MY_TURN;
        int thinkMs = Config.THINK_TIME.get() * Config.STRENGTH.get().multiplier;
        CompletableFuture.runAsync(() -> {
            try {
                int[] xy = engine.bestMove(board, thinkMs);
                if (xy == null) {
                    return;
                }
                int x = xy[0];
                int y = xy[1];
                mc.execute(() -> PENDING_CLICKS.add(
                        new PendingClick(BoardClicker.gomokuHit(center, x, y), 0, sneak)));
            } catch (Throwable t) {
                Chuying.LOGGER.error("[chuying] gomoku 代打异常", t);
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    /**
     * 围棋（TouhouGO 车万女仆·围棋棋盘）代打。
     * <p>
     * <b>不碰 TouhouGO 的自定义协议</b>：落子依旧走原版右键模拟（{@link BoardClicker}），
     * 女仆应手仍由模组自己的 {@code GoSyncPayload -> GoAI -> GoMovePayload} 流程计算；
     * 本 mod 既不发送也不拦截它的 {@code go_to_client} / {@code go_to_server} 通道，
     * 服务器也不需要安装本 mod。
     * <p>
     * 与其它棋种不同的是「整盘喂局面」：每手都把当前棋面 + 劫点注入引擎再 genmove，
     * 所以不需要跟手、也不会因中途开关而算错局面。
     */
    private static void tryGo(Minecraft mc, BlockPos center, BlockEntity te) {
        // 对局被重置/换新（手数回退）时清除局面去重，无需按 K 重启
        int counter = GoCompat.moveCounter(te);
        if (counter >= 0 && counter < ProxyPlayState.lastGoCounter) {
            ProxyPlayState.lastFen = "";
        }
        if (counter >= 0) {
            ProxyPlayState.lastGoCounter = counter;
        }

        if (!GoCompat.isPlayerTurn(te) || GoCompat.statue(te) != Statue.IN_PROGRESS) {
            return;
        }
        byte[][] board = GoCompat.board(te);
        if (board == null) {
            return;
        }
        int koX = GoCompat.koX(te);
        int koY = GoCompat.koY(te);
        // 指纹：手数 + 最近一手 + 劫点（劫点变化也必须重新算）
        String fp = "go" + counter + ":" + GoCompat.lastX(te) + "," + GoCompat.lastY(te) + ":" + koX + "," + koY;
        if (fp.equals(ProxyPlayState.lastFen)) {
            return;
        }
        NativeGoEngine engine = EngineManager.go();
        if (engine == null) {
            Chuying.LOGGER.warn("[chuying] go engine not available");
            noticeNoEngine("message.chuying.no_go_engine");
            return;
        }
        claimPosition(fp);
        int level = NativeGoEngine.levelFor(Config.STRENGTH.get().multiplier);
        Chuying.LOGGER.info("[chuying] go trigger counter={} ko=({},{}) level={} last=({},{})",
                counter, koX, koY, level, GoCompat.lastX(te), GoCompat.lastY(te));
        CompletableFuture.runAsync(() -> {
            try {
                int packed = engine.bestMove(board, koX, koY, level);
                if (packed == NativeGoEngine.ERROR) {
                    Chuying.LOGGER.warn("[chuying] go 引擎未给出着法");
                    return;
                }
                mc.execute(() -> scheduleGoMove(center, packed, koX, koY, board));
            } catch (Throwable t) {
                Chuying.LOGGER.error("[chuying] go 代打异常", t);
            } finally {
                ProxyPlayState.busy = false;
            }
        }, Util.backgroundExecutor());
    }

    /** 围棋落子：{@link NativeGoEngine#PASS} 表示停一手（潜行 + 空手点击棋盘）。 */
    private static void scheduleGoMove(BlockPos center, int packed, int koX, int koY, byte[][] board) {
        Minecraft mc = Minecraft.getInstance();
        if (packed == NativeGoEngine.PASS) {
            Chuying.LOGGER.info("[chuying] go pass");
            // 停一手：服务端判定的是 player.isShiftKeyDown()，必须先发潜行包再点棋盘
            // （复用 PVP 代打那套潜行会话）。点 (7,7) 落在棋盘中央，不会误触"棋子盒重置"区。
            PENDING_CLICKS.add(new PendingClick(BoardClicker.goHit(center, 7, 7), 0, true));
            return;
        }
        int x = packed / 100;
        int y = packed % 100;
        if (x < 0 || y < 0 || x >= GoCompat.SIZE || y >= GoCompat.SIZE || board[x][y] != 0) {
            Chuying.LOGGER.warn("[chuying] go 非法着法 ({},{}), 跳过", x, y);
            return;
        }
        if (x == koX && y == koY) {
            // 引擎踩劫点：服务端（GoRules）会拒绝这一手，跳过等超时重算
            Chuying.LOGGER.warn("[chuying] go 引擎给出劫点 ({},{}), 跳过", x, y);
            return;
        }
        if (mc.player != null && mc.player.isShiftKeyDown()) {
            // 玩家真的按着潜行：服务端会把这次点击当成"停一手"，先不落子，等玩家松手
            Chuying.LOGGER.info("[chuying] go 玩家正按住潜行，暂不落子（避免被当成停一手）");
            ProxyPlayState.lastFen = "";
            return;
        }
        Chuying.LOGGER.info("[chuying] go move ({},{})", x, y);
        PENDING_CLICKS.add(new PendingClick(BoardClicker.goHit(center, x, y), 0, false));
    }

    /** 象棋"选子→落子"两步模拟点击，先点起点格，间隔数 tick 再点终点格；sneak 为 PVP 潜行标记 */
    private static void scheduleChessMove(BlockPos center, Direction facing, int fromSq, int toSq, boolean cchess, boolean sneak) {
        BlockHitResult fromHit = chessHit(center, facing, fromSq, cchess);
        BlockHitResult toHit = chessHit(center, facing, toSq, cchess);
        Chuying.LOGGER.info("[chuying] {} move fromSq={} toSq={} fromHit={} toHit={} sneak={}",
                cchess ? "cchess" : "wchess", fromSq, toSq, fromHit.getLocation(), toHit.getLocation(), sneak);
        PENDING_CLICKS.add(new PendingClick(fromHit, 0, sneak));
        PENDING_CLICKS.add(new PendingClick(toHit, CHESS_STEP_DELAY_TICKS, sneak));
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
