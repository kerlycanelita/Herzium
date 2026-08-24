package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.render.CombatItemClassifier;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renderer-only instant ordinary items with Vanilla combat equip transitions. */
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
            MultiBufferSource.BufferSource buffers,
            LocalPlayer player,
            int packedLight,
            CallbackInfo ci) {
        ItemStack visualMainHandItem = ImmediateHotbarInput.visualMainHandItem(player);
        if (CombatItemClassifier.preservesVanillaEquipTransition(visualMainHandItem)) {
            CoreDiagnostics.recordCombatEquipFramePreserved();
        } else {
            this.mainHandItem = visualMainHandItem;
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
            CoreDiagnostics.recordInstantEquipFrame();
        }

        ItemStack visualOffHandItem = player.getOffhandItem();
        if (CombatItemClassifier.preservesVanillaEquipTransition(visualOffHandItem)) {
            CoreDiagnostics.recordCombatEquipFramePreserved();
        } else {
            this.offHandItem = visualOffHandItem;
            this.offHandHeight = 1.0F;
            this.oOffHandHeight = 1.0F;
            CoreDiagnostics.recordInstantEquipFrame();
        }
        CoreDiagnostics.recordHandRenderHook();
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
        if (!CombatItemClassifier.preservesVanillaEquipTransition(currentItem)) {
            cir.setReturnValue(true);
        }
    }
}
