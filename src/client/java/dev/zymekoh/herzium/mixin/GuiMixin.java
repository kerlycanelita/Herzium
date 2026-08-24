package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the attack indicator advance inside the current tick instead of only at
 * tick boundaries.
 *
 * <p>Both hooks are {@link ModifyExpressionValue} rather than {@code @Redirect}.
 * A redirect is exclusive: it claims the call site, so any other HUD mod
 * touching {@code getAttackStrengthScale} at these two points would simply
 * stop working. The neighbouring {@code HotbarVisualMixin} already used the
 * composable form; these two were the outliers.</p>
 */
@Mixin(value = Gui.class, priority = 2000)
abstract class GuiMixin {
    @ModifyExpressionValue(
            method = "extractCrosshair",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"),
            require = 1)
    private float herzium$smoothCrosshairAttackStrength(float vanillaScale) {
        return herzium$smoothedAttackStrength(vanillaScale);
    }

    @ModifyExpressionValue(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F"),
            require = 1)
    private float herzium$smoothHotbarAttackStrength(float vanillaScale) {
        return herzium$smoothedAttackStrength(vanillaScale);
    }

    @Unique
    private static float herzium$smoothedAttackStrength(float vanillaScale) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? vanillaScale : player.getAttackStrengthScale(herzium$safePartialTick());
    }

    /**
     * Deliberately more conservative than vanilla, and deliberately not
     * equivalent to it.
     *
     * <p>Vanilla's HUD asks for the scale at partial tick {@code 0.0F}, so the
     * indicator only moves once per tick. Vanilla's own attack resolution in
     * {@code Player.attack()} samples at {@code 0.5F}. Herzium interpolates the
     * displayed value across that gap and stops there, so the bar can never
     * claim a charge the gameplay sample has not reached.</p>
     *
     * <p>The visible consequence is a real one, not a rounding detail: the
     * indicator advances through the first half of every tick and then sits
     * still until the next one. Uncapping it to {@code 1.0F} would look
     * smoother and would let the bar read full while an attack landing at that
     * instant would still be resolved as partial -- which is the one thing this
     * mod does not do.</p>
     */
    @Unique
    private static float herzium$safePartialTick() {
        float renderPartialTick =
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return Math.max(0.0F, Math.min(0.5F, renderPartialTick));
    }
}
