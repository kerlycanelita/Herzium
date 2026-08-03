package dev.zymekoh.herzium.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Forces the immediate/non-VSync present-mode branch introduced in 26.2. */
@Mixin(value = Minecraft.class, priority = 10000)
abstract class WindowMixin {
    @ModifyArg(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuSurface$PresentMode;getSupportedVsyncMode(Ljava/util/Collection;Z)Lcom/mojang/blaze3d/systems/GpuSurface$PresentMode;"),
            index = 1)
    private boolean herzium$forceNonVsyncPresentMode(boolean requestedVsync) {
        return false;
    }
}
