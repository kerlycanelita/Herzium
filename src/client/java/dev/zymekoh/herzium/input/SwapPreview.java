package dev.zymekoh.herzium.input;

import dev.zymekoh.herzium.config.HerziumConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Draws an offhand swap the moment it is sent.
 *
 * <p>Vanilla does not predict the swap: the client sends it and both hands
 * keep their old items on screen until the server answers, a whole round trip
 * later. This shows the two stacks exchanged for that wait and nothing more.
 * The inventory is never touched, so every action still uses what the client
 * really holds; the picture ends the moment either stack is replaced by the
 * server's answer, the selected slot moves, or half a second passes without
 * one (a server may refuse the swap and say nothing).</p>
 */
public final class SwapPreview {
    private static final long MAX_NANOS = 500_000_000L;

    private static LocalPlayer player;
    private static ItemStack main = ItemStack.EMPTY;
    private static ItemStack off = ItemStack.EMPTY;
    private static int slot = -1;
    private static long armedNanos;

    private SwapPreview() {
    }

    /** Called right before Vanilla sends the swap. */
    public static void arm(Minecraft minecraft) {
        LocalPlayer current = minecraft.player;
        if (current == null || current.isSpectator()
                || !HerziumConfig.get().on(HerziumConfig.Feature.SWAP_PREVIEW)) {
            return;
        }
        if (active(current)) {
            // A second press before the answer swaps them back.
            clear();
            return;
        }
        int selected = current.getInventory().getSelectedSlot();
        ItemStack held = current.getInventory().getItem(selected);
        ItemStack other = current.getOffhandItem();
        if (held.isEmpty() && other.isEmpty()) {
            return;
        }
        player = current;
        slot = selected;
        main = held;
        off = other;
        armedNanos = System.nanoTime();
    }

    public static boolean active(LocalPlayer current) {
        if (slot < 0) {
            return false;
        }
        if (current != player
                || System.nanoTime() - armedNanos > MAX_NANOS
                || current.getInventory().getSelectedSlot() != slot
                || current.getInventory().getItem(slot) != main
                || current.getOffhandItem() != off) {
            clear();
            return false;
        }
        return true;
    }

    /** What the hotbar draws in a slot. */
    public static ItemStack hotbar(LocalPlayer current, int index, ItemStack real) {
        return current != null && index == slot && active(current) ? off : real;
    }

    /** What the offhand draws. */
    public static ItemStack offhand(LocalPlayer current, ItemStack real) {
        return current != null && active(current) ? main : real;
    }

    /** What the main hand draws, or null when there is no swap to show. */
    public static ItemStack mainHand(LocalPlayer current) {
        return active(current) ? off : null;
    }

    public static void clear() {
        player = null;
        main = ItemStack.EMPTY;
        off = ItemStack.EMPTY;
        slot = -1;
    }
}
