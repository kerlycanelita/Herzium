package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes only completed Vanilla wheel selection without changing it. */
@Mixin(value = MouseHandler.class, priority = 2000)
abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private int herzium$selectedSlotBeforeScroll = -1;

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void herzium$captureSlotBeforeScroll(
            long handle,
            double xOffset,
            double yOffset,
            CallbackInfo ci) {
        this.herzium$selectedSlotBeforeScroll = this.minecraft.player == null
                ? -1
                : this.minecraft.player.getInventory().getSelectedSlot();
    }

    @Inject(method = "onScroll", at = @At("RETURN"))
    private void herzium$acceptVanillaScrollSelection(
            long handle,
            double xOffset,
            double yOffset,
            CallbackInfo ci) {
        if (this.herzium$selectedSlotBeforeScroll >= 0) {
            ImmediateHotbarInput.onVanillaScrollFinished(
                    this.minecraft,
                    this.herzium$selectedSlotBeforeScroll);
        }
        this.herzium$selectedSlotBeforeScroll = -1;
    }
}
