package com.chuying.network;

import com.github.tartaricacid.touhoulittlemaid.advancements.maid.TriggerType;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Statue;
import com.github.tartaricacid.touhoulittlemaid.api.game.xqwlight.Position;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import com.github.tartaricacid.touhoulittlemaid.network.message.CChessToClientPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.GomokuClientPackage;
import com.github.tartaricacid.touhoulittlemaid.network.message.WChessToClientPackage;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityCChess;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityGomoku;
import com.github.tartaricacid.touhoulittlemaid.tileentity.TileEntityWChess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端：收到代理走子请求后，复用 TLM 的公开走子逻辑替玩家落子。
 * 走完照常把局面发回玩家客户端，客户端里女仆自带的 AI 会照常应招。
 */
public final class ProxyServerHandlers {
    private ProxyServerHandlers() {
    }

    public static void handleCChess(CChessProxyMovePayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Level level = player.level();
            if (!level.isLoaded(message.pos())) {
                return;
            }
            if (!(level.getBlockEntity(message.pos()) instanceof TileEntityCChess chess)) {
                return;
            }
            // 未轮到玩家 / 对局已结束，忽略
            if (!chess.isPlayerTurn() || chess.isCheckmate() || chess.isMoveNumberLimit() || chess.isRepeat()) {
                return;
            }
            Position chessData = chess.getChessData();
            int move = message.move();
            if (!chessData.legalMove(move)) {
                return;
            }
            boolean notChecked = chessData.makeMove(move);
            if (notChecked) {
                // 吃子则重置自然限着计数器
                if (chessData.captured()) {
                    chessData.setIrrev();
                }
                chess.addChessCounter();
                chess.setSelectChessPoint(Position.DST(move));
                chess.refresh();
                level.playSound(null, message.pos(), InitSounds.GOMOKU.get(), SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
                PacketDistributor.sendToPlayer(player, new CChessToClientPackage(message.pos(), chessData.toFen()));
            }
        });
    }

    public static void handleWChess(WChessProxyMovePayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Level level = player.level();
            if (!level.isLoaded(message.pos())) {
                return;
            }
            if (!(level.getBlockEntity(message.pos()) instanceof TileEntityWChess chess)) {
                return;
            }
            if (!chess.isPlayerTurn() || chess.isCheckmate() || chess.isMoveNumberLimit() || chess.isRepeat()) {
                return;
            }
            com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position chessData = chess.getChessData();
            int move = message.move();
            if (!chessData.legalMove(move)) {
                return;
            }
            boolean notChecked = chessData.makeMove(move);
            if (notChecked) {
                int pcSrc = chessData.squares[com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.SRC(move)];
                // 吃子或动兵则重置自然限着计数器
                if (chessData.captured() || com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.PIECE_TYPE(pcSrc)
                        == com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.PIECE_PAWN) {
                    chessData.setIrrev();
                }
                chess.addChessCounter();
                chess.setSelectChessPoint(com.github.tartaricacid.touhoulittlemaid.api.game.chess.Position.DST(move));
                chess.refresh();
                level.playSound(null, message.pos(), InitSounds.GOMOKU.get(), SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
                PacketDistributor.sendToPlayer(player, new WChessToClientPackage(message.pos(), chessData.toFen()));
            }
        });
    }

    public static void handleGomoku(GomokuProxyMovePayload message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            Level level = player.level();
            if (!level.isLoaded(message.pos())) {
                return;
            }
            if (!(level.getBlockEntity(message.pos()) instanceof TileEntityGomoku gomoku)) {
                return;
            }
            Statue statue = gomoku.getStatue();
            if (statue != Statue.IN_PROGRESS || !gomoku.isPlayerTurn()) {
                return;
            }
            byte[][] chessData = gomoku.getChessData();
            Point playerPoint = message.point();
            if (playerPoint.x < 0 || playerPoint.x >= chessData.length || playerPoint.y < 0 || playerPoint.y >= chessData.length) {
                return;
            }
            if (chessData[playerPoint.x][playerPoint.y] != Point.EMPTY) {
                return;
            }
            gomoku.setChessData(playerPoint.x, playerPoint.y, Point.BLACK);
            statue = MaidGomokuAI.getStatue(chessData, playerPoint);
            if (level instanceof ServerLevel serverLevel && serverLevel.getEntity(gomoku.getSitId()) instanceof EntitySit sit
                    && sit.getFirstPassenger() instanceof EntityMaid maid && maid.isOwnedBy(player)) {
                if (statue == Statue.WIN) {
                    // 玩家获胜：好感度 + 胜场记录（与 TLM 原逻辑一致）
                    maid.getFavorabilityManager().apply(Type.GOMOKU_WIN);
                    maid.getGameRecordManager().markStatue(false);
                    int rankBefore = MaidGomokuAI.getRank(maid);
                    maid.getGameRecordManager().increaseGomokuWinCount();
                    int rankAfter = MaidGomokuAI.getRank(maid);
                    if (rankBefore < rankAfter) {
                        // 升段（粒子动画从简）
                    }
                    InitTrigger.MAID_EVENT.get().trigger(player, TriggerType.WIN_GOMOKU);
                }
            }
            gomoku.setStatue(statue);
            level.playSound(null, message.pos(), InitSounds.GOMOKU.get(), SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
            if (gomoku.getStatue() == Statue.IN_PROGRESS) {
                gomoku.setPlayerTurn(false);
                int winCount = 0;
                if (level instanceof ServerLevel serverLevel && serverLevel.getEntity(gomoku.getSitId()) instanceof EntitySit sit
                        && sit.getFirstPassenger() instanceof EntityMaid maid) {
                    winCount = maid.getGameRecordManager().getGomokuWinCount();
                }
                PacketDistributor.sendToPlayer(player, new GomokuClientPackage(message.pos(), chessData, playerPoint, winCount));
            }
            gomoku.refresh();
        });
    }
}
