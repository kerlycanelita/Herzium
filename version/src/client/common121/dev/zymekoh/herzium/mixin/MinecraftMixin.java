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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.x core option ownership without newer world-load tracker APIs. */
@Mixin(value = Minecraft.class, priority = 2000)
abstract class MinecraftMixin {
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

    @Redirect(
            method = "onGameLoadFinished",
            at = @At(value = "INVOKE", target = "Ljava/lang/Runnable;run()V"))
    private void herzium$showPerformanceWarning(Runnable showInitialScreen) {
        if (HerziumConfig.get().startupWarningAcknowledged()) {
            showInitialScreen.run();
            return;
        }

        Minecraft minecraft = (Minecraft) (Object) this;
        minecraft.setScreen(new HerziumWarningScreen(showInitialScreen));
    }

    @Unique
    private static void herzium$enforceCoreOptions(Minecraft minecraft) {
        if (minecraft.options.enableVsync().get()) {
            minecraft.options.enableVsync().set(false);
        }
        if (minecraft.options.framerateLimit().get() != Options.UNLIMITED_FRAMERATE_CUTOFF) {
            minecraft.options.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
        }
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
