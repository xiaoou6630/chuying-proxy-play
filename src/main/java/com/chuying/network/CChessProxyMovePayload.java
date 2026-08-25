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
 * 客户端 -> 服务端：让服务端替玩家在中国象棋棋盘上走一步。
 * 服务端会复用 TLM 的走子校验逻辑，走完照常把局面发回客户端触发女仆应招。
 */
public record CChessProxyMovePayload(BlockPos pos, int move) implements CustomPacketPayload {
    public static final Type<CChessProxyMovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Chuying.MODID, "cchess_proxy_move"));
    public static final StreamCodec<ByteBuf, CChessProxyMovePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CChessProxyMovePayload::pos,
            ByteBufCodecs.VAR_INT, CChessProxyMovePayload::move,
            CChessProxyMovePayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
