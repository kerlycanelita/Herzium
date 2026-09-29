package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.herzium.input.HotbarOrderController;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-observable slot preference; Herzium order is the default, Vanilla is one click away. */
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

    // Vanilla resolves every hotbar key of a tick before any click. Under the
    // Herzium order, a key pressed after the click that bounds the pass is left
    // queued, unconsumed, for the next pass, so the click uses the item the
    // player held when pressing it; with split bursts, a click pressed after
    // that key waits with it. Every other mapping, and every other order,
    // passes straight through.
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/KeyMapping;consumeClick()Z"), require = 1)
    private boolean herzium$keepKeysPressedAfterAClickForNextPass(KeyMapping mapping, Operation<Boolean> original) {
        Minecraft minecraft = (Minecraft) (Object) this;
        int slot = HotbarOrderController.hotbarSlotOf(minecraft, mapping);
        if (slot >= 0) {
            if (HotbarOrderController.defersHotbarClick(slot)) return false;
            boolean consumed = original.call(mapping);
            if (consumed) {
                HotbarOrderController.hotbarClickConsumed(slot, ((KeyMappingAccessor) mapping).herzium$getPendingClickCount());
            }
            return consumed;
        }
        int action = HotbarOrderController.actionTypeOf(minecraft, mapping);
        if (action < 0) return original.call(mapping);
        if (!HotbarOrderController.allowsActionClick(action)) return false;
        boolean consumed = original.call(mapping);
        if (consumed) {
            HotbarOrderController.actionClickConsumed(action, ((KeyMappingAccessor) mapping).herzium$getPendingClickCount());
        }
        return consumed;
    }

    // The queue and its one-consumption-per-slot loop are untouched. An
    // alternate policy only prevents a lower-priority consumed slot from
    // replacing the preferred one at this existing selection call site.
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"), require = 1)
    private void herzium$applyPreferredConsumedSlot(Inventory inventory, int slot, Operation<Void> original) {
        if (HotbarOrderController.acceptSelection(slot)) {
            original.call(inventory, slot);
            ImmediateHotbarInput.noteCallSiteSelection(slot);
        }
    }

    // The offhand swap is the only packet handleKeybinds builds itself; Vanilla
    // sends it before telling the server which slot this pass selected.
    @Inject(method = "handleKeybinds", at = @At(value = "NEW",
            target = "net/minecraft/network/protocol/game/ServerboundPlayerActionPacket"), require = 1)
    private void herzium$sendSlotBeforeOffhandSwap(CallbackInfo ci) {
        HotbarOrderController.sendSlotBeforeOffhandAction((Minecraft) (Object) this);
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;drop(Z)Z"), require = 1)
    private void herzium$sendSlotBeforeDrop(CallbackInfo ci) {
        HotbarOrderController.sendSlotBeforeOffhandAction((Minecraft) (Object) this);
    }
}
