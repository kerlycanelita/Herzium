package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 2000)
abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Unique
    private int herzium$selectedSlotBeforeScroll = -1;

    @Redirect(
            method = "onPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/KeyMapping;click(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V"))
    private void herzium$selectMouseBoundHotbarOnInputEvent(InputConstants.Key key) {
        KeyMapping.click(key);
        ImmediateHotbarInput.selectFromMouse(this.minecraft, key.getValue());
    }

    @Inject(method = "onScroll", at = @At("HEAD"))
    private void herzium$captureSelectedSlotBeforeScroll(
            long window,
            double horizontal,
            double vertical,
            CallbackInfo ci) {
        this.herzium$selectedSlotBeforeScroll = ImmediateHotbarInput.selectedSlot(this.minecraft);
    }

    @Inject(method = "onScroll", at = @At("RETURN"))
    private void herzium$publishVanillaScrollSelection(
            long window,
            double horizontal,
            double vertical,
            CallbackInfo ci) {
        ImmediateHotbarInput.publishVanillaScrollSelection(
                this.minecraft,
                this.herzium$selectedSlotBeforeScroll);
    }
}
