package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zymekoh.herzium.input.ImmediateHotbarVisualState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Removes the visible one-tick item replacement delay on Minecraft 1.21-1.21.8. */
@Mixin(value = ItemInHandRenderer.class, priority = 2000)
abstract class ItemInHandRendererMixin {
    @Unique
    private static final int HERZIUM_SELECTION_GUARD_TICKS = 40;

    @Unique
    private long herzium$seenHotbarRevision;

    @Unique
    private long herzium$renderedHotbarRevision;

    @Unique
    private boolean herzium$suppressSelectionDip;

    @Unique
    private boolean herzium$sawSelectionCooldownReset;

    @Unique
    private int herzium$selectionResetGraceTicks;

    @Unique
    private int herzium$selectionGuardTicks;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

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
        this.herzium$selectionGuardTicks = HERZIUM_SELECTION_GUARD_TICKS;
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

    /** Keeps Vanilla from redrawing its equip dip after the immediate frame. */
    @Inject(method = "tick", at = @At("RETURN"))
    private void herzium$finishImmediateSelectionVisual(CallbackInfo ci) {
        if (!this.herzium$suppressSelectionDip) {
            return;
        }

        LocalPlayer player = this.minecraft.player;
        if (player != null && !player.isHandsBusy()) {
            this.mainHandItem = player.getMainHandItem();
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
        }

        if (--this.herzium$selectionGuardTicks <= 0) {
            this.herzium$suppressSelectionDip = false;
        }
    }

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void herzium$showChangedItemsOnNextFrame(
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            LocalPlayer player,
            int packedLight,
            CallbackInfo ci) {
        long revision = ImmediateHotbarVisualState.revision();
        if (revision != this.herzium$renderedHotbarRevision) {
            this.herzium$renderedHotbarRevision = revision;

            ItemStack currentMainHand = player.getMainHandItem();
            if (this.mainHandItem != currentMainHand) {
                this.mainHandItem = currentMainHand;
                this.mainHandHeight = 1.0F;
                this.oMainHandHeight = 1.0F;
            }
        }
    }

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = 0.4F), require = 0)
    private float herzium$fasterPositiveEquipStep(float originalStep) {
        return 0.8F;
    }

    @ModifyConstant(method = "tick", constant = @Constant(floatValue = -0.4F), require = 0)
    private float herzium$fasterNegativeEquipStep(float originalStep) {
        return -0.8F;
    }
}
