package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.zymekoh.herzium.performance.InactiveFpsLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FramerateLimitTracker.class, priority = 10000)
abstract class FramerateLimitTrackerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void herzium$useUnlimitedFramerate(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(this.minecraft.isWindowActive()
                ? Options.UNLIMITED_FRAMERATE_CUTOFF
                : InactiveFpsLimiter.INACTIVE_FPS);
    }

    @Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
    private void herzium$disableThrottleReasons(
            CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
        cir.setReturnValue(this.minecraft.isWindowActive()
                ? FramerateLimitTracker.FramerateThrottleReason.NONE
                : FramerateLimitTracker.FramerateThrottleReason.WINDOW_ICONIFIED);
    }

    @Inject(method = "isHeavilyThrottled", at = @At("HEAD"), cancellable = true)
    private void herzium$neverReportHeavyThrottling(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(!this.minecraft.isWindowActive());
    }
}
