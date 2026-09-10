package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.herzium.input.HotbarOrderController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional server-observable slot preference; Vanilla remains the default. */
@Mixin(value = Minecraft.class, priority = 1100)
abstract class HotbarOrderMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void herzium$captureOrderForPass(CallbackInfo ci) {
        HotbarOrderController.beginPass((Minecraft) (Object) this);
    }

    // In alternative modes, seal the completed slot pass before action/screen
    // callbacks can supply a newer event. Repeated social-key loop iterations
    // are ignored by the controller's once-per-pass guard.
    @Inject(method = "handleKeybinds", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Options;keySocialInteractions:Lnet/minecraft/client/KeyMapping;",
            ordinal = 0), require = 1)
    private void herzium$sealAlternateSlotPass(CallbackInfo ci) {
        HotbarOrderController.captureAlternatePass((Minecraft) (Object) this);
    }

    // The queue and its one-consumption-per-slot loop are untouched. An
    // alternate policy only prevents a lower-priority consumed slot from
    // replacing the preferred one at this existing selection call site.
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"), require = 1)
    private void herzium$applyPreferredConsumedSlot(Inventory inventory, int slot, Operation<Void> original) {
        if (HotbarOrderController.acceptSelection(slot)) original.call(inventory, slot);
    }
}
