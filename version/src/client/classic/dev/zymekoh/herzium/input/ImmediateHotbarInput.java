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

    /** Returns the current Vanilla-selected slot without changing it. */
    public static int selectedSlot(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        return player == null ? -1 : player.getInventory().getSelectedSlot();
    }

    /** Publishes a Vanilla mouse-wheel selection to the visual renderer only. */
    public static void publishVanillaScrollSelection(Minecraft minecraft, int previousSlot) {
        if (!HerziumConfig.get().immediateHotbarSelection() || previousSlot < 0) {
            return;
        }

        int selectedSlot = selectedSlot(minecraft);
        if (selectedSlot >= 0 && selectedSlot != previousSlot) {
            ImmediateHotbarVisualState.markSelectionChanged();
        }
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
                int previousSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(slot);
                if (slot != previousSlot) {
                    ImmediateHotbarVisualState.markSelectionChanged();
                }
            }
        }
    }

    @FunctionalInterface
    private interface SlotMatcher {
        boolean matches(KeyMapping mapping);
    }
}
