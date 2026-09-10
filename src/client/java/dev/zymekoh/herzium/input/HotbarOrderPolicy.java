package dev.zymekoh.herzium.input;

import java.util.Arrays;

/** Fixed-size event metadata. Never stores, drains or replays a click queue. */
public final class HotbarOrderPolicy {
    private final long[] lastPress = new long[9];
    private final long[] passPress = new long[9];
    private long sequence;
    private HotbarOrder passOrder = HotbarOrder.VANILLA;
    private int acceptedSlot = -1;

    public synchronized void recordPress(int boundSlotMask) {
        if (boundSlotMask == 0) return;
        if (sequence == Long.MAX_VALUE) {
            Arrays.fill(lastPress, 0L);
            sequence = 0;
        }
        long serial = ++sequence;
        for (int slot = 0; slot < 9; slot++) {
            if ((boundSlotMask & (1 << slot)) != 0) lastPress[slot] = serial;
        }
    }

    public synchronized int preview(int pendingSlotMask, HotbarOrder order) {
        int selected = -1;
        for (int slot = 0; slot < 9; slot++) {
            if ((pendingSlotMask & (1 << slot)) != 0) {
                selected = order.preferred(selected, slot, lastPress);
            }
        }
        return selected;
    }

    public synchronized void beginPass(HotbarOrder order) {
        passOrder = order;
        acceptedSlot = -1;
        System.arraycopy(lastPress, 0, passPress, 0, 9);
    }

    /** Called only for slots whose real Vanilla consumeClick succeeded. */
    public synchronized boolean acceptSelection(int slot) {
        if (slot < 0 || slot >= 9 || passOrder == HotbarOrder.VANILLA) return true;
        int winner = passOrder.preferred(acceptedSlot, slot, passPress);
        acceptedSlot = winner;
        return winner == slot;
    }

    public synchronized void reset() {
        Arrays.fill(lastPress, 0L);
        Arrays.fill(passPress, 0L);
        sequence = 0L;
        acceptedSlot = -1;
        passOrder = HotbarOrder.VANILLA;
    }
}
