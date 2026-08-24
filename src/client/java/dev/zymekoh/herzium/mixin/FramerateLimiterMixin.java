package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Second layer of the same policy as {@code FramerateLimitTrackerMixin}.
 *
 * <p>Vanilla only calls this when the resolved limit is below the unlimited
 * cutoff, so with the tracker already reporting "unlimited" during play this
 * rarely fires. It stays because another mod can call {@code limitDisplayFPS}
 * directly, and because a second layer that disagrees with the first would be
 * worse than none. The condition is therefore identical: bypass pacing only
 * while the window has focus <em>and</em> vanilla is not throttling for a
 * reason of its own -- minimized, AFK, or a menu with no level behind it.</p>
 */
@Mixin(value = FramerateLimiter.class, priority = 10000)
abstract class FramerateLimiterMixin {
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void herzium$skipFramePacing(int framerateLimit, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isWindowActive()
                && minecraft.getFramerateLimitTracker().getThrottleReason()
                        == FramerateLimitTracker.FramerateThrottleReason.NONE) {
            ci.cancel();
        }
    }
}
