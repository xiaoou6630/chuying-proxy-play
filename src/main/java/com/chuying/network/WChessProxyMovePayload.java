package com.chuying.network;

import com.chuying.Chuying;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 -> 服务端：让服务端替玩家在国际象棋棋盘上走一步。
 */
public record WChessProxyMovePayload(BlockPos pos, int move) implements CustomPacketPayload {
    public static final Type<WChessProxyMovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Chuying.MODID, "wchess_proxy_move"));
    public static final StreamCodec<ByteBuf, WChessProxyMovePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, WChessProxyMovePayload::pos,
            ByteBufCodecs.VAR_INT, WChessProxyMovePayload::move,
            WChessProxyMovePayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
