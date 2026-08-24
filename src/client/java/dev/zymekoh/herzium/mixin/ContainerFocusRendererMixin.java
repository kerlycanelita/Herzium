package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import dev.zymekoh.herzium.render.ContainerFocusState;
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
    /**
     * Frame boundary for container focus. This is the first of Herzium's
     * container hooks to run in a frame, so the decision recorded here is what
     * {@code ContainerFocusBackgroundMixin} reads later in the same frame, and
     * the previous frame's decision is cleared here and nowhere else.
     */
    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean herzium$omitLevelExtractionBehindContainer(boolean renderLevel) {
        boolean omitLevel = renderLevel && herzium$containerOpen();
        ContainerFocusState.beginFrame(omitLevel);
        return renderLevel && !omitLevel;
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean herzium$omitLevelRenderingBehindContainer(boolean renderLevel) {
        // Reuses the extraction decision instead of asking again, so the two
        // halves of the frame can never disagree about the same world.
        if (renderLevel && ContainerFocusState.levelOmittedThisFrame()) {
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
