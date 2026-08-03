package dev.zymekoh.herzium.mixin.compat;

import dev.zymekoh.herzium.Herzium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Exordium intentionally redraws the HUD at a configurable lower rate. When
 * both mods are present, disable that optional buffer so the vanilla HUD path
 * (hotbar, player list/TAB, crosshair, status effects, etc.) runs every frame.
 */
@Pseudo
@Mixin(targets = "dev.tr7zw.exordium.components.BufferInstance", priority = 10000, remap = false)
abstract class ExordiumBufferInstanceMixin {
    private static boolean herzium$loggedCompatibility;

    @Inject(method = "enabled()Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void herzium$disableExordiumHudBuffer(CallbackInfoReturnable<Boolean> cir) {
        if (!herzium$loggedCompatibility) {
            herzium$loggedCompatibility = true;
            Herzium.LOGGER.info("Exordium HUD frame limiter bypassed successfully.");
        }

        cir.setReturnValue(false);
    }
}
