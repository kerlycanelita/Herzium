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
 * <p>Turning {@code instantEquip} off returns first-person hand rendering to
 * plain Vanilla, which also means the hand stops previewing the hotbar slot
 * ahead of the next tick: that preview is applied by writing the visible item
 * here, so it cannot survive the dip being restored.</p>
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
