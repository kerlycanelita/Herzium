package dev.zymekoh.herzium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bypasses the 1.21.x limiter only while active; Vanilla owns inactive/AFK pacing. */
@Mixin(value = Minecraft.class, priority = 10000)
abstract class FramerateLimiterMixin {
    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void herzium$skipVanillaFramePacing(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (minecraft.isWindowActive()) {
            cir.setReturnValue(Options.UNLIMITED_FRAMERATE_CUTOFF);
        }
    }
}
