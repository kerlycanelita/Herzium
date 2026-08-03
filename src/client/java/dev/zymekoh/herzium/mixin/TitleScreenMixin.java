package dev.zymekoh.herzium.mixin;

import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes the first title screen fully visible immediately. */
@Mixin(value = TitleScreen.class, priority = 2000)
abstract class TitleScreenMixin {
    @Shadow
    private boolean fading;

    @Inject(
            method = "<init>(ZLnet/minecraft/client/gui/components/LogoRenderer;)V",
            at = @At("TAIL"))
    private void herzium$skipInitialTitleFade(
            boolean requestedFade,
            LogoRenderer logoRenderer,
            CallbackInfo ci) {
        this.fading = false;
    }
}
