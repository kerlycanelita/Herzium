package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** 1.21.x container focus path; GUI rendering remains live while the world is omitted. */
@Mixin(value = GameRenderer.class, priority = 2000)
abstract class ContainerFocusRendererMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean herzium$omitLevelRenderingBehindContainer(boolean renderLevel) {
        if (renderLevel && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>) {
            CoreDiagnostics.recordContainerFrameOptimized();
            return false;
        }
        return renderLevel;
    }
}
