import dev.zymekoh.herzium.input.HotbarOrder;
import dev.zymekoh.herzium.input.HotbarOrderPolicy;
import dev.zymekoh.herzium.gui.HerziumConfigLayout;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Production policy tests; no Minecraft client, server or input automation. */
public final class HotbarOrderTest {
    /** Use and Attack clicks in a burst; 0-8 are hotbar keys. */
    private static final int USE = 9;
    private static final int ATTACK = 10;
    private static int checks;
    private static int exhaustive;
    private static int random;

    public static void main(String[] args) {
        for (HotbarOrder order : HotbarOrder.values()) {
            // Every sequence of four events: keys, repeats, ties, uses and attacks.
            for (int encoded = 0; encoded < 14_641; encoded++) {
                int value = encoded;
                int[] events = new int[4];
                for (int i = 0; i < events.length; i++) { events[i] = value % 11; value /= 11; }
                verifyBurst(order, events);
                exhaustive++;
            }
        }
        Random rng = new Random(2612);
        for (int i = 0; i < 5000; i++) {
            int[] events = new int[1 + rng.nextInt(80)];
            for (int e = 0; e < events.length; e++) {
                int roll = rng.nextInt(14);
                events[e] = roll == 0 ? USE : roll == 1 ? ATTACK : rng.nextInt(9);
            }
            for (HotbarOrder order : HotbarOrder.values()) { verifyBurst(order, events); random++; }
        }

        HotbarOrderPolicy policy = new HotbarOrderPolicy();
        policy.recordPress((1 << 0) | (1 << 8));
        check(8, policy.preview(counts(0, 8), HotbarOrder.HERZIUM, -1), "duplicate binding deterministic tie");
        check(0, policy.preview(counts(0, 8), HotbarOrder.VANILLA_REVERSED, -1), "duplicate binding reverse");

        policy.reset(); policy.recordPress(1 << 8); policy.recordPress(1 << 0);
        int[] frozen = counts(0, 8);
        policy.beginPass(HotbarOrder.HERZIUM, 4, frozen.clone());
        policy.recordPress(1 << 8); // Later events cannot alter a pass already underway.
        policy.consumed(0, 0);
        check(true, policy.acceptSelection(0), "frozen pass first winner");
        policy.consumed(8, 1);
        check(false, policy.acceptSelection(8), "frozen pass ignores late event");
        check(8, policy.preview(counts(8), HotbarOrder.HERZIUM, 0), "next generation receives late event");
        policy.reset();
        check(8, policy.preview(counts(0, 8), HotbarOrder.HERZIUM, -1), "unknown event order uses Vanilla tie");
        check(-1, policy.preview(new int[9], HotbarOrder.HERZIUM, -1), "empty queue has no invented input");

        // A key tapped twice inside one tick, or held until it auto-repeats, still
        // has clicks queued after a newer key wins. They must not undo that key.
        policy.reset();
        policy.recordPress(1 << 0); policy.recordPress(1 << 0); policy.recordPress(1 << 8);
        check(8, pass(policy, HotbarOrder.HERZIUM, 4, counts(0, 0, 8)), "queued pass: newer key wins");
        check(-1, policy.preview(counts(0), HotbarOrder.HERZIUM, 8), "stale queued click previews nothing");
        check(0, policy.preview(counts(0), HotbarOrder.HERZIUM, 5), "selection moved without Herzium: plain ordering");
        check(-1, pass(policy, HotbarOrder.HERZIUM, 8, counts(0)), "stale queued click cannot undo the newer key");

        // Vanilla resolves every key of a tick before its clicks. Under the
        // Herzium order a click uses the key pressed before it; a key pressed
        // after it waits, unconsumed, for the next pass.
        policy.reset();
        policy.recordPress(1 << 3); policy.recordUse(); policy.recordPress(1 << 1);
        check(3, policy.preview(counts(3, 1), HotbarOrder.HERZIUM, 0), "click keeps the key pressed before it");
        check(3, pass(policy, HotbarOrder.HERZIUM, 0, counts(3, 1)), "nexus, use, glowstone: the use gets the nexus");
        check(1, policy.preview(counts(1), HotbarOrder.HERZIUM, 3), "the later key is next");
        check(1, pass(policy, HotbarOrder.HERZIUM, 3, counts(1)), "the later key applies on the next pass");
        policy.reset();
        policy.recordUse(); policy.recordPress(1 << 5);
        check(-1, pass(policy, HotbarOrder.HERZIUM, 2, counts(5)), "use, then key: the use keeps the held item");
        check(5, pass(policy, HotbarOrder.HERZIUM, 2, counts(5)), "use, then key: the key follows");
        policy.reset();
        policy.recordPress(1 << 3); policy.recordUse(); policy.recordPress(1 << 1);
        check(3, pass(policy, HotbarOrder.VANILLA, 0, counts(3, 1)), "Vanilla order ignores clicks");
        policy.reset();
        policy.recordPress(1 << 3); policy.recordUse(); policy.recordPress(1 << 3);
        check(3, pass(policy, HotbarOrder.HERZIUM, 0, counts(3, 3)), "same key before and after a click");
        check(3, pass(policy, HotbarOrder.HERZIUM, 3, counts(3)), "its later click only re-selects it");
        // Crystal PvP: break the crystal, switch to obsidian, place it. Vanilla
        // attacks before it uses, so the key belongs to the Use.
        policy.reset();
        policy.recordAttack(); policy.recordPress(1 << 2); policy.recordUse();
        check(2, pass(policy, HotbarOrder.HERZIUM, 5, counts(2)), "attack, obsidian, use: the use places obsidian");
        policy.reset();
        policy.recordPress(1 << 1); policy.recordAttack(); policy.recordPress(1 << 5);
        check(1, pass(policy, HotbarOrder.HERZIUM, 0, counts(1, 5)), "attack alone keeps the key pressed before it");
        policy.reset();
        policy.recordPress(1 << 2); policy.recordUse(); policy.recordPress(1 << 5); policy.recordUse();
        check(2, pass(policy, HotbarOrder.HERZIUM, 0, counts(2, 5)), "obsidian, use, crystal, use: one slot per tick");
        check(5, pass(policy, HotbarOrder.HERZIUM, 2, counts(5)), "the crystal key follows on the next tick");

        // The same policy sees slot masks regardless of mouse, keysym or scancode.
        for (String binding : new String[] {"mouse.left", "mouse.right", "mouse.side", "keysym", "scancode"}) {
            policy.reset(); policy.recordPress(1 << 8); policy.recordPress(1 << 0);
            check(0, policy.preview(counts(0, 8), HotbarOrder.HERZIUM, -1), binding);
        }
        for (int w : new int[] {64, 160, 240, 320, 427, 480, 640, 960, 1920}) {
            for (int h : new int[] {64, 90, 135, 180, 240, 270, 360, 540, 1080}) {
                HerziumConfigLayout l = HerziumConfigLayout.fit(w, h);
                check(true, l.x() >= 0 && l.y() >= 0 && l.x() + l.width() <= w && l.y() + l.height() <= h, "panel bounds");
                check(true, l.modeY() >= l.y() && l.doneY() + l.buttonHeight() <= l.y() + l.height(), "button bounds");
                check(true, l.modeY() + l.buttonHeight() <= l.doneY(), "button overlap");
            }
        }
        System.out.printf("PASS: %d policy/layout assertions; %,d exhaustive and %,d random bursts.%n",
                checks, exhaustive, random);
    }

    /** One Vanilla keybind pass as handleKeybinds runs it; returns the slot it selected, or -1. */
    private static int pass(HotbarOrderPolicy policy, HotbarOrder order, int current, int[] clicks) {
        policy.beginPass(order, current, clicks.clone());
        int committed = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (clicks[slot] == 0 || policy.defers(slot)) continue;
            policy.consumed(slot, --clicks[slot]);
            if (policy.acceptSelection(slot)) committed = slot;
        }
        return committed;
    }

    private static int[] counts(int... slots) {
        int[] counts = new int[9];
        for (int slot : slots) counts[slot]++;
        return counts;
    }

    /** Every event arrives before the first pass, as in one very fast frame. */
    private static void verifyBurst(HotbarOrder order, int[] events) {
        HotbarOrderPolicy policy = new HotbarOrderPolicy();
        List<ArrayDeque<Integer>> queued = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) queued.add(new ArrayDeque<>());
        int[] counts = new int[9];
        int firstUse = 0;
        int firstAttack = 0;
        int keyBeforeUse = -1;
        int keyBeforeAttack = -1;
        int lastKey = -1;
        for (int i = 0; i < events.length; i++) {
            int serial = i + 1;
            if (events[i] == USE) {
                policy.recordUse();
                if (firstUse == 0) firstUse = serial;
                continue;
            }
            if (events[i] == ATTACK) {
                policy.recordAttack();
                if (firstAttack == 0) firstAttack = serial;
                continue;
            }
            policy.recordPress(1 << events[i]);
            counts[events[i]]++;
            queued.get(events[i]).addLast(serial);
            lastKey = events[i];
            if (firstUse == 0) keyBeforeUse = events[i];
            if (firstAttack == 0) keyBeforeAttack = events[i];
        }
        int current = 4;
        // Oracle state for the Herzium order: the press behind the current slot.
        int selectionSlot = -1;
        int selectionPress = 0;
        boolean firstPass = true;
        while (firstPass || Arrays.stream(counts).sum() > 0) {
            // Independent oracle. Every click is consumed by the first pass, so
            // only that pass has a boundary: the first Use, else the first Attack.
            int firstAction = firstUse != 0 ? firstUse : firstAttack;
            int keyBeforeAction = firstUse != 0 ? keyBeforeUse : keyBeforeAttack;
            int boundary = firstPass && order == HotbarOrder.HERZIUM ? firstAction : 0;
            int superseded = order == HotbarOrder.HERZIUM && current == selectionSlot ? selectionPress : 0;
            boolean[] deferred = new boolean[9];
            int[] press = new int[9];
            int expected = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (counts[slot] == 0) continue;
                ArrayDeque<Integer> clicks = queued.get(slot);
                deferred[slot] = boundary > 0 && clicks.peekFirst() > boundary;
                if (deferred[slot]) continue;
                for (int serial : clicks) if (boundary == 0 || serial < boundary) press[slot] = Math.max(press[slot], serial);
                if (order == HotbarOrder.VANILLA) {
                    expected = slot;
                } else if (order == HotbarOrder.VANILLA_REVERSED) {
                    if (expected < 0) expected = slot;
                } else if (!(superseded > 0 && slot != current && press[slot] <= superseded)
                        && (expected < 0 || press[slot] >= press[expected])) {
                    expected = slot;
                }
            }
            check(expected, policy.preview(counts.clone(), order, current), "preview matches independent policy");
            policy.beginPass(order, current, counts.clone());
            int committed = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (counts[slot] == 0) continue;
                check(deferred[slot], policy.defers(slot), "keys pressed after a click wait for the next pass");
                if (deferred[slot]) continue;
                counts[slot]--; // Vanilla consumes exactly once per mapping.
                queued.get(slot).removeFirst();
                policy.consumed(slot, counts[slot]);
                boolean accept = policy.acceptSelection(slot);
                if (order == HotbarOrder.VANILLA) check(true, accept, "Vanilla setter passthrough");
                if (accept) committed = slot;
            }
            check(expected, committed, "actual consumed winner matches preview");
            if (firstPass && firstAction > 0 && order == HotbarOrder.HERZIUM) {
                check(keyBeforeAction >= 0 ? keyBeforeAction : current, committed >= 0 ? committed : current,
                        firstUse != 0 ? "the first use gets the key pressed before it"
                                : "an attack alone gets the key pressed before it");
            }
            if (committed >= 0) {
                current = committed;
                if (order == HotbarOrder.HERZIUM) {
                    selectionSlot = committed;
                    selectionPress = press[committed];
                }
            }
            firstPass = false;
        }
        if (order == HotbarOrder.HERZIUM && lastKey >= 0) {
            check(lastKey, current, "queued clicks never undo the last input");
        }
    }

    private static void check(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
