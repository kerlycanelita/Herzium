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
    private static int optionBursts;
    /** The three orders of 1.10.7, which the first oracle models. */
    private static final HotbarOrder[] LEGACY_ORDERS = {HotbarOrder.VANILLA, HotbarOrder.HERZIUM, HotbarOrder.VANILLA_REVERSED};

    public static void main(String[] args) {
        for (HotbarOrder order : LEGACY_ORDERS) {
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
            for (HotbarOrder order : LEGACY_ORDERS) { verifyBurst(order, events); random++; }
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
        for (int w = 48; w <= 1920; w += w < 480 ? 8 : 40) {
            for (int h = 48; h <= 1080; h += h < 360 ? 6 : 40) {
                for (int labelWidth : new int[] {40, 110, 150, 320}) {
                    HerziumConfigLayout l = HerziumConfigLayout.fit(w, h, labelWidth);
                    check(true, l.x() >= 0 && l.y() >= 0 && l.x() + l.width() <= w && l.y() + l.height() <= h, "panel bounds");
                    check(true, l.modeY() >= l.y() && l.doneY() + l.buttonHeight() <= l.y() + l.height(), "button bounds");
                    check(true, l.modeY() + l.buttonHeight() <= l.doneY(), "button overlap");
                    int[] previous = null;
                    for (int option = 0; option < HerziumConfigLayout.OPTIONS; option++) {
                        int[] b = l.option(option);
                        check(true, b[2] > 0 && b[3] > 0, "option has a size " + w + "x" + h);
                        check(true, b[0] >= l.x() && b[0] + b[2] <= l.x() + l.width(), "option inside the panel " + w + "x" + h);
                        check(true, b[1] >= l.modeY() + l.buttonHeight(), "option below the order " + w + "x" + h);
                        check(true, b[1] + b[3] <= l.doneY(), "option above Done " + w + "x" + h);
                        if (previous != null) {
                            boolean apart = previous[0] + previous[2] <= b[0] || previous[1] + previous[3] <= b[1];
                            check(true, apart, "options do not overlap " + w + "x" + h);
                        }
                        previous = b;
                        check(true, l.textTop() >= b[1] + b[3], "explanation below the options " + w + "x" + h);
                    }
                    check(true, l.textBottom() >= l.textTop() && l.textBottom() <= l.doneY(), "explanation above Done");
                }
            }
        }
        verifyOptionSuites();
        System.out.printf("PASS: %d policy/layout assertions; %,d exhaustive and %,d random bursts;"
                + " %,d option bursts.%n", checks, exhaustive, random, optionBursts);
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

    // ------------------------------------------------------------------
    // Burst options (split, strict, offhand sync).
    // Event codes: 0-8 keys, 9 use, 10 attack, 11 swap, 12 drop.
    // ------------------------------------------------------------------

    private static final HotbarOrderPolicy.Options SPLIT = new HotbarOrderPolicy.Options(true, false, true);
    private static final HotbarOrderPolicy.Options STRICT = new HotbarOrderPolicy.Options(true, true, true);
    private static final HotbarOrderPolicy.Options SPLIT_NO_SYNC = new HotbarOrderPolicy.Options(true, false, false);
    private static final int SWAP = HotbarOrderPolicy.SWAP;
    private static final int DROP = HotbarOrderPolicy.DROP;
    /** Test-only codes 20-28: the mouse wheel selects slot (code - 20). */
    private static final int WHEEL = 20;

    private static void verifyOptionSuites() {
        // Named cases first: each is a burst from the laboratory.
        int[] obsidianCrystal = {2, USE, 5, USE};
        check(List.of("9@2", "9@5"), actionsOf(HotbarOrder.HERZIUM, SPLIT, obsidianCrystal),
                "obsidian, use, crystal, use: each use gets its own key");
        check(2, passesOf(HotbarOrder.HERZIUM, SPLIT, obsidianCrystal), "split: the crystal goes on the next tick");
        check(List.of("9@2", "9@2"), actionsOf(HotbarOrder.HERZIUM, HotbarOrderPolicy.Options.LEGACY, obsidianCrystal),
                "1.10.7: the second use kept the obsidian");
        int[] sameKeyTwice = {2, USE, 2, USE};
        check(2, simulate(HotbarOrder.HERZIUM, SPLIT, sameKeyTwice).firstPassActions(),
                "the same key twice does not wait");
        int[] leftover = {6, 2, USE, 6, USE};
        check(List.of("9@2", "9@6"), actionsOf(HotbarOrder.HERZIUM, SPLIT, leftover),
                "a key queued before the use and pressed again after it");
        int[] attackKeyUse = {ATTACK, 2, USE};
        check(List.of("10@4", "9@2"), actionsOf(HotbarOrder.HERZIUM, STRICT, attackKeyUse),
                "strict: attack keeps the held item, the use gets the key");
        check(2, passesOf(HotbarOrder.HERZIUM, STRICT, attackKeyUse), "strict: the key and the use go on the next tick");
        check(List.of("10@2", "9@2"), actionsOf(HotbarOrder.HERZIUM, SPLIT, attackKeyUse),
                "split: attack, key, use in one tick as in Vanilla");
        check(1, passesOf(HotbarOrder.HERZIUM, SPLIT, attackKeyUse), "split: attack, key, use is one tick");
        int[] swordHitCrystal = {0, ATTACK, 6, USE};
        check(List.of("10@0", "9@6"), actionsOf(HotbarOrder.HERZIUM, STRICT, swordHitCrystal),
                "strict: the hit is the sword's, the crystal goes next tick");
        int[] totemSwapSword = {4, SWAP, 0};
        check(List.of("11@4"), actionsOf(HotbarOrder.HERZIUM, SPLIT, totemSwapSword),
                "offhand sync: the swap takes the totem, the sword key waits");
        check(0, finalSlotOf(HotbarOrder.HERZIUM, SPLIT, totemSwapSword), "the sword is selected afterwards");
        check(List.of("11@0"), actionsOf(HotbarOrder.HERZIUM, SPLIT_NO_SYNC, totemSwapSword),
                "without offhand sync the last key wins the swap too");

        // The wheel is the newest input: a key queued before it cannot undo it on the next tick.
        check(5, finalSlotOf(HotbarOrder.HERZIUM, SPLIT, new int[] {2, WHEEL + 5}), "key, then wheel: the wheel stays");
        check(7, finalSlotOf(HotbarOrder.HERZIUM, SPLIT, new int[] {2, WHEEL + 5, 7}), "key, wheel, key: the last key");
        check(2, finalSlotOf(HotbarOrder.HERZIUM, SPLIT, new int[] {WHEEL + 5, 2}), "wheel, then key: the key");
        int[] wheelAlphabet = {0, 4, 8, USE, WHEEL, WHEEL + 4, WHEEL + 8};
        for (int encoded = 0; encoded < 7 * 7 * 7 * 7 * 7; encoded++) {
            int[] raw = decode(encoded, 5, 7);
            int[] events = new int[raw.length];
            for (int i = 0; i < raw.length; i++) events[i] = wheelAlphabet[raw[i]];
            for (HotbarOrderPolicy.Options option : new HotbarOrderPolicy.Options[] {SPLIT, STRICT,
                    HotbarOrderPolicy.Options.LEGACY}) {
                verifyOptions(HotbarOrder.HERZIUM, option, events);
            }
        }

        HotbarOrder[] orders = {HotbarOrder.HERZIUM};
        HotbarOrderPolicy.Options[] options = {SPLIT, STRICT, SPLIT_NO_SYNC, HotbarOrderPolicy.Options.LEGACY};
        // Exhaustive: every burst of four events over all thirteen codes.
        for (int encoded = 0; encoded < 13 * 13 * 13 * 13; encoded++) {
            int[] events = decode(encoded, 4, 13);
            for (HotbarOrder order : orders) {
                for (HotbarOrderPolicy.Options option : options) verifyOptions(order, option, events);
            }
            verifyLegacyEquivalence(events);
        }
        // Exhaustive and deeper: six events over three keys, use, attack and swap.
        int[] alphabet = {0, 4, 8, USE, ATTACK, SWAP};
        for (int encoded = 0; encoded < 6 * 6 * 6 * 6 * 6 * 6; encoded++) {
            int[] raw = decode(encoded, 6, 6);
            int[] events = new int[raw.length];
            for (int i = 0; i < raw.length; i++) events[i] = alphabet[raw[i]];
            for (HotbarOrder order : orders) {
                for (HotbarOrderPolicy.Options option : options) verifyOptions(order, option, events);
            }
            verifyLegacyEquivalence(events);
        }
        Random rng = new Random(1107);
        for (int i = 0; i < 20_000; i++) {
            int[] events = new int[1 + rng.nextInt(40)];
            for (int e = 0; e < events.length; e++) {
                int roll = rng.nextInt(20);
                events[e] = roll < 4 ? USE : roll < 6 ? ATTACK : roll == 6 ? SWAP : roll == 7 ? DROP : rng.nextInt(9);
            }
            for (HotbarOrder order : orders) {
                for (HotbarOrderPolicy.Options option : options) verifyOptions(order, option, events);
            }
            verifyLegacyEquivalence(events);
        }
    }

    private static int[] decode(int encoded, int length, int base) {
        int[] events = new int[length];
        for (int i = 0; i < length; i++) { events[i] = encoded % base; encoded /= base; }
        return events;
    }

    /** What one burst did: each action as "type@slot" in the order it ran, and how many passes it took. */
    private record Outcome(List<String> actions, int passes, int finalSlot, boolean changedTwiceInAPass,
            int firstPassActions) { }

    private static List<String> actionsOf(HotbarOrder order, HotbarOrderPolicy.Options options, int[] events) {
        return simulate(order, options, events).actions();
    }

    private static int passesOf(HotbarOrder order, HotbarOrderPolicy.Options options, int[] events) {
        return simulate(order, options, events).passes();
    }

    private static int finalSlotOf(HotbarOrder order, HotbarOrderPolicy.Options options, int[] events) {
        return simulate(order, options, events).finalSlot();
    }

    /**
     * Every event arrives before the first pass, as in one very fast frame; then
     * passes run as handleKeybinds does -- the hotbar loop, then swap, drop,
     * attack and use -- until every click is consumed.
     */
    private static Outcome simulate(HotbarOrder order, HotbarOrderPolicy.Options options, int[] events) {
        HotbarOrderPolicy policy = new HotbarOrderPolicy();
        int[] counts = new int[HotbarOrderPolicy.TYPES];
        int current = 4;
        for (int event : events) {
            if (event >= WHEEL) {
                // The wheel selects at once, between ticks, as Vanilla's scroll handler does.
                current = event - WHEEL;
                policy.noteWheel(current);
                continue;
            }
            policy.recordEvent(1 << event);
            counts[event]++;
        }
        List<String> actions = new ArrayList<>();
        int passes = 0;
        boolean changedTwice = false;
        int firstPassActions = -1;
        int predicted = -1;
        while (Arrays.stream(counts).sum() > 0) {
            if (++passes > events.length + 2) throw new AssertionError("no progress: " + Arrays.toString(events));
            int changes = 0;
            policy.beginPass(order, options, current, counts.clone());
            int before = current;
            for (int slot = 0; slot < 9; slot++) {
                if (counts[slot] == 0 || policy.defers(slot)) continue;
                counts[slot]--;
                policy.consumed(slot, counts[slot]);
                if (policy.acceptSelection(slot)) current = slot;
            }
            if (current != before) changes++;
            // The preview Herzium caches when it seals this pass is its bet on the next one; the
            // HUD shows it until then, and a wrong bet suspends the preview for the world.
            if (predicted >= 0) {
                check(predicted, current, "the sealed preview is the slot the next pass selects " + order + " "
                        + options + " " + Arrays.toString(events));
            }
            predicted = policy.preview(counts.clone(), order, options, current);
            for (int type : new int[] {SWAP, DROP, ATTACK, USE}) {
                while (counts[type] > 0 && policy.allowsAction(type)) {
                    counts[type]--;
                    policy.actionConsumed(type, counts[type]);
                    actions.add(type + "@" + current);
                }
            }
            policy.endPass();
            if (order == HotbarOrder.HERZIUM && changes > 1) changedTwice = true;
            if (firstPassActions < 0) firstPassActions = actions.size();
        }
        return new Outcome(actions, passes, current, changedTwice, firstPassActions);
    }

    /** Each checked click runs with the last key pressed before it; the Herzium order changes slot once per tick. */
    private static void verifyOptions(HotbarOrder order, HotbarOrderPolicy.Options options, int[] events) {
        optionBursts++;
        Outcome outcome = simulate(order, options, events);
        check(false, outcome.changedTwiceInAPass(), "Herzium order changes the slot at most once per tick");
        int lastKey = -1;
        int total = 0;
        boolean hasWheel = false;
        for (int event : events) {
            if (event >= WHEEL) {
                lastKey = event - WHEEL;
                hasWheel = true;
            } else if (event < 9) {
                lastKey = event;
            } else {
                total++;
            }
        }
        if (lastKey >= 0) check(lastKey, outcome.finalSlot(), "the last input stays selected " + order + " " + options + " " + Arrays.toString(events));
        check(total, outcome.actions().size(), "every click runs exactly once");
        if (!options.splitBursts()) return;
        // The wheel selects at once: a click queued before it runs with the wheel's slot, in
        // Vanilla too. Only the final selection is checked for bursts with a wheel.
        if (hasWheel) return;
        List<List<String>> expected = new ArrayList<>();
        List<List<String>> actual = new ArrayList<>();
        for (int type = 0; type < HotbarOrderPolicy.TYPES; type++) {
            expected.add(new ArrayList<>());
            actual.add(new ArrayList<>());
        }
        int held = 4;
        for (int event : events) {
            if (event < 9) held = event;
            else expected.get(event).add(event + "@" + held);
        }
        for (String action : outcome.actions()) {
            actual.get(Integer.parseInt(action.substring(0, action.indexOf('@')))).add(action);
        }
        for (int type = USE; type < HotbarOrderPolicy.TYPES; type++) {
            boolean covered = type == USE
                    || (type == ATTACK && options.strictActions())
                    || ((type == SWAP || type == DROP) && options.offhandSync());
            if (covered) {
                check(expected.get(type), actual.get(type),
                        order + " " + options + " click type " + type + " " + Arrays.toString(events));
            }
        }
    }

    /** Options all off must be Herzium 1.10.7: the same decisions on every pass as the 1.10.7 entry points. */
    private static void verifyLegacyEquivalence(int[] events) {
        HotbarOrderPolicy legacy = new HotbarOrderPolicy();
        HotbarOrderPolicy modern = new HotbarOrderPolicy();
        int[] slotCounts = new int[9];
        int[] counts = new int[HotbarOrderPolicy.TYPES];
        for (int event : events) {
            // 1.10.7 saw no swap or drop events: they never bounded anything.
            if (event < 9) {
                legacy.recordPress(1 << event);
                slotCounts[event]++;
            } else if (event == USE) {
                legacy.recordUse();
            } else if (event == ATTACK) {
                legacy.recordAttack();
            }
            modern.recordEvent(1 << event);
            counts[event]++;
        }
        int currentLegacy = 4;
        int currentModern = 4;
        boolean first = true;
        while (first || Arrays.stream(slotCounts).sum() > 0) {
            first = false;
            legacy.beginPass(HotbarOrder.HERZIUM, currentLegacy, slotCounts.clone());
            modern.beginPass(HotbarOrder.HERZIUM, HotbarOrderPolicy.Options.LEGACY, currentModern, counts.clone());
            for (int slot = 0; slot < 9; slot++) {
                if (slotCounts[slot] == 0) continue;
                check(legacy.defers(slot), modern.defers(slot), "legacy deferral " + Arrays.toString(events));
                if (legacy.defers(slot)) continue;
                slotCounts[slot]--;
                counts[slot]--;
                legacy.consumed(slot, slotCounts[slot]);
                modern.consumed(slot, counts[slot]);
                boolean a = legacy.acceptSelection(slot);
                boolean b = modern.acceptSelection(slot);
                check(a, b, "legacy selection " + Arrays.toString(events));
                if (a) currentLegacy = slot;
                if (b) currentModern = slot;
            }
            for (int type = USE; type < HotbarOrderPolicy.TYPES; type++) {
                while (counts[type] > 0 && modern.allowsAction(type)) {
                    counts[type]--;
                    modern.actionConsumed(type, counts[type]);
                }
                check(0, counts[type], "legacy options drain every click");
            }
        }
        check(currentLegacy, currentModern, "legacy final slot " + Arrays.toString(events));
    }

    private static void check(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
