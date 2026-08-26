package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import dev.zymekoh.herzium.render.FramePolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the frame limit while the player is actually playing, and only then.
 *
 * <p>Vanilla throttles for five distinct reasons, and Herzium now overrides
 * exactly one of them -- {@code NONE}, the case where the player is at the
 * controls with a level on screen. The other four are handed back:</p>
 *
 * <ul>
 *   <li>{@code WINDOW_ICONIFIED}: 10 fps. Nothing is visible.</li>
 *   <li>{@code SHORT_AFK}: 30 fps after a minute without input.</li>
 *   <li>{@code LONG_AFK}: 10 fps after ten minutes without input.</li>
 *   <li>{@code OUT_OF_LEVEL_MENU}: 60 fps on the title screen and in menus
 *       with no level loaded, where the image is static.</li>
 * </ul>
 *
 * <p>This used to override all of them whenever the window had focus, which is
 * the one thing in this mod that could make a long session <em>slower</em>.
 * Rendering a static title screen, or an empty room the player walked away
 * from, at a couple of thousand frames a second holds the GPU at its power and
 * thermal ceiling for as long as it lasts. Cards respond by dropping sustained
 * clocks, so the frame rate that matters -- the one during play -- is worse
 * after three hours than it was in the first ten minutes. Nothing was gained
 * for it: nobody was looking.</p>
 *
 * <p>The AFK thresholds are vanilla's own and are gated behind the player's
 * {@code inactivityFpsLimit} option. Setting it to "minimized" makes vanilla
 * report {@code NONE} while AFK, and Herzium then stays uncapped -- so the
 * player's own setting decides, which is the point.</p>
 */
@Mixin(value = FramerateLimitTracker.class, priority = 10000)
abstract class FramerateLimitTrackerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract FramerateLimitTracker.FramerateThrottleReason getThrottleReason();

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void herzium$useUnlimitedFramerate(CallbackInfoReturnable<Integer> cir) {
        if (FramePolicy.shouldRunUncapped(this.minecraft, this.getThrottleReason())) {
            cir.setReturnValue(Options.UNLIMITED_FRAMERATE_CUTOFF);
        }
    }
}
