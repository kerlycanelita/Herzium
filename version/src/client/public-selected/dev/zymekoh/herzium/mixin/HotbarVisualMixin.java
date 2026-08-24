package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Selected-slot HUD preview for 1.21 through 1.21.4's public field API. */
@Mixin(value = Gui.class, priority = 2000)
abstract class HotbarVisualMixin {
    @ModifyExpressionValue(
            method = "extractItemHotbar",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/player/Inventory;selected:I"),
            require = 0)
    private int herzium$renderVanillaResolvableHotbarInput(int vanillaSlot) {
        CoreDiagnostics.recordHotbarVisualHook();
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? vanillaSlot
                : ImmediateHotbarInput.visualSelectedSlot(
                        minecraft.player.getInventory(),
                        vanillaSlot);
    }
}
