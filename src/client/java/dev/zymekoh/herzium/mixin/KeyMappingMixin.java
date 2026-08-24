package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cooperative observer placed after Vanilla records a logical click. It works
 * for keyboard, scancode, mouse-button, and mod-remapped input without owning
 * or cancelling any platform callback.
 */
@Mixin(value = KeyMapping.class, priority = 100)
abstract class KeyMappingMixin {
    @Inject(method = "click", at = @At("TAIL"))
    private static void herzium$observeLogicalHotbarInput(
            InputConstants.Key logicalKey,
            CallbackInfo ci) {
        ImmediateHotbarInput.previewLogicalKey(logicalKey);
    }
}
