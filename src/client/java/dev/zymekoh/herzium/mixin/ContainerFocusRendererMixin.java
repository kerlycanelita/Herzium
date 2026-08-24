package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps container GUI extraction and rendering at the active-window frame rate
 * while omitting the expensive live 3D world behind it. Client ticks, menu
 * state, slot interaction, and networking remain untouched.
 */
@Mixin(value = GameRenderer.class, priority = 2000)
abstract class ContainerFocusRendererMixin {
    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean herzium$omitLevelExtractionBehindContainer(boolean renderLevel) {
        return renderLevel && !herzium$containerOpen();
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean herzium$omitLevelRenderingBehindContainer(boolean renderLevel) {
        if (renderLevel && herzium$containerOpen()) {
            CoreDiagnostics.recordContainerFrameOptimized();
            return false;
        }
        return renderLevel;
    }

    private static boolean herzium$containerOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen instanceof AbstractContainerScreen<?>;
    }
}
