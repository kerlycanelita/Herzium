package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.compat.ExternalInputCompatibility;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import dev.zymekoh.herzium.gui.HerziumWarningScreen;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 2000)
abstract class MinecraftMixin {
    /**
     * Applies Herzium's policy at the window and device level, once, on start-up.
     *
     * <p>Nothing here writes to {@link net.minecraft.client.Options}. Those
     * values are persisted to {@code options.txt}, so writing them made
     * Herzium's policy outlive the mod's own installation, froze the Video
     * Settings screen (every edit bounced back on the next frame) and made
     * cinematic camera permanently unreachable. The enforcement is redundant
     * anyway: {@code WindowMixin} and {@code GlDeviceMixin} force the swap
     * interval to zero whatever value is requested, and
     * {@code FramerateLimiterMixin} plus {@code FramerateLimitTrackerMixin}
     * bypass the pacing while the window is active. The user's stored
     * preferences are left exactly as they are -- including any value a
     * previous Herzium session already overwrote, which is deliberately not
     * restored because the original is no longer knowable.</p>
     */
    @Inject(method = "run", at = @At("HEAD"))
    private void herzium$applyWindowPolicy(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        minecraft.getWindow().updateVsync(false);
        // KoHsium owns its explicit input-latency controls when both mods are
        // present, so Herzium does not race those editable values.
        if (!KoHsiumIntegration.present() && InputConstants.isRawMouseInputSupported()) {
            minecraft.getWindow().updateRawMouseInput(!ExternalInputCompatibility.externalRawInputPresent());
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void herzium$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        CoreDiagnostics.recordCoreFrameHook();
        // Identity hash rather than the level itself: the diagnostics ledger
        // must not hold a reference to a ClientLevel, and must not import
        // Minecraft classes at all.
        CoreDiagnostics.observeSession(
                minecraft.level == null ? 0 : System.identityHashCode(minecraft.level));
        // Runs on every frame, including frames without a level, so a preview
        // left behind by a disconnect cannot retain the player it captured.
        ImmediateHotbarInput.releaseStalePreview(minecraft);
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void herzium$observeVanillaHotbarConfirmation(CallbackInfo ci) {
        ImmediateHotbarInput.onVanillaHotbarTick((Minecraft) (Object) this);
    }

    /**
     * Places the advisory after the initial resource reload, but before
     * onboarding, the title screen, or Quick Play. Continuing invokes the
     * untouched vanilla screen chain exactly once.
     *
     * <p>{@code ordinal = 0} pins the injection to the first matching call.
     * Without it Mixin binds every {@code Runnable.run()} in the method: today
     * there is exactly one, but if Mojang adds a second or moves this one into
     * a lambda, the advisory would swallow the wrong runnable and start-up
     * would hang with no visible error. {@code require = 1} turns that same
     * signature change into a loud mixin failure instead.</p>
     *
     * <p>{@link WrapOperation} rather than {@code @Redirect} because a redirect
     * is exclusive: it would break any other mod touching the initial screen
     * chain (onboarding, Quick Play, profile launchers). Wrapping leaves the
     * call site shareable, and deferring {@code original.call(...)} into the
     * screen's continuation keeps the rest of the chain intact.</p>
     */
    @WrapOperation(
            method = "onGameLoadFinished",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Runnable;run()V",
                    ordinal = 0),
            require = 1)
    private void herzium$showPerformanceWarning(
            Runnable showInitialScreen,
            Operation<Void> original) {
        if (HerziumConfig.get().startupWarningAcknowledged()) {
            original.call(showInitialScreen);
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        minecraft.setScreen(new HerziumWarningScreen(() -> original.call(showInitialScreen)));
    }

    /**
     * New worlds already wait until the player section is compiled and visible.
     * Vanilla then holds the loading screen for another decorative 500 ms.
     */
    @ModifyArg(
            method = "doWorldLoad",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;<init>(J)V"),
            index = 0)
    private long herzium$removeNewWorldCloseDelay(long vanillaDelayMs) {
        return vanillaDelayMs == LevelLoadTracker.LEVEL_LOAD_CLOSE_DELAY_MS
                ? 0L
                : vanillaDelayMs;
    }
}
