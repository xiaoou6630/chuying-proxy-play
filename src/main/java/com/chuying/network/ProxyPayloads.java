package com.chuying.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 本 mod 的网络 payload 注册。
 */
public final class ProxyPayloads {
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(CChessProxyMovePayload.TYPE, CChessProxyMovePayload.STREAM_CODEC, ProxyServerHandlers::handleCChess);
        registrar.playToServer(WChessProxyMovePayload.TYPE, WChessProxyMovePayload.STREAM_CODEC, ProxyServerHandlers::handleWChess);
        registrar.playToServer(GomokuProxyMovePayload.TYPE, GomokuProxyMovePayload.STREAM_CODEC, ProxyServerHandlers::handleGomoku);
    }
}
