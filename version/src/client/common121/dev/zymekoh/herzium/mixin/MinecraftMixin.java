package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.input.ImmediateActionFeedback;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.gui.HerziumWarningScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.x lifecycle hooks without the newer world-load tracker APIs. */
@Mixin(value = Minecraft.class, priority = 2000)
abstract class MinecraftMixin {
    @Unique
    private static int herzium$sessionId;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void herzium$onFrameStart(boolean advanceGameTime, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        int sessionId = minecraft.level == null ? 0 : System.identityHashCode(minecraft.level);
        if (sessionId != 0 && sessionId != herzium$sessionId) {
            herzium$sessionId = sessionId;
            ImmediateHotbarInput.resetSession();
            ImmediateActionFeedback.reset();
        }
        ImmediateHotbarInput.releaseStalePreview(minecraft);
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void herzium$markVanillaHotbarPassCompleted(CallbackInfo ci) {
        ImmediateHotbarInput.markVanillaHotbarPassCompleted((Minecraft) (Object) this);
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
}
