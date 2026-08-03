package dev.zymekoh.herzium.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// GlDevice is package-private in 26.1.2, so Mixin's string target form is required.
@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice", priority = 10000)
abstract class GlDeviceMixin {
    @ModifyVariable(method = "setVsync", at = @At("HEAD"), argsOnly = true)
    private boolean herzium$forceSwapIntervalZero(boolean requestedVsync) {
        return false;
    }
}
