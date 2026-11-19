package com.chuying.mixin;

import com.chuying.Config;
import com.github.tartaricacid.touhoulittlemaid.api.game.gomoku.AIService;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 纯客户端调试：开启 Config 的 debugForceMaxMaid 后，五子棋女仆 AI 强制用最高难度 HELL。
 * <p>
 * 原理：TLM 在客户端 {@code GomokuToClientMessage.onHandle} 里用
 * {@code MaidGomokuAI.getService(胜场数)} 选难度，这里在方法开头拦截，开关开启时直接返回 HELL。
 */
@Mixin(MaidGomokuAI.class)
public class MaidGomokuAIMixin {
    @Inject(method = "getService", at = @At("HEAD"), cancellable = true)
    private static void chuying$forceHell(int winCount, CallbackInfoReturnable<AIService> cir) {
        if (Config.DEBUG_FORCE_MAX_MAID.get()) {
            cir.setReturnValue(MaidGomokuAI.HELL);
        }
    }
}
