package dev.zymekoh.herzium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents the 1.21.x render loop from entering its limiter; inactive pacing is independent. */
@Mixin(value = Minecraft.class, priority = 10000)
abstract class FramerateLimiterMixin {
    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void herzium$skipVanillaFramePacing(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Options.UNLIMITED_FRAMERATE_CUTOFF);
    }
}
