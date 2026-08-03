package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = Window.class, priority = 10000)
abstract class WindowMixin {
    @ModifyVariable(method = "updateVsync", at = @At("HEAD"), argsOnly = true)
    private boolean herzium$forceVsyncOff(boolean requestedVsync) {
        return false;
    }
}
