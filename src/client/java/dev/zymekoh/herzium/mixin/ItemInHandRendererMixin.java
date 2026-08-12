package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * Keeps both first-person item slots synchronized with the player every frame.
 * Only renderer-owned item references and equip interpolation fields are
 * changed; inventory state, actions, packets and server timing stay Vanilla.
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
    private void herzium$renderCurrentItemsWithoutEquipAnimation(
            float partialTick,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            LocalPlayer player,
            int packedLight,
            CallbackInfo ci) {
        this.mainHandItem = player.getMainHandItem();
        this.offHandItem = player.getOffhandItem();

        // Move both interpolation endpoints together. This removes the
        // hotbar/offhand equip dip without touching swing or use animations.
        this.mainHandHeight = 1.0F;
        this.oMainHandHeight = 1.0F;
        this.offHandHeight = 1.0F;
        this.oOffHandHeight = 1.0F;
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
        cir.setReturnValue(true);
    }
}
