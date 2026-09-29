package dev.zymekoh.herzium.input;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Fixed-size event metadata. Never stores, drains or replays a click queue:
 * Vanilla's click counters stay the only authority, and this class only keeps
 * the order in which their clicks were pressed.
 */
public final class HotbarOrderPolicy {
    /** Serials kept per slot. Older clicks beyond it count as pressed long ago. */
    private static final int TRACKED_CLICKS = 32;

    @SuppressWarnings("unchecked")
    private final ArrayDeque<Long>[] pending = new ArrayDeque[9];
    private long sequence;
    /** The first Use and the first Attack pressed since the last pass began; 0 if none. */
    private long nextUseBoundary;
    private long nextAttackBoundary;

    private HotbarOrder passOrder = HotbarOrder.VANILLA;
    private long passActionBoundary;
    private final long[] passPress = new long[9];
    private final boolean[] passDeferred = new boolean[9];
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

    public HotbarOrderPolicy() {
        for (int slot = 0; slot < 9; slot++) {
            pending[slot] = new ArrayDeque<>();
        }
    }

    public synchronized void recordPress(int boundSlotMask) {
        if (boundSlotMask == 0) return;
        long serial = nextSerial();
        for (int slot = 0; slot < 9; slot++) {
            if ((boundSlotMask & (1 << slot)) != 0) {
                pending[slot].addLast(serial);
                if (pending[slot].size() > TRACKED_CLICKS) pending[slot].removeFirst();
            }
        }
    }

    /**
     * A Use click. Vanilla resolves every hotbar key of a tick before its
     * clicks, so a key pressed after the first pending Use would otherwise
     * decide which item that Use places or uses.
     */
    public synchronized void recordUse() {
        long serial = nextSerial();
        if (nextUseBoundary == 0L) nextUseBoundary = serial;
    }

    /**
     * An Attack click. It only bounds the pass when no Use is pending: Vanilla
     * attacks before it uses within a tick, so "attack, key, use" is the key
     * choosing the item for the Use, which is how Vanilla already plays it.
     */
    public synchronized void recordAttack() {
        long serial = nextSerial();
        if (nextAttackBoundary == 0L) nextAttackBoundary = serial;
    }

    /** The slot the next pass will select, or -1 if it will not select one. */
    public synchronized int preview(int[] clickCounts, HotbarOrder order, int currentSlot) {
        long boundary = order == HotbarOrder.HERZIUM ? nextBoundary() : 0L;
        long superseded = superseded(order, currentSlot);
        long[] press = new long[9];
        int selected = -1;
        for (int slot = 0; slot < 9; slot++) {
            sync(slot, clickCounts[slot]);
            if (clickCounts[slot] <= 0 || deferred(slot, clickCounts[slot], boundary)) continue;
            press[slot] = effectivePress(slot, boundary);
            if (!stale(slot, currentSlot, press[slot], superseded)) {
                selected = order.preferred(selected, slot, press);
            }
        }
        return selected;
    }

    /** Freezes the order, the Use/Attack boundary and the press order for one Vanilla pass. */
    public synchronized void beginPass(HotbarOrder order, int currentSlot, int[] clickCounts) {
        passOrder = order;
        acceptedSlot = -1;
        passCurrentSlot = currentSlot;
        // Every Use and Attack click pressed so far is consumed by this pass.
        passActionBoundary = order == HotbarOrder.HERZIUM ? nextBoundary() : 0L;
        nextUseBoundary = 0L;
        nextAttackBoundary = 0L;
        passSuperseded = superseded(order, currentSlot);
        for (int slot = 0; slot < 9; slot++) {
            sync(slot, clickCounts[slot]);
            passDeferred[slot] = clickCounts[slot] > 0 && deferred(slot, clickCounts[slot], passActionBoundary);
            passPress[slot] = clickCounts[slot] > 0 && !passDeferred[slot]
                    ? effectivePress(slot, passActionBoundary) : 0L;
        }
    }

    /**
     * Whether this pass must leave the slot's click queued. True only under the
     * Herzium order, for a key whose oldest queued click was pressed after the
     * first pending Use, or the first Attack when no Use is pending: that click
     * belongs to the next tick.
     */
    public synchronized boolean defers(int slot) {
        return slot >= 0 && slot < 9 && passOrder == HotbarOrder.HERZIUM && passDeferred[slot];
    }

    /** Called after Vanilla really consumed one click of the slot. */
    public synchronized void consumed(int slot, int remainingClicks) {
        if (slot >= 0 && slot < 9) sync(slot, remainingClicks);
    }

    /** Called only for slots whose real Vanilla consumeClick succeeded. */
    public synchronized boolean acceptSelection(int slot) {
        if (slot < 0 || slot >= 9 || passOrder == HotbarOrder.VANILLA) return true;
        if (stale(slot, passCurrentSlot, passPress[slot], passSuperseded)) return false;
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
        for (ArrayDeque<Long> clicks : pending) clicks.clear();
        Arrays.fill(passPress, 0L);
        Arrays.fill(passDeferred, false);
        sequence = 0L;
        nextUseBoundary = 0L;
        nextAttackBoundary = 0L;
        passActionBoundary = 0L;
        acceptedSlot = -1;
        passOrder = HotbarOrder.VANILLA;
        passCurrentSlot = -1;
        passSuperseded = 0L;
        selectionSlot = -1;
        selectionPress = 0L;
    }

    /** The first pending Use; the first pending Attack only when no Use is pending. */
    private long nextBoundary() {
        return nextUseBoundary != 0L ? nextUseBoundary : nextAttackBoundary;
    }

    private long nextSerial() {
        if (sequence == Long.MAX_VALUE) reset();
        return ++sequence;
    }

    /**
     * Keeps the tracked serials in step with Vanilla's counter. Clicks consumed
     * by someone else are the oldest ones; clicks Herzium never saw pressed are
     * treated as older than every tracked one.
     */
    private void sync(int slot, int clickCount) {
        ArrayDeque<Long> clicks = pending[slot];
        while (clicks.size() > Math.max(0, clickCount)) clicks.removeFirst();
    }

    /** Vanilla consumes the oldest click first, so that press decides the tick. */
    private boolean deferred(int slot, int clickCount, long boundary) {
        if (boundary == 0L || clickCount > pending[slot].size()) return false;
        Long oldest = pending[slot].peekFirst();
        return oldest != null && oldest > boundary;
    }

    /** The newest press this pass can attribute to the slot: before the boundary, if there is one. */
    private long effectivePress(int slot, long boundary) {
        long newest = 0L;
        Iterator<Long> clicks = pending[slot].iterator();
        while (clicks.hasNext()) {
            long serial = clicks.next();
            if (boundary == 0L || serial < boundary) newest = Math.max(newest, serial);
        }
        return newest;
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
    private static boolean stale(int slot, int currentSlot, long press, long superseded) {
        return superseded > 0L && slot != currentSlot && press <= superseded;
    }
}
