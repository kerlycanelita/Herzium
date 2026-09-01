package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.zymekoh.herzium.input.ImmediateActionFeedback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the attack indicator advance inside the current tick instead of only at
 * tick boundaries.
 *
 * <p>Both hooks are {@link ModifyExpressionValue} rather than {@code @Redirect}.
 * A redirect is exclusive: it claims the call site, so any other HUD mod
 * touching {@code getAttackStrengthScale} at these two points would simply
 * stop working. The composable form keeps those call sites shareable.</p>
 */
@Mixin(value = Gui.class, priority = 2000)
abstract class GuiMixin {
    /**
     * A tiny render-only acknowledgement of the logical Attack/Use press.
     * Left/top marks Attack; right/bottom marks Use. It never represents a
     * successful hit or placement and is never consulted by gameplay code.
     */
    @Inject(method = "extractCrosshair", at = @At("TAIL"), require = 1)
    private void herzium$renderImmediateActionFeedback(
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci) {
        ImmediateActionFeedback.FeedbackSample feedback = ImmediateActionFeedback.sample();
        if (!feedback.visible()) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int centerY = graphics.guiHeight() / 2;
        if (feedback.attack() > 0.0F) {
            int attackColor = herzium$feedbackColor(feedback.attack(), 0xC46CFF);
            graphics.fill(centerX - 10, centerY - 1, centerX - 7, centerY + 1, attackColor);
            graphics.fill(centerX - 1, centerY - 10, centerX + 1, centerY - 7, attackColor);
        }
        if (feedback.use() > 0.0F) {
            int useColor = herzium$feedbackColor(feedback.use(), 0x8A46FF);
            graphics.fill(centerX + 7, centerY - 1, centerX + 10, centerY + 1, useColor);
            graphics.fill(centerX - 1, centerY + 7, centerX + 1, centerY + 10, useColor);
        }
    }

    @Unique
    private static int herzium$feedbackColor(float intensity, int rgb) {
        int alpha = Math.max(0, Math.min(255, Math.round(210.0F * intensity)));
        return alpha << 24 | rgb;
    }

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
