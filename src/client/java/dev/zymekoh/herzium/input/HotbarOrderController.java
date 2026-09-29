package dev.zymekoh.herzium.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Adapts the optional slot-order policy to the existing Vanilla input pass. */
public final class HotbarOrderController {
    private static final HotbarOrderPolicy POLICY = new HotbarOrderPolicy();
    private static HotbarOrder passOrder = HotbarOrder.VANILLA;
    private static boolean passCaptured;

    private HotbarOrderController() { }

    public static void observeLogicalKey(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ordinarySelection(minecraft)) return;
        int mask = 0;
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            KeyMappingAccessor mapping = (KeyMappingAccessor) minecraft.options.keyHotbarSlots[slot];
            if (mapping.herzium$getBoundKey().equals(key)
                    && mapping.herzium$getPendingClickCount() > 0) mask |= 1 << slot;
        }
        POLICY.recordPress(mask);
        if (clicked(minecraft.options.keyAttack, key)) POLICY.recordAttack();
        if (clicked(minecraft.options.keyUse, key)) POLICY.recordUse();
    }

    public static void beginPass(Minecraft minecraft) {
        passOrder = ordinarySelection(minecraft)
                ? HerziumConfig.get().hotbarOrder() : HotbarOrder.VANILLA;
        passCaptured = false;
        POLICY.beginPass(passOrder, selectedSlot(minecraft), clickCounts(minecraft));
        ImmediateHotbarInput.notePassStart();
    }

    /** The hotbar slot a mapping selects, or -1 for any other mapping. */
    public static int hotbarSlotOf(Minecraft minecraft, KeyMapping mapping) {
        KeyMapping[] slots = minecraft.options.keyHotbarSlots;
        for (int slot = 0; slot < slots.length; slot++) {
            if (slots[slot] == mapping) return slot;
        }
        return -1;
    }

    /** True when this pass must leave the slot's click queued for the next tick. */
    public static boolean defersHotbarClick(int slot) {
        return POLICY.defers(slot);
    }

    public static void hotbarClickConsumed(int slot, int remainingClicks) {
        POLICY.consumed(slot, remainingClicks);
    }

    public static boolean acceptSelection(int slot) {
        return !ordinarySelection(Minecraft.getInstance()) || POLICY.acceptSelection(slot);
    }

    public static int previewPendingSlot(Minecraft minecraft) {
        return POLICY.preview(clickCounts(minecraft), HerziumConfig.get().hotbarOrder(), selectedSlot(minecraft));
    }

    public static void captureAlternatePass(Minecraft minecraft) {
        if (passOrder != HotbarOrder.VANILLA && !passCaptured) {
            passCaptured = true;
            ImmediateHotbarInput.markVanillaHotbarPassCompleted(minecraft);
        }
    }

    public static void captureRemainingPass(Minecraft minecraft) {
        if (!passCaptured) {
            passCaptured = true;
            ImmediateHotbarInput.markVanillaHotbarPassCompleted(minecraft);
        }
    }

    public static String configuredOrderName() { return HerziumConfig.get().hotbarOrder().name(); }

    public static void reset() {
        POLICY.reset();
        passOrder = HotbarOrder.VANILLA;
        passCaptured = false;
    }

    private static boolean clicked(KeyMapping mapping, InputConstants.Key key) {
        KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
        return accessor.herzium$getBoundKey().equals(key) && accessor.herzium$getPendingClickCount() > 0;
    }

    private static int[] clickCounts(Minecraft minecraft) {
        KeyMapping[] slots = minecraft.options.keyHotbarSlots;
        int[] counts = new int[9];
        for (int slot = 0; slot < Math.min(9, slots.length); slot++) {
            counts[slot] = ((KeyMappingAccessor) slots[slot]).herzium$getPendingClickCount();
        }
        return counts;
    }

    private static int selectedSlot(Minecraft minecraft) {
        return minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot();
    }

    private static boolean ordinarySelection(Minecraft minecraft) {
        return minecraft.player != null
                && !minecraft.player.isSpectator()
                && minecraft.screen == null
                && minecraft.getOverlay() == null
                && !(minecraft.player.hasInfiniteMaterials()
                        && (minecraft.options.keySaveHotbarActivator.isDown()
                                || minecraft.options.keyLoadHotbarActivator.isDown()));
    }
}
