package dev.zymekoh.herzium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Smooths the HUD toward the same 0.5 partial-tick sample used by vanilla's
 * attack calculation. The displayed value is never allowed to run ahead of
 * that gameplay sample.
 */
@Mixin(value = Gui.class, priority = 2000)
abstract class GuiMixin {
    @Redirect(
            method = "extractCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"),
            require = 1)
    private float herzium$smoothCrosshairAttackStrength(LocalPlayer player, float ignoredPartialTick) {
        return player.getAttackStrengthScale(herzium$safePartialTick());
    }

    @Redirect(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"),
            require = 1)
    private float herzium$smoothHotbarAttackStrength(LocalPlayer player, float ignoredPartialTick) {
        return player.getAttackStrengthScale(herzium$safePartialTick());
    }

    @Unique
    private static float herzium$safePartialTick() {
        float renderPartialTick =
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return Math.max(0.0F, Math.min(0.5F, renderPartialTick));
    }
}
