package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.performance.InactiveFpsLimiter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps inactive-window pacing independent from other configurable limiters. */
@Mixin(value = Minecraft.class, priority = 10000)
abstract class InactiveWindowFpsMixin {
    @Inject(method = "runTick", at = @At("TAIL"))
    private void herzium$protectInactiveFramerate(boolean advanceGameTime, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        InactiveFpsLimiter.limit(minecraft.isWindowActive());
    }
}
