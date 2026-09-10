package dev.zymekoh.herzium.debug.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Direct observation of Herzium's public visual-preview lifecycle. */
@Mixin(targets = "dev.zymekoh.herzium.input.ImmediateHotbarInput", priority = 3000, remap = false)
abstract class HerziumImmediateHotbarTraceMixin {
    @Inject(method = "previewLogicalKey", at = @At("HEAD"), remap = false)
    private static void herziumDebug$previewRequested(InputConstants.Key key, CallbackInfo ci) {
        DebugCollector.onHerziumPreviewRequested(key);
    }

    @Inject(method = "previewLogicalKey", at = @At("RETURN"), remap = false)
    private static void herziumDebug$previewRegistered(InputConstants.Key key, CallbackInfo ci) {
        DebugCollector.onHerziumPreviewRegistered();
    }

    @Inject(method = "visualSelectedSlot", at = @At("RETURN"), remap = false)
    private static void herziumDebug$visualSlot(
            Inventory inventory,
            int vanillaSlot,
            CallbackInfoReturnable<Integer> cir) {
        DebugCollector.onHerziumVisualSlot(vanillaSlot, cir.getReturnValueI());
    }

    @Inject(method = "visualMainHandItem", at = @At("RETURN"), remap = false)
    private static void herziumDebug$visualHand(LocalPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        DebugCollector.onHerziumVisualMainHand(player.getMainHandItem(), cir.getReturnValue());
    }

    @Inject(method = "markVanillaHotbarPassCompleted", at = @At("HEAD"), remap = false)
    private static void herziumDebug$hotbarPass(Minecraft minecraft, CallbackInfo ci) {
        DebugCollector.onHerziumHotbarPass();
    }

    @Inject(method = "confirmAfterClientTick", at = @At("HEAD"), remap = false)
    private static void herziumDebug$confirmBegin(Minecraft minecraft, CallbackInfo ci) {
        DebugCollector.onHerziumConfirm(false);
    }

    @Inject(method = "confirmAfterClientTick", at = @At("RETURN"), remap = false)
    private static void herziumDebug$confirmEnd(Minecraft minecraft, CallbackInfo ci) {
        DebugCollector.onHerziumConfirm(true);
    }

    @Inject(method = "observeHudHook", at = @At("HEAD"), remap = false)
    private static void herziumDebug$hudHook(CallbackInfo ci) {
        DebugCollector.onHerziumHudHook();
    }

    @Inject(method = "onVanillaScrollFinished", at = @At("RETURN"), remap = false)
    private static void herziumDebug$scroll(Minecraft minecraft, int before, CallbackInfo ci) {
        int after = minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot();
        DebugCollector.onHerziumScroll(before, after);
    }

    @Inject(method = "resetSession", at = @At("HEAD"), remap = false)
    private static void herziumDebug$reset(CallbackInfo ci) {
        DebugCollector.onHerziumReset();
    }

    @Inject(method = "releaseStalePreview", at = @At("HEAD"), remap = false)
    private static void herziumDebug$stale(Minecraft minecraft, CallbackInfo ci) {
        DebugCollector.onHerziumStaleCheck();
    }
}
