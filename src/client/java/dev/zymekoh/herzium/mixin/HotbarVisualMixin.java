package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.zymekoh.herzium.input.SwapPreview;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.input.ResponseMeter;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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
        // If this optional expression hook disappears because mappings changed
        // or another renderer replaced the hotbar, hand previewing fails closed
        // too instead of allowing the HUD and hand to show different slots.
        ImmediateHotbarInput.observeHudHook();
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null
                ? vanillaSlot
                : ImmediateHotbarInput.hudSlot(
                        minecraft.player.getInventory(),
                        vanillaSlot);
    }

    // An offhand swap on its way to the server: the two stacks are drawn
    // exchanged until it answers. Optional hooks; without them only the hands
    // show it.
    @WrapOperation(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private ItemStack herzium$drawSwapInHotbar(Inventory inventory, int index, Operation<ItemStack> original) {
        return SwapPreview.hotbar(Minecraft.getInstance().player, index, original.call(inventory, index));
    }

    @WrapOperation(
            method = "extractItemHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"),
            require = 0)
    private ItemStack herzium$drawSwapInOffhandSlot(Player player, Operation<ItemStack> original) {
        return SwapPreview.offhand(Minecraft.getInstance().player, original.call(player));
    }

    // The response meter's line, in the corner under the chat. Optional: without the hook the
    // numbers are still in the settings.
    @Inject(method = "extractItemHotbar", at = @At("TAIL"), require = 0)
    private void herzium$drawResponseMeter(CallbackInfo callback, @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        if (HerziumConfig.get().on(HerziumConfig.Feature.RESPONSE_METER)) {
            graphics.text(Minecraft.getInstance().font, ResponseMeter.hudLine(), 2, graphics.guiHeight() - 10, 0xC8E6DCF2);
        }
    }
}
