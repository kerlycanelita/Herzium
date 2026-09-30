package dev.zymekoh.herzium.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import dev.zymekoh.herzium.mixin.MultiPlayerGameModeInvoker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Adapts the optional slot-order policy to the existing Vanilla input pass. */
public final class HotbarOrderController {
    private static final HotbarOrderPolicy POLICY = new HotbarOrderPolicy();
    private static HotbarOrder passOrder = HotbarOrder.VANILLA;
    private static HotbarOrderPolicy.Options passOptions = HotbarOrderPolicy.Options.LEGACY;
    private static boolean passCaptured;

    private HotbarOrderController() { }

    public static void observeLogicalKey(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ordinarySelection(minecraft)) return;
        int mask = 0;
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length && slot < 9; slot++) {
            if (clicked(minecraft.options.keyHotbarSlots[slot], key)) mask |= 1 << slot;
        }
        if (clicked(minecraft.options.keyUse, key)) mask |= 1 << HotbarOrderPolicy.USE;
        if (clicked(minecraft.options.keyAttack, key)) mask |= 1 << HotbarOrderPolicy.ATTACK;
        if (clicked(minecraft.options.keySwapOffhand, key)) mask |= 1 << HotbarOrderPolicy.SWAP;
        if (clicked(minecraft.options.keyDrop, key)) mask |= 1 << HotbarOrderPolicy.DROP;
        // One press, one serial, even when the key is bound to several mappings.
        POLICY.recordEvent(mask);
    }

    public static void beginPass(Minecraft minecraft) {
        boolean ordinary = ordinarySelection(minecraft);
        passOrder = ordinary ? HerziumConfig.get().hotbarOrder() : HotbarOrder.VANILLA;
        passOptions = ordinary ? HerziumConfig.get().burstOptions() : HotbarOrderPolicy.Options.LEGACY;
        passCaptured = false;
        POLICY.beginPass(passOrder, passOptions, selectedSlot(minecraft), clickCounts(minecraft));
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

    /** The action type of a mapping (use, attack, offhand swap, drop), or -1. */
    public static int actionTypeOf(Minecraft minecraft, KeyMapping mapping) {
        if (mapping == minecraft.options.keyUse) return HotbarOrderPolicy.USE;
        if (mapping == minecraft.options.keyAttack) return HotbarOrderPolicy.ATTACK;
        if (mapping == minecraft.options.keySwapOffhand) return HotbarOrderPolicy.SWAP;
        if (mapping == minecraft.options.keyDrop) return HotbarOrderPolicy.DROP;
        return -1;
    }

    /** True when this pass must leave the slot's click queued for the next tick. */
    public static boolean defersHotbarClick(int slot) {
        return POLICY.defers(slot);
    }

    public static void hotbarClickConsumed(int slot, int remainingClicks) {
        POLICY.consumed(slot, remainingClicks);
    }

    /** False when the pass must leave this action click queued, with the key pressed before it. */
    public static boolean allowsActionClick(int type) {
        return POLICY.allowsAction(type);
    }

    public static void actionClickConsumed(int type, int remainingClicks) {
        POLICY.actionConsumed(type, remainingClicks);
    }

    public static boolean acceptSelection(int slot) {
        return !ordinarySelection(Minecraft.getInstance()) || POLICY.acceptSelection(slot);
    }

    public static int previewPendingSlot(Minecraft minecraft) {
        HerziumConfig config = HerziumConfig.get();
        return POLICY.preview(clickCounts(minecraft), config.hotbarOrder(), config.burstOptions(),
                selectedSlot(minecraft));
    }

    /**
     * Vanilla sends an offhand swap or a drop straight away, but the slot its
     * hotbar keys selected only at the next use, attack or tick. A key and a
     * swap in the same tick therefore swapped the item held before the key.
     * With the option on, the server learns the slot first, the way Vanilla
     * already sends it before a use or an attack.
     */
    public static void sendSlotBeforeOffhandAction(Minecraft minecraft) {
        if (passOrder == HotbarOrder.VANILLA || !passOptions.offhandSync() || minecraft.gameMode == null) return;
        ((MultiPlayerGameModeInvoker) minecraft.gameMode).herzium$ensureHasSentCarriedItem();
    }

    public static void captureAlternatePass(Minecraft minecraft) {
        if (passOrder != HotbarOrder.VANILLA && !passCaptured) {
            passCaptured = true;
            ImmediateHotbarInput.markVanillaHotbarPassCompleted(minecraft);
        }
    }

    /** End of Vanilla's keybind pass: seal it if nothing did, then close it. */
    public static void captureRemainingPass(Minecraft minecraft) {
        if (!passCaptured) {
            passCaptured = true;
            ImmediateHotbarInput.markVanillaHotbarPassCompleted(minecraft);
        }
        POLICY.endPass();
    }

    /** Vanilla's wheel just changed the selected slot; a last-press order treats it as the newest input. */
    public static void observeWheelSelection(Minecraft minecraft) {
        if (!ordinarySelection(minecraft) || !HerziumConfig.get().hotbarOrder().lastPressWins()) return;
        POLICY.noteWheel(selectedSlot(minecraft));
    }

    public static String configuredOrderName() { return HerziumConfig.get().hotbarOrder().name(); }

    public static void reset() {
        POLICY.reset();
        passOrder = HotbarOrder.VANILLA;
        passOptions = HotbarOrderPolicy.Options.LEGACY;
        passCaptured = false;
    }

    private static int pendingClicks(KeyMapping mapping) {
        return ((KeyMappingAccessor) mapping).herzium$getPendingClickCount();
    }

    private static boolean clicked(KeyMapping mapping, InputConstants.Key key) {
        KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
        return accessor.herzium$getBoundKey().equals(key) && accessor.herzium$getPendingClickCount() > 0;
    }

    private static int[] clickCounts(Minecraft minecraft) {
        KeyMapping[] slots = minecraft.options.keyHotbarSlots;
        int[] counts = new int[HotbarOrderPolicy.TYPES];
        for (int slot = 0; slot < Math.min(9, slots.length); slot++) {
            counts[slot] = pendingClicks(slots[slot]);
        }
        counts[HotbarOrderPolicy.USE] = pendingClicks(minecraft.options.keyUse);
        counts[HotbarOrderPolicy.ATTACK] = pendingClicks(minecraft.options.keyAttack);
        counts[HotbarOrderPolicy.SWAP] = pendingClicks(minecraft.options.keySwapOffhand);
        counts[HotbarOrderPolicy.DROP] = pendingClicks(minecraft.options.keyDrop);
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
