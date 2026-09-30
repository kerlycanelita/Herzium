package dev.zymekoh.herzium.input;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Fixed-size event metadata. Vanilla's click counters stay the only authority:
 * this class keeps the order in which their clicks were pressed and, for each
 * pass, how many of them the pass may take.
 *
 * <p>Event types 0 to 8 are the hotbar keys; {@link #USE}, {@link #ATTACK},
 * {@link #SWAP} and {@link #DROP} are the four clicks that act with the item in
 * hand. Every type keeps the press serials of its pending clicks, trimmed to
 * Vanilla's counter whenever a counter is read, so a click consumed by anyone
 * else is forgotten here too.</p>
 */
public final class HotbarOrderPolicy {
    public static final int USE = 9;
    public static final int ATTACK = 10;
    public static final int SWAP = 11;
    public static final int DROP = 12;
    public static final int TYPES = 13;

    /** Serials kept per type. Older clicks beyond it count as pressed long ago. */
    private static final int TRACKED_CLICKS = 32;
    private static final int UNLIMITED = Integer.MAX_VALUE;

    /**
     * How the Herzium order treats a burst. All off is Herzium 1.10.7.
     *
     * @param splitBursts   a click pressed after a key that waits for the next
     *                      tick waits with it, so it gets that key's item
     * @param strictActions an Attack bounds the pass like a Use, so it gets the
     *                      key pressed before it even when a Use follows
     * @param offhandSync   offhand swaps and drops bound the pass like a Use,
     *                      and the server learns the slot before they are sent
     */
    public record Options(boolean splitBursts, boolean strictActions, boolean offhandSync) {
        public static final Options LEGACY = new Options(false, false, false);
    }

    @SuppressWarnings("unchecked")
    private final ArrayDeque<Long>[] pending = new ArrayDeque[TYPES];
    private long sequence;

    private HotbarOrder passOrder = HotbarOrder.VANILLA;
    /** Between {@link #beginPass} and {@link #endPass}: Vanilla is still taking this pass's clicks. */
    private boolean passOpen;
    private final long[] passPress = new long[9];
    private final boolean[] passDeferred = new boolean[9];
    /** Action clicks the pass may still take, per type; UNLIMITED lets Vanilla drain them all. */
    private final int[] passAllowance = new int[TYPES];
    private int acceptedSlot = -1;
    private int passCurrentSlot = -1;
    private long passSuperseded;
    /**
     * The slot a last-press order selected last, and the press that selected it.
     *
     * <p>Vanilla consumes one click per hotbar mapping per tick, so a key that
     * was tapped twice inside one tick, or held until it auto-repeated, still
     * has clicks queued after a newer key has won. Without this, those older
     * clicks select their slot again on the following tick and undo the newer
     * press. Under a last-press order a queued click whose press is not newer
     * than the one behind the current selection is stale: Vanilla still
     * consumes it, but it no longer selects anything.</p>
     */
    private int selectionSlot = -1;
    private long selectionPress;

    public HotbarOrderPolicy() {
        for (int type = 0; type < TYPES; type++) {
            pending[type] = new ArrayDeque<>();
        }
        Arrays.fill(passAllowance, UNLIMITED);
    }

    public synchronized void recordPress(int boundSlotMask) {
        if (boundSlotMask == 0) return;
        long serial = nextSerial();
        for (int slot = 0; slot < 9; slot++) {
            if ((boundSlotMask & (1 << slot)) != 0) track(slot, serial);
        }
    }

    /** One press of a key that may be bound to several mappings: every type in the mask shares the serial. */
    public synchronized void recordEvent(int typeMask) {
        if (typeMask == 0) return;
        long serial = nextSerial();
        for (int type = 0; type < TYPES; type++) {
            if ((typeMask & (1 << type)) != 0) track(type, serial);
        }
    }

    public void recordUse() { recordEvent(1 << USE); }

    public void recordAttack() { recordEvent(1 << ATTACK); }

    public void recordSwap() { recordEvent(1 << SWAP); }

    public void recordDrop() { recordEvent(1 << DROP); }

    /** 1.10.7 overload: nine hotbar counters; every action click counts as drained by the pass. */
    public synchronized int preview(int[] clickCounts, HotbarOrder order, int currentSlot) {
        return preview(legacyCounts(clickCounts), order, Options.LEGACY, currentSlot);
    }

    /**
     * The slot the next pass will leave selected, or -1 if it will not select one.
     *
     * <p>Called while a pass is open -- Herzium seals the hotbar part of a pass before Vanilla
     * runs its clicks, and previews the next pass right there -- the clicks this pass is still
     * going to take are left out: they will be gone before the next pass begins, so they cannot
     * bound it.</p>
     */
    public synchronized int preview(int[] counts, HotbarOrder order, Options options, int currentSlot) {
        syncAll(counts);
        if (!passOpen) {
            return previewPending(counts, order, options, currentSlot);
        }
        int[] visible = counts.clone();
        List<List<Long>> hidden = new ArrayList<>();
        for (int type = USE; type < TYPES; type++) {
            List<Long> taken = new ArrayList<>();
            int take = passAllowance[type] == UNLIMITED ? pending[type].size() : Math.min(passAllowance[type], pending[type].size());
            for (int click = 0; click < take; click++) taken.add(pending[type].removeFirst());
            visible[type] = Math.max(0, visible[type] - (passAllowance[type] == UNLIMITED ? visible[type] : passAllowance[type]));
            hidden.add(taken);
        }
        try {
            return previewPending(visible, order, options, currentSlot);
        } finally {
            for (int type = TYPES - 1; type >= USE; type--) {
                List<Long> taken = hidden.get(type - USE);
                for (int click = taken.size() - 1; click >= 0; click--) pending[type].addFirst(taken.get(click));
            }
        }
    }

    /** Called when Vanilla's keybind pass is over: previews no longer leave its clicks out. */
    public synchronized void endPass() {
        passOpen = false;
    }

    private int previewPending(int[] counts, HotbarOrder order, Options options, int currentSlot) {
        long boundary = order == HotbarOrder.HERZIUM ? boundary(options) : 0L;
        long superseded = superseded(order, currentSlot);
        long[] press = new long[9];
        int selected = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (counts[slot] <= 0 || deferred(slot, counts[slot], boundary)) continue;
            press[slot] = effectivePress(slot, boundary);
            if (!stale(slot, currentSlot, press[slot], superseded)) {
                selected = order.preferred(selected, slot, press);
            }
        }
        return selected;
    }

    /** 1.10.7 overload, see {@link #preview(int[], HotbarOrder, int)}. */
    public synchronized void beginPass(HotbarOrder order, int currentSlot, int[] clickCounts) {
        beginPass(order, Options.LEGACY, currentSlot, legacyCounts(clickCounts));
        // 1.10.7 cleared its Use/Attack boundary at every pass: Vanilla drains
        // every action click of the pass.
        for (int type = USE; type < TYPES; type++) pending[type].clear();
    }

    /** Freezes the order, the boundary and the press order for one Vanilla pass. */
    public synchronized void beginPass(HotbarOrder order, Options options, int currentSlot, int[] counts) {
        passOrder = order;
        passOpen = true;
        acceptedSlot = -1;
        passCurrentSlot = currentSlot;
        syncAll(counts);
        long boundary = order == HotbarOrder.HERZIUM ? boundary(options) : 0L;
        passSuperseded = superseded(order, currentSlot);
        int winner = -1;
        for (int slot = 0; slot < 9; slot++) {
            passDeferred[slot] = counts[slot] > 0 && deferred(slot, counts[slot], boundary);
            passPress[slot] = counts[slot] > 0 && !passDeferred[slot]
                    ? effectivePress(slot, boundary) : 0L;
            if (counts[slot] > 0 && !passDeferred[slot]
                    && !stale(slot, currentSlot, passPress[slot], passSuperseded)) {
                winner = order.preferred(winner, slot, passPress);
            }
        }
        Arrays.fill(passAllowance, UNLIMITED);
        if (order == HotbarOrder.HERZIUM && options.splitBursts()) {
            // A click whose item (the last key pressed before it) is not the one
            // this tick leaves in hand waits for the next tick, and every click
            // pressed after it waits too, so pressed order is kept across ticks.
            long wait = firstMismatch(options, winner >= 0 ? winner : currentSlot, currentSlot);
            if (wait != 0L) {
                for (int type = USE; type < TYPES; type++) {
                    passAllowance[type] = countBefore(type, wait, counts[type]);
                }
            }
        }
    }

    /**
     * The serial of the first checked click whose intended item differs from
     * the slot this pass holds, or 0. The intended item of a click is the last
     * key pressed before it, or the slot held before the burst. Uses are always
     * checked; attacks with strict actions; swaps and drops with offhand sync.
     * Keys at or before the press behind the current selection are stale and
     * never the intended item.
     */
    private long firstMismatch(Options options, int held, int currentSlot) {
        List<long[]> events = new ArrayList<>();
        for (int type = 0; type < TYPES; type++) {
            for (long serial : pending[type]) events.add(new long[] {serial, type});
        }
        events.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
        int intended = currentSlot;
        for (long[] event : events) {
            int type = (int) event[1];
            if (type < 9) {
                if (passSuperseded == 0L || event[0] > passSuperseded || type == currentSlot) intended = type;
                continue;
            }
            boolean checked = type == USE
                    || (type == ATTACK && options.strictActions())
                    || ((type == SWAP || type == DROP) && options.offhandSync());
            if (checked && intended != held) return event[0];
        }
        return 0L;
    }

    /**
     * Whether this pass must leave the slot's click queued. True only under the
     * Herzium order, for a key whose oldest queued click was pressed after the
     * click that bounds the pass: that key belongs to the next tick.
     */
    public synchronized boolean defers(int slot) {
        return slot >= 0 && slot < 9 && passOrder == HotbarOrder.HERZIUM && passDeferred[slot];
    }

    /** Called after Vanilla really consumed one click of the slot. */
    public synchronized void consumed(int slot, int remainingClicks) {
        if (slot >= 0 && slot < 9) sync(slot, remainingClicks);
    }

    /** Whether the pass may take one more click of this action type now. */
    public synchronized boolean allowsAction(int type) {
        return type < USE || type >= TYPES || passAllowance[type] > 0;
    }

    /** Called after Vanilla really consumed one click of the action type. */
    public synchronized void actionConsumed(int type, int remainingClicks) {
        if (type < USE || type >= TYPES) return;
        if (passAllowance[type] != UNLIMITED && passAllowance[type] > 0) passAllowance[type]--;
        sync(type, remainingClicks);
    }

    /** Called only for slots whose real Vanilla consumeClick succeeded. */
    public synchronized boolean acceptSelection(int slot) {
        if (slot < 0 || slot >= 9 || passOrder == HotbarOrder.VANILLA) return true;
        if (stale(slot, passCurrentSlot, passPress[slot], passSuperseded)) return false;
        int winner = passOrder.preferred(acceptedSlot, slot, passPress);
        acceptedSlot = winner;
        if (winner != slot) return false;
        if (passOrder.lastPressWins()) {
            // An older click that selects the current slot again does not make the selection
            // older: after the wheel, the wheel is still the newest input behind it.
            if (slot != selectionSlot || passPress[slot] > selectionPress) selectionPress = passPress[slot];
            selectionSlot = slot;
        }
        return true;
    }

    /**
     * The mouse wheel selected a slot, right now. Under a last-press order it is the newest input:
     * a hotbar key still queued from before it would select its slot again on the next tick and
     * undo the wheel, so from here on such a key counts as stale, like one pressed before the
     * last key that won.
     */
    public synchronized void noteWheel(int slot) {
        selectionSlot = slot;
        selectionPress = nextSerial();
    }

    public synchronized void reset() {
        for (ArrayDeque<Long> clicks : pending) clicks.clear();
        Arrays.fill(passPress, 0L);
        Arrays.fill(passDeferred, false);
        Arrays.fill(passAllowance, UNLIMITED);
        sequence = 0L;
        acceptedSlot = -1;
        passOrder = HotbarOrder.VANILLA;
        passOpen = false;
        passCurrentSlot = -1;
        passSuperseded = 0L;
        selectionSlot = -1;
        selectionPress = 0L;
    }

    /**
     * The click that bounds the pass: the first Use (and, with the options, the
     * first swap, drop or Attack); a lone Attack when nothing else is pending.
     * Vanilla attacks before it uses within a tick, so without strict actions
     * "attack, key, use" is the key choosing the item for the Use, which is how
     * Vanilla already plays it.
     */
    private long boundary(Options options) {
        long primary = oldest(USE);
        if (options.offhandSync()) {
            primary = earliest(primary, oldest(SWAP));
            primary = earliest(primary, oldest(DROP));
        }
        if (options.strictActions()) primary = earliest(primary, oldest(ATTACK));
        return primary != 0L ? primary : oldest(ATTACK);
    }

    private void track(int type, long serial) {
        pending[type].addLast(serial);
        if (pending[type].size() > TRACKED_CLICKS) pending[type].removeFirst();
    }

    private long oldest(int type) {
        Long serial = pending[type].peekFirst();
        return serial == null ? 0L : serial;
    }

    /** Pending clicks of the type pressed before the serial; unseen ones count as older. */
    private int countBefore(int type, long serial, int clickCount) {
        int before = Math.max(0, clickCount - pending[type].size());
        for (long pressed : pending[type]) {
            if (pressed < serial) before++;
        }
        return before;
    }

    private static long earliest(long a, long b) {
        if (a == 0L) return b;
        if (b == 0L) return a;
        return Math.min(a, b);
    }

    private long nextSerial() {
        if (sequence == Long.MAX_VALUE) reset();
        return ++sequence;
    }

    private void syncAll(int[] counts) {
        for (int type = 0; type < TYPES; type++) sync(type, counts[type]);
    }

    /**
     * Keeps the tracked serials in step with Vanilla's counter. Clicks consumed
     * by someone else are the oldest ones; clicks Herzium never saw pressed are
     * treated as older than every tracked one.
     */
    private void sync(int type, int clickCount) {
        ArrayDeque<Long> clicks = pending[type];
        while (clicks.size() > Math.max(0, clickCount)) clicks.removeFirst();
    }

    /** 13 counters from the nine hotbar ones, the action clicks as Herzium saw them. */
    private int[] legacyCounts(int[] slots) {
        int[] counts = new int[TYPES];
        System.arraycopy(slots, 0, counts, 0, Math.min(9, slots.length));
        for (int type = USE; type < TYPES; type++) counts[type] = pending[type].size();
        return counts;
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
     * outside the last-press orders and whenever the selection has since moved
     * without them -- the wheel, the server or another mod. Those keep plain
     * last-press ordering among the queued slots.
     */
    private long superseded(HotbarOrder order, int currentSlot) {
        return order.lastPressWins() && currentSlot >= 0 && currentSlot == selectionSlot
                ? selectionPress : 0L;
    }

    /** Re-selecting the current slot changes nothing, so it is never stale. */
    private static boolean stale(int slot, int currentSlot, long press, long superseded) {
        return superseded > 0L && slot != currentSlot && press <= superseded;
    }
}
