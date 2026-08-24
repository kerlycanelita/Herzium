package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.compat.ExternalInputCompatibility;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import dev.zymekoh.herzium.gui.HerziumWarningScreen;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 2000)
abstract class MinecraftMixin {
    /**
     * Keep the visible vanilla options aligned with Herzium's always-on policy.
     * The lower-level mixins still enforce the policy if another mod changes
     * these values later in the session.
     */
    @Inject(method = "run", at = @At("HEAD"))
    private void herzium$applyUncappedOptions(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        herzium$enforceCoreOptions(minecraft);
        minecraft.getWindow().updateVsync(false);
        if (!KoHsiumIntegration.present() && InputConstants.isRawMouseInputSupported()) {
            minecraft.getWindow().updateRawMouseInput(!ExternalInputCompatibility.externalRawInputPresent());
        }
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void herzium$keepCoreOptionsOptimized(boolean advanceGameTime, CallbackInfo ci) {
        CoreDiagnostics.recordCoreFrameHook();
        herzium$enforceCoreOptions((Minecraft) (Object) this);
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void herzium$observeVanillaHotbarConfirmation(CallbackInfo ci) {
        ImmediateHotbarInput.onVanillaHotbarTick((Minecraft) (Object) this);
    }

    /**
     * Place the advisory after the initial resource reload, but before
     * onboarding, the title screen, or Quick Play. Continuing invokes the
     * untouched vanilla screen chain exactly once.
     */
    @Redirect(
            method = "onGameLoadFinished",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Runnable;run()V"))
    private void herzium$showPerformanceWarning(Runnable showInitialScreen) {
        if (HerziumConfig.get().startupWarningAcknowledged()) {
            showInitialScreen.run();
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        minecraft.setScreen(new HerziumWarningScreen(showInitialScreen));
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

    @Unique
    private static void herzium$enforceCoreOptions(Minecraft minecraft) {
        if (minecraft.options.enableVsync().get()) {
            minecraft.options.enableVsync().set(false);
        }

        if (minecraft.options.framerateLimit().get() != Options.UNLIMITED_FRAMERATE_CUTOFF) {
            minecraft.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
        }

        minecraft.getFramerateLimitTracker().setFramerateLimit(Options.UNLIMITED_FRAMERATE_CUTOFF);

        // KoHsium owns its explicit input-latency controls when both mods are present.
        // Herzium keeps VSync/FPS ownership and does not race those editable values.
        if (!KoHsiumIntegration.present()) {
            boolean vanillaRawInput = !ExternalInputCompatibility.externalRawInputPresent();
            if (InputConstants.isRawMouseInputSupported()
                    && minecraft.options.rawMouseInput().get() != vanillaRawInput) {
                minecraft.options.rawMouseInput().set(vanillaRawInput);
            }
            minecraft.options.smoothCamera = false;
        }
    }
}
