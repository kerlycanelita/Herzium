package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.render.CombatItemClassifier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the decorative equip dip for ordinary items while preserving the
 * complete Vanilla transition whenever a combat item appears. Inventory state,
 * actions, cooldowns, and packets remain untouched.
 *
 * <p>Hotbar selection is never read ahead here. The current main-hand stack is
 * taken from the player only after Vanilla commits the selected slot, leaving
 * remaps, duplicate bindings and other hotbar mods completely authoritative.</p>
 */
@Mixin(value = ItemInHandRenderer.class, priority = 2000)
abstract class ItemInHandRendererMixin {
    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void herzium$synchronizeVisibleHandsWithoutEquipTransition(
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            LocalPlayer player,
            int packedLight,
            CallbackInfo ci) {
        // Recorded before the flag is consulted: the diagnostics panel reports
        // whether the hook fires, which stays true when the feature is off.
        CoreDiagnostics.recordHandRenderHook();
        if (!HerziumConfig.get().instantEquip()) {
            return;
        }

        // ItemInHandRenderer.tick() lowers both hands while the player is busy
        // with an item -- eating, drinking, using a spyglass. That dip is not
        // the equip transition this mod removes, and pinning the heights to 1.0
        // every frame would erase it. Only the equip case is overridden.
        boolean handsBusy = player.isHandsBusy();

        // The hand follows the same previewed slot the HUD shows, so a swap
        // reaches both at once instead of the hand trailing by up to a tick.
        ItemStack visualMainHandItem = ImmediateHotbarInput.visualMainHandItem(player);
        if (!CombatItemClassifier.preservesVanillaEquipTransition(visualMainHandItem)) {
            this.mainHandItem = visualMainHandItem;
            if (!handsBusy) {
                this.mainHandHeight = 1.0F;
                this.oMainHandHeight = 1.0F;
            }
        }

        ItemStack visualOffHandItem = player.getOffhandItem();
        if (!CombatItemClassifier.preservesVanillaEquipTransition(visualOffHandItem)) {
            this.offHandItem = visualOffHandItem;
            if (!handsBusy) {
                this.offHandHeight = 1.0F;
                this.oOffHandHeight = 1.0F;
            }
        }
    }

    @Inject(
            method = "shouldInstantlyReplaceVisibleItem",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void herzium$replaceVisibleItemImmediately(
            ItemStack renderedItem,
            ItemStack currentItem,
            CallbackInfoReturnable<Boolean> cir) {
        if (!HerziumConfig.get().instantEquip()) {
            return;
        }

        if (!CombatItemClassifier.preservesVanillaEquipTransition(currentItem)) {
            cir.setReturnValue(true);
        }
    }
}
