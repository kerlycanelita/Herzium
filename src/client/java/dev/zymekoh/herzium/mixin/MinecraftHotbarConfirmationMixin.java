package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes the final committed slot after the complete client tick. */
@Mixin(value = Minecraft.class, priority = 100)
abstract class MinecraftHotbarConfirmationMixin {
    @Inject(method = "tick", at = @At("RETURN"))
    private void herzium$confirmFinalVanillaHotbarSelection(CallbackInfo ci) {
        ImmediateHotbarInput.confirmAfterClientTick((Minecraft) (Object) this);
    }
}
