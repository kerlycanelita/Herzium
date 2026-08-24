package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps container GUI extraction and rendering at the active-window frame rate
 * while omitting the expensive live 3D world behind it. Client ticks, menu
 * state, slot interaction, and networking remain untouched.
 *
 * <p>Herzium paints nothing to fill the gap. {@code GameRenderer.render} clears
 * the colour texture at the top of every frame, so omitting the level leaves a
 * black backdrop with vanilla's own screen background drawn over it -- and that
 * is deliberately all it is. An earlier version painted a near-opaque purple
 * gradient there, which meant Herzium, not Minecraft and not whatever other mod
 * the player installed, decided what an open inventory looked like.</p>
 *
 * <p>That black backdrop is the whole cost of the option, which is why it is
 * off by default: leaving it off is plain vanilla, turning it on trades the
 * view of the world for the frames spent drawing it.</p>
 */
@Mixin(value = GameRenderer.class, priority = 2000)
abstract class ContainerFocusRendererMixin {
    @ModifyVariable(method = "extract", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean herzium$omitLevelExtractionBehindContainer(boolean renderLevel) {
        return renderLevel && !herzium$shouldOmitLevel();
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean herzium$omitLevelRenderingBehindContainer(boolean renderLevel) {
        if (renderLevel && herzium$shouldOmitLevel()) {
            CoreDiagnostics.recordContainerFrameOptimized();
            return false;
        }
        return renderLevel;
    }

    @Unique
    private static boolean herzium$shouldOmitLevel() {
        return HerziumConfig.get().containerFocus() && herzium$containerOpen();
    }

    @Unique
    private static boolean herzium$containerOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.screen instanceof AbstractContainerScreen<?>;
    }
}
