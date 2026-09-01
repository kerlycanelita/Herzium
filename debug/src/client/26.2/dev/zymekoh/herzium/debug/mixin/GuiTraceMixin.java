package dev.zymekoh.herzium.debug.mixin;

import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes 26.2 screen transitions after screen ownership moved into Gui. */
@Mixin(value = Gui.class, priority = 3000)
abstract class GuiTraceMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void herziumDebug$screenRequested(Screen screen, CallbackInfo ci) {
        DebugCollector.onSetScreen(this.minecraft, screen, false);
    }

    @Inject(method = "setScreen", at = @At("RETURN"))
    private void herziumDebug$screenApplied(Screen screen, CallbackInfo ci) {
        DebugCollector.onSetScreen(this.minecraft, screen, true);
    }
}
