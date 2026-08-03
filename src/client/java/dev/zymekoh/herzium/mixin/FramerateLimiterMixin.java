package dev.zymekoh.herzium.mixin;

import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FramerateLimiter.class, priority = 10000)
abstract class FramerateLimiterMixin {
    /**
     * Final safety net for every vanilla call site that attempts to sleep or
     * spin-wait until a target frame time.
     */
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void herzium$skipFramePacing(int framerateLimit, CallbackInfo ci) {
        ci.cancel();
    }
}
