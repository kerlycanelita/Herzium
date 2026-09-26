package dev.zymekoh.herzium.input;

import java.util.Arrays;

/** Fixed-size event metadata. Never stores, drains or replays a click queue. */
public final class HotbarOrderPolicy {
    private final long[] lastPress = new long[9];
    private final long[] passPress = new long[9];
    private long sequence;
    private HotbarOrder passOrder = HotbarOrder.VANILLA;
    private int acceptedSlot = -1;
    private int passCurrentSlot = -1;
    private long passSuperseded;
    /**
     * The slot the Herzium order selected last, and the press that selected it.
     *
     * <p>Vanilla consumes one click per hotbar mapping per tick, so a key that
     * was tapped twice inside one tick, or held until it auto-repeated, still
     * has clicks queued after a newer key has won. Without this, those older
     * clicks select their slot again on the following tick and undo the newer
     * press. Under the Herzium order a queued click whose press is not newer
     * than the one behind the current selection is stale: Vanilla still
     * consumes it, but it no longer selects anything.</p>
     */
    private int selectionSlot = -1;
    private long selectionPress;

    public synchronized void recordPress(int boundSlotMask) {
        if (boundSlotMask == 0) return;
        if (sequence == Long.MAX_VALUE) {
            Arrays.fill(lastPress, 0L);
            sequence = 0;
            selectionPress = 0L;
        }
        long serial = ++sequence;
        for (int slot = 0; slot < 9; slot++) {
            if ((boundSlotMask & (1 << slot)) != 0) lastPress[slot] = serial;
        }
    }

    public synchronized int preview(int pendingSlotMask, HotbarOrder order, int currentSlot) {
        long superseded = superseded(order, currentSlot);
        int selected = -1;
        for (int slot = 0; slot < 9; slot++) {
            if ((pendingSlotMask & (1 << slot)) != 0 && !stale(slot, currentSlot, lastPress, superseded)) {
                selected = order.preferred(selected, slot, lastPress);
            }
        }
        return selected;
    }

    public synchronized void beginPass(HotbarOrder order, int currentSlot) {
        passOrder = order;
        acceptedSlot = -1;
        passCurrentSlot = currentSlot;
        passSuperseded = superseded(order, currentSlot);
        System.arraycopy(lastPress, 0, passPress, 0, 9);
    }

    /** Called only for slots whose real Vanilla consumeClick succeeded. */
    public synchronized boolean acceptSelection(int slot) {
        if (slot < 0 || slot >= 9 || passOrder == HotbarOrder.VANILLA) return true;
        if (stale(slot, passCurrentSlot, passPress, passSuperseded)) return false;
        int winner = passOrder.preferred(acceptedSlot, slot, passPress);
        acceptedSlot = winner;
        if (winner != slot) return false;
        if (passOrder == HotbarOrder.HERZIUM) {
            selectionSlot = slot;
            selectionPress = passPress[slot];
        }
        return true;
    }

    public synchronized void reset() {
        Arrays.fill(lastPress, 0L);
        Arrays.fill(passPress, 0L);
        sequence = 0L;
        acceptedSlot = -1;
        passOrder = HotbarOrder.VANILLA;
        passCurrentSlot = -1;
        passSuperseded = 0L;
        selectionSlot = -1;
        selectionPress = 0L;
    }

    /**
     * The press a queued click has to be newer than. Zero, so nothing is stale,
     * outside the Herzium order and whenever the selection has since moved
     * without it -- the wheel, the server or another mod. Those keep plain
     * last-press ordering among the queued slots.
     */
    private long superseded(HotbarOrder order, int currentSlot) {
        return order == HotbarOrder.HERZIUM && currentSlot >= 0 && currentSlot == selectionSlot
                ? selectionPress : 0L;
    }

    /** Re-selecting the current slot changes nothing, so it is never stale. */
    private static boolean stale(int slot, int currentSlot, long[] press, long superseded) {
        return superseded > 0L && slot != currentSlot && press[slot] <= superseded;
    }
}
