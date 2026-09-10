package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.herzium.Herzium;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.gui.HerziumWarningScreen;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.input.HotbarOrderController;
import dev.zymekoh.herzium.render.CombatItemClassifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 2000)
abstract class MinecraftMixin {
    @Unique
    private static int herzium$sessionId;

    /**
     * Frees preview state, on every frame without exception.
     *
     * <p>Deliberately at {@code HEAD} rather than beside the session check
     * below. {@code processQueuedPackets} and {@code runAllTasks} both sit
     * inside the {@code if (advanceGameTime)} branch that {@code runTick} opens
     * at offset 89, and {@code run} passes {@code !oom}: after an
     * {@code OutOfMemoryError} the whole branch is skipped. A release hook
     * placed after that branch would stop running exactly when the client is
     * shedding memory, which is the worst possible moment to keep holding a
     * {@link net.minecraft.client.player.LocalPlayer} and, through it, a whole
     * {@code ClientLevel}. Reading a screen field one frame stale costs a frame
     * of cosmetics; not running costs the leak this hook exists to prevent.</p>
     */
    @Inject(method = "runTick", at = @At("HEAD"))
    private void herzium$releaseStalePreviewEveryFrame(boolean advanceGameTime, CallbackInfo ci) {
        ImmediateHotbarInput.releaseStalePreview((Minecraft) (Object) this);
    }

    /**
     * Notices the world change once the world has actually changed.
     *
     * <p>This used to sit at {@code runTick} HEAD, which is offset 0, while the
     * field it reads is assigned later in the same method: the client's level
     * arrives through {@code processQueuedPackets} at offset 111 or through a
     * task drained by {@code runAllTasks} at offset 124, both after HEAD. The
     * check therefore compared against the previous frame's level and fired one
     * frame late, leaving a frame in which item classifications cached against
     * the old world's tags were still being served for the new one.</p>
     *
     * <p>Injecting after {@code runAllTasks} covers both routes, because a
     * packet handler that hands {@code setLevel} to the main thread is drained
     * there too. The call site names {@code Minecraft} as the owner even though
     * the method is inherited from {@code BlockableEventLoop}, so that is what
     * the target has to say.</p>
     */
    @Inject(
            method = "runTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;runAllTasks()V",
                    shift = At.Shift.AFTER),
            require = 1)
    private void herzium$onSessionSettled(boolean advanceGameTime, CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        // Identity hash rather than the level itself, so nothing here can keep a
        // ClientLevel alive past its disconnect.
        int sessionId = minecraft.level == null ? 0 : System.identityHashCode(minecraft.level);
        if (sessionId != 0 && sessionId != herzium$sessionId) {
            herzium$sessionId = sessionId;
            // Entering a level is when the server's tag sync lands, so it is the
            // moment anything derived from item tags stops being trustworthy.
            CombatItemClassifier.invalidate();
            ImmediateHotbarInput.resetSession();
        }
    }

    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void herzium$markVanillaHotbarPassCompleted(CallbackInfo ci) {
        HotbarOrderController.captureRemainingPass((Minecraft) (Object) this);
    }

    /** Never let a render-only future slot survive into a Vanilla action. */
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void herzium$useOnlyTheCommittedSlot(CallbackInfo ci) {
        ImmediateHotbarInput.discardDivergentPreviewBeforeAction((Minecraft) (Object) this);
    }

    /** The same fail-closed boundary applies to attack and block breaking. */
    @Inject(method = "startAttack", at = @At("HEAD"))
    private void herzium$attackOnlyWithTheCommittedSlot(CallbackInfoReturnable<Boolean> cir) {
        ImmediateHotbarInput.discardDivergentPreviewBeforeAction((Minecraft) (Object) this);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"))
    private void herzium$continueAttackOnlyWithTheCommittedSlot(boolean leftClick, CallbackInfo ci) {
        if (leftClick) {
            ImmediateHotbarInput.discardDivergentPreviewBeforeAction((Minecraft) (Object) this);
        }
    }

    /** A newly opened screen invalidates a world-input preview immediately. */
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void herzium$clearPreviewBeforeScreen(Screen screen, CallbackInfo ci) {
        if (screen != null) {
            ImmediateHotbarInput.clearPreview();
        }
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
     *
     * <p>That deferral is the one part of this that runs outside the injected
     * frame, so it carries a fallback: if calling the wrapped operation later
     * fails, the captured runnable is invoked directly. That skips any other
     * mod's wrapper, which is a bad outcome -- but the alternative is the exact
     * failure this hook exists to prevent, a start-up that stops on a screen
     * that will never advance, with nothing in the log to say why.</p>
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
        minecraft.setScreen(new HerziumWarningScreen(
                () -> herzium$continueStartup(showInitialScreen, original)));
    }

    @Unique
    private static void herzium$continueStartup(Runnable showInitialScreen, Operation<Void> original) {
        try {
            original.call(showInitialScreen);
        } catch (Throwable failure) {
            Herzium.LOGGER.error(
                    "Herzium could not resume the vanilla start-up chain through the wrapped "
                            + "operation; falling back to the captured runnable. Another mod's "
                            + "wrapper around this call may have been skipped.",
                    failure);
            showInitialScreen.run();
        }
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
