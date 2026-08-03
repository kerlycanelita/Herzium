package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.21.5+ tracker policy; the independent tail limiter owns inactive pacing. */
@Mixin(value = FramerateLimitTracker.class, priority = 10000)
abstract class FramerateLimitTrackerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void herzium$skipVanillaFramePacing(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Options.UNLIMITED_FRAMERATE_CUTOFF);
    }

    @Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
    private void herzium$reportWindowState(
            CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
        cir.setReturnValue(this.minecraft.isWindowActive()
                ? FramerateLimitTracker.FramerateThrottleReason.NONE
                : FramerateLimitTracker.FramerateThrottleReason.WINDOW_ICONIFIED);
    }

    @Inject(method = "isHeavilyThrottled", at = @At("HEAD"), cancellable = true)
    private void herzium$reportHeavyThrottle(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(!this.minecraft.isWindowActive());
    }
}
