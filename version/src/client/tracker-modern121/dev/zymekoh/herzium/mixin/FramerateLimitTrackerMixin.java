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

/** 1.21.5+ tracker policy; Vanilla retains all inactive/AFK pacing. */
@Mixin(value = FramerateLimitTracker.class, priority = 10000)
abstract class FramerateLimitTrackerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void herzium$skipVanillaFramePacing(CallbackInfoReturnable<Integer> cir) {
        if (this.minecraft.isWindowActive()) {
            cir.setReturnValue(Options.UNLIMITED_FRAMERATE_CUTOFF);
        }
    }
}
