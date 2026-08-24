package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Composably changes only the HUD expression used for the selected-slot read. */
@Mixin(value = Gui.class, priority = 2000)
abstract class HotbarVisualMixin {
    @ModifyExpressionValue(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedSlot()I"),
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
