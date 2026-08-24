package dev.zymekoh.herzium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FramerateLimiter.class, priority = 10000)
abstract class FramerateLimiterMixin {
    /**
     * Bypasses Vanilla pacing only while Minecraft is active. Inactive,
     * minimized, menu, and AFK policies remain entirely owned by Vanilla.
     */
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), cancellable = true)
    private static void herzium$skipFramePacing(int framerateLimit, CallbackInfo ci) {
        if (Minecraft.getInstance().isWindowActive()) {
            ci.cancel();
        }
    }
}
