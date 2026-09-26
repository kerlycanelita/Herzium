package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.render.CombatItemClassifier;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.3 moved the live hand state out of the renderer and into the local player. */
@Mixin(value = FirstPersonHandsAndItems.class, priority = 2000)
abstract class ItemInHandRendererMixin {
    @Shadow private ItemStack mainHandItem;
    @Shadow private ItemStack offHandItem;
    @Shadow private float mainHandHeight;
    @Shadow private float oMainHandHeight;
    @Shadow private float offHandHeight;
    @Shadow private float oOffHandHeight;
    @Unique private boolean herzium$mainHandPlacing;
    @Unique private boolean herzium$offHandPlacing;

    // Vanilla's short dip after an item is used or placed is feedback for the
    // action, not an equip transition: it always plays out untouched.
    @Inject(method = "itemUsed(Lnet/minecraft/world/InteractionHand;)V", at = @At("TAIL"), require = 1)
    private void herzium$keepVanillaPlacementAnimation(InteractionHand hand, CallbackInfo ci) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.herzium$mainHandPlacing = true;
        } else {
            this.herzium$offHandPlacing = true;
        }
    }

    // Synchronize before vanilla extracts both the stack and its resolved model.
    // Changing only the submitted render state would leave the two disagreeing.
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/LocalPlayer;FLnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V",
            at = @At("HEAD"), require = 1)
    private void herzium$synchronizeVisibleHandsWithoutEquipTransition(
            LocalPlayer player, float partialTick,
            FirstPersonHandsAndItemsRenderState renderState, CallbackInfo ci) {
        boolean handsBusy = player.isHandsBusy();
        // A placement animation ends once Vanilla has the hand fully up again.
        this.herzium$mainHandPlacing &= this.mainHandHeight < 1.0F || this.oMainHandHeight < 1.0F;
        this.herzium$offHandPlacing &= this.offHandHeight < 1.0F || this.oOffHandHeight < 1.0F;

        ItemStack visualMainHandItem = ImmediateHotbarInput.visualMainHandItem(player);
        if (!CombatItemClassifier.preservesVanillaEquipTransition(visualMainHandItem)) {
            this.mainHandItem = visualMainHandItem;
            if (!handsBusy && !this.herzium$mainHandPlacing) {
                this.mainHandHeight = 1.0F;
                this.oMainHandHeight = 1.0F;
            }
        }

        ItemStack visualOffHandItem = player.getOffhandItem();
        if (!CombatItemClassifier.preservesVanillaEquipTransition(visualOffHandItem)) {
            this.offHandItem = visualOffHandItem;
            if (!handsBusy && !this.herzium$offHandPlacing) {
                this.offHandHeight = 1.0F;
                this.oOffHandHeight = 1.0F;
            }
        }
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/LocalPlayer;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void herzium$replaceVisibleItemImmediately(
            ItemStack renderedItem, ItemStack currentItem, LocalPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (!CombatItemClassifier.preservesVanillaEquipTransition(currentItem)) {
            cir.setReturnValue(true);
        }
    }
}
