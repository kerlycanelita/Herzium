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
    }

    public static void beginPass(Minecraft minecraft) {
        passOrder = ordinarySelection(minecraft)
                ? HerziumConfig.get().hotbarOrder() : HotbarOrder.VANILLA;
        passCaptured = false;
        POLICY.beginPass(passOrder);
    }

    public static boolean acceptSelection(int slot) {
        return !ordinarySelection(Minecraft.getInstance()) || POLICY.acceptSelection(slot);
    }

    public static int previewPendingSlot(Minecraft minecraft) {
        int mask = 0;
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            KeyMapping mapping = minecraft.options.keyHotbarSlots[slot];
            if (((KeyMappingAccessor) mapping).herzium$getPendingClickCount() > 0) mask |= 1 << slot;
        }
        return POLICY.preview(mask, HerziumConfig.get().hotbarOrder());
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
