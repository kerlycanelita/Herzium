package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.render.ContainerFocusState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives container focus mode a deliberate low-cost purple background.
 *
 * <p>Strictly conditional on {@code ContainerFocusRendererMixin} having omitted
 * the level in this very frame. Painting an opaque gradient over a world that
 * is still being rendered costs the full world draw and hides it as well, so
 * without the renderer's confirmation this mixin does nothing and the container
 * screen falls back to vanilla's own background.</p>
 *
 * <p>Known side effects while it is active: F2 with an inventory open no longer
 * captures the world, and the player loses peripheral vision while using a
 * chest.</p>
 */
@Mixin(value = AbstractContainerScreen.class, priority = 1900)
abstract class ContainerFocusBackgroundMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void herzium$extractContainerFocusBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        if (!ContainerFocusState.levelOmittedThisFrame()) {
            return;
        }

        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int height = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        graphics.fillGradient(0, 0, width, height, 0xFA08030F, 0xFA1A0828);
    }
}
