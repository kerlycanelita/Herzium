package dev.zymekoh.herzium.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives container focus mode a deliberate low-cost purple background. */
@Mixin(value = AbstractContainerScreen.class, priority = 1900)
abstract class ContainerFocusBackgroundMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void herzium$extractContainerFocusBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        graphics.fillGradient(0, 0, width, height, 0xFA08030F, 0xFA1A0828);
    }
}
