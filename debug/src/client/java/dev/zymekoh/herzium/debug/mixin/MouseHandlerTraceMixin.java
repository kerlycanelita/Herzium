package dev.zymekoh.herzium.debug.mixin;

import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raw mouse callback observation; never owns or rewrites the cursor pipeline. */
@Mixin(value = MouseHandler.class, priority = 3000)
abstract class MouseHandlerTraceMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private int herziumDebug$slotBeforeScroll = -1;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void herziumDebug$button(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        DebugCollector.onMouseButton(info, action);
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void herziumDebug$scrollBefore(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        this.herziumDebug$slotBeforeScroll = this.minecraft.player == null
                ? -1
                : this.minecraft.player.getInventory().getSelectedSlot();
        DebugCollector.onMouseScroll(xOffset, yOffset, this.herziumDebug$slotBeforeScroll, -1, false);
    }

    @Inject(method = "onScroll", at = @At("RETURN"))
    private void herziumDebug$scrollAfter(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        int after = this.minecraft.player == null
                ? -1
                : this.minecraft.player.getInventory().getSelectedSlot();
        DebugCollector.onMouseScroll(xOffset, yOffset, this.herziumDebug$slotBeforeScroll, after, true);
        this.herziumDebug$slotBeforeScroll = -1;
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void herziumDebug$move(long handle, double x, double y, CallbackInfo ci) {
        DebugCollector.onMouseMove(x, y);
    }

    @Inject(method = "grabMouse", at = @At("HEAD"))
    private void herziumDebug$grab(CallbackInfo ci) {
        DebugCollector.onMouseGrab(true);
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"))
    private void herziumDebug$release(CallbackInfo ci) {
        DebugCollector.onMouseGrab(false);
    }
}
