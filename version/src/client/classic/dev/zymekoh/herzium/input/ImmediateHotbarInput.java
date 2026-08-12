package dev.zymekoh.herzium.input;

import dev.zymekoh.herzium.config.HerziumConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Event-time hotbar selection for the classic 1.21 keyboard and mouse APIs. */
public final class ImmediateHotbarInput {
    private ImmediateHotbarInput() {
    }

    public static void selectFromKeyboard(Minecraft minecraft, int key, int scanCode) {
        selectMatchingSlot(minecraft, mapping -> mapping.matches(key, scanCode));
    }

    public static void selectFromMouse(Minecraft minecraft, int button) {
        selectMatchingSlot(minecraft, mapping -> mapping.matchesMouse(button));
    }

    private static void selectMatchingSlot(Minecraft minecraft, SlotMatcher matcher) {
        if (!HerziumConfig.get().immediateHotbarSelection()) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null
                || player.isSpectator()
                || minecraft.screen != null
                || minecraft.getOverlay() != null) {
            return;
        }

        boolean creativeHotbarAction = player.hasInfiniteMaterials()
                && (minecraft.options.keyLoadHotbarActivator.isDown()
                        || minecraft.options.keySaveHotbarActivator.isDown());
        if (creativeHotbarAction) {
            return;
        }

        KeyMapping[] hotbarSlots = minecraft.options.keyHotbarSlots;
        for (int slot = 0; slot < hotbarSlots.length; slot++) {
            KeyMapping mapping = hotbarSlots[slot];
            if (matcher.matches(mapping) && mapping.consumeClick()) {
                player.getInventory().setSelectedSlot(slot);
            }
        }
    }

    @FunctionalInterface
    private interface SlotMatcher {
        boolean matches(KeyMapping mapping);
    }
}
