package dev.zymekoh.herzium.debug.mixin;

import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame and tick lifecycle instrumentation for the 26.2 GUI split. */
@Mixin(value = Minecraft.class, priority = 3000)
abstract class MinecraftTraceMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void herziumDebug$frameStart(boolean advanceGameTime, CallbackInfo ci) {
        DebugCollector.onFrameStart();
    }

    @Inject(method = "runTick", at = @At("RETURN"))
    private void herziumDebug$frameEnd(boolean advanceGameTime, CallbackInfo ci) {
        DebugCollector.onFrameEnd((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void herziumDebug$tickStart(CallbackInfo ci) {
        DebugCollector.onTickStart();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void herziumDebug$tickEnd(CallbackInfo ci) {
        DebugCollector.onTickEnd((Minecraft) (Object) this);
    }
}
