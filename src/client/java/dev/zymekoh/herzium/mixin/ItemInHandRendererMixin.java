package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zymekoh.herzium.input.ImmediateHotbarVisualState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the tick-sized latency before a newly selected item becomes visible.
 * Only client render state is changed; use, attack and server cooldown logic are
 * left untouched.
 */
@Mixin(value = ItemInHandRenderer.class, priority = 2000)
abstract class ItemInHandRendererMixin {
    @Unique
    private static final float HERZIUM_FAST_EQUIP_STEP = 0.8F;

    @Unique
    private long herzium$seenHotbarRevision;

    @Unique
    private boolean herzium$suppressSelectionDip;

    @Unique
    private boolean herzium$sawSelectionCooldownReset;

    @Unique
    private int herzium$selectionResetGraceTicks;

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

    @Inject(method = "tick", at = @At("HEAD"))
    private void herzium$prepareImmediateSelectionVisual(CallbackInfo ci) {
        long revision = ImmediateHotbarVisualState.revision();
        if (revision == this.herzium$seenHotbarRevision) {
            return;
        }

        this.herzium$seenHotbarRevision = revision;
        this.herzium$suppressSelectionDip = true;
        this.herzium$sawSelectionCooldownReset = false;
        this.herzium$selectionResetGraceTicks = 2;
    }

    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"),
            require = 0)
    private float herzium$keepImmediateSelectionRaised(LocalPlayer player, float partialTick) {
        float vanillaStrength = player.getAttackStrengthScale(partialTick);
        if (!this.herzium$suppressSelectionDip) {
            return vanillaStrength;
        }

        if (vanillaStrength < 0.999F) {
            this.herzium$sawSelectionCooldownReset = true;
            return 1.0F;
        }

        if (this.herzium$sawSelectionCooldownReset) {
            this.herzium$suppressSelectionDip = false;
            return vanillaStrength;
        }

        if (this.herzium$selectionResetGraceTicks-- > 0) {
            return 1.0F;
        }

        this.herzium$suppressSelectionDip = false;
        return vanillaStrength;
    }

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void herzium$showChangedItemsOnNextFrame(
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            LocalPlayer player,
            int packedLight,
            CallbackInfo ci) {
        ItemStack currentMainHand = player.getMainHandItem();
        if (!ItemStack.matches(this.mainHandItem, currentMainHand)) {
            this.mainHandItem = currentMainHand;
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
        }

        ItemStack currentOffHand = player.getOffhandItem();
        if (!ItemStack.matches(this.offHandItem, currentOffHand)) {
            this.offHandItem = currentOffHand;
            this.offHandHeight = 1.0F;
            this.oOffHandHeight = 1.0F;
        }
    }

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void herzium$replaceVisibleItemWithoutDip(
            ItemStack renderedItem,
            ItemStack currentItem,
            CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = 0.4F), require = 4)
    private float herzium$fasterPositiveEquipStep(float originalStep) {
        return HERZIUM_FAST_EQUIP_STEP;
    }

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = -0.4F), require = 2)
    private float herzium$fasterNegativeEquipStep(float originalStep) {
        return -HERZIUM_FAST_EQUIP_STEP;
    }
}
