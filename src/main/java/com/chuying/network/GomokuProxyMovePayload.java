package com.chuying.network;

import com.chuying.Chuying;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.Point;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 -> 服务端：让服务端替玩家在五子棋棋盘上落一子。
 */
public record GomokuProxyMovePayload(BlockPos pos, Point point) implements CustomPacketPayload {
    public static final Type<GomokuProxyMovePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Chuying.MODID, "gomoku_proxy_move"));
    public static final StreamCodec<ByteBuf, GomokuProxyMovePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, GomokuProxyMovePayload::pos,
            Point.POINT_STREAM_CODEC, GomokuProxyMovePayload::point,
            GomokuProxyMovePayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
