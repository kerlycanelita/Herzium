package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 replacement for {@code MinecraftMixin.herzium$clearPreviewBeforeScreen}.
 *
 * <p>26.2 removed {@code Minecraft.setScreen} and put screen ownership on
 * {@code Gui}, so the shared hook has no target there. The behaviour is
 * unchanged: opening a screen drops any world-input preview immediately, so a
 * preview can never survive into a screen the player is now interacting with.
 *
 * <p>This file is deliberately outside the source filter that rewrites
 * {@code Gui} to {@code Hud} for the HUD mixins. In 26.2 those are two
 * different classes -- {@code Hud} draws the HUD, {@code Gui} owns the screen
 * and overlay -- and this one wants the real {@code Gui}.</p>
 */
@Mixin(value = Gui.class, priority = 2000)
abstract class GuiScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void herzium$clearPreviewBeforeScreen(Screen screen, CallbackInfo ci) {
        if (screen != null) {
            ImmediateHotbarInput.clearPreview();
        }
    }
}
