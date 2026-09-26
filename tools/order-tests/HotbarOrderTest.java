import dev.zymekoh.herzium.input.HotbarOrder;
import dev.zymekoh.herzium.input.HotbarOrderPolicy;
import dev.zymekoh.herzium.gui.HerziumConfigLayout;
import java.util.Arrays;
import java.util.Random;

/** Production policy tests; no Minecraft client, server or input automation. */
public final class HotbarOrderTest {
    private static int checks;
    public static void main(String[] args) {
        for (HotbarOrder order : HotbarOrder.values()) {
            // Every sequence of four presses, including repetitions and ties.
            for (int encoded = 0; encoded < 6561; encoded++) {
                int value = encoded;
                int[] keys = new int[4];
                for (int i = 0; i < keys.length; i++) { keys[i] = value % 9; value /= 9; }
                verifyBurst(order, keys);
            }
        }
        Random random = new Random(2612);
        for (int i = 0; i < 5000; i++) {
            int[] keys = random.ints(1 + random.nextInt(80), 0, 9).toArray();
            for (HotbarOrder order : HotbarOrder.values()) verifyBurst(order, keys);
        }

        HotbarOrderPolicy policy = new HotbarOrderPolicy();
        policy.recordPress((1 << 0) | (1 << 8));
        check(8, policy.preview(257, HotbarOrder.HERZIUM, -1), "duplicate binding deterministic tie");
        check(0, policy.preview(257, HotbarOrder.VANILLA_REVERSED, -1), "duplicate binding reverse");
        policy.reset(); policy.recordPress(1 << 8); policy.recordPress(1 << 0);
        policy.beginPass(HotbarOrder.HERZIUM, 4);
        policy.recordPress(1 << 8); // Later events cannot alter a pass already underway.
        check(true, policy.acceptSelection(0), "frozen pass first winner");
        check(false, policy.acceptSelection(8), "frozen pass ignores late event");
        check(8, policy.preview(257, HotbarOrder.HERZIUM, 0), "next generation receives late event");
        policy.reset(); check(8, policy.preview(257, HotbarOrder.HERZIUM, -1), "unknown event order uses Vanilla tie");
        check(-1, policy.preview(0, HotbarOrder.HERZIUM, -1), "empty queue has no invented input");

        // A key tapped twice inside one tick, or held until it auto-repeats, still
        // has clicks queued after a newer key wins. They must not undo that key.
        policy.reset();
        policy.recordPress(1 << 0); policy.recordPress(1 << 0); policy.recordPress(1 << 8);
        policy.beginPass(HotbarOrder.HERZIUM, 4);
        check(true, policy.acceptSelection(0), "queued pass: older key consumed first");
        check(true, policy.acceptSelection(8), "queued pass: newer key wins");
        check(-1, policy.preview(1, HotbarOrder.HERZIUM, 8), "stale queued click previews nothing");
        policy.beginPass(HotbarOrder.HERZIUM, 8);
        check(false, policy.acceptSelection(0), "stale queued click cannot undo the newer key");
        policy.beginPass(HotbarOrder.HERZIUM, 5);
        check(true, policy.acceptSelection(0), "selection moved without Herzium: plain ordering again");
        policy.reset();
        policy.recordPress(1 << 0); policy.recordPress(1 << 8);
        policy.beginPass(HotbarOrder.VANILLA_REVERSED, 4);
        check(true, policy.acceptSelection(0), "reversed keeps the lowest slot");
        policy.beginPass(HotbarOrder.VANILLA_REVERSED, 0);
        check(true, policy.acceptSelection(8), "reversed has no stale guard");

        // The same policy sees slot masks regardless of mouse, keysym or scancode.
        for (String binding : new String[] {"mouse.left", "mouse.right", "mouse.side", "keysym", "scancode"}) {
            policy.reset(); policy.recordPress(1 << 8); policy.recordPress(1 << 0);
            check(0, policy.preview(257, HotbarOrder.HERZIUM, -1), binding);
        }
        for (int w : new int[] {64, 160, 240, 320, 427, 480, 640, 960, 1920}) {
            for (int h : new int[] {64, 90, 135, 180, 240, 270, 360, 540, 1080}) {
                HerziumConfigLayout l = HerziumConfigLayout.fit(w, h);
                check(true, l.x() >= 0 && l.y() >= 0 && l.x() + l.width() <= w && l.y() + l.height() <= h, "panel bounds");
                check(true, l.modeY() >= l.y() && l.doneY() + l.buttonHeight() <= l.y() + l.height(), "button bounds");
                check(true, l.modeY() + l.buttonHeight() <= l.doneY(), "button overlap");
            }
        }
        System.out.println("PASS: " + checks + " policy/layout assertions; 19,683 exhaustive and 15,000 random bursts.");
    }

    private static void verifyBurst(HotbarOrder order, int[] keys) {
        HotbarOrderPolicy policy = new HotbarOrderPolicy();
        int[] counts = new int[9];
        for (int key : keys) { counts[key]++; policy.recordPress(1 << key); }
        int current = 4;
        // Oracle state for the Herzium order: the press behind the current slot.
        int selectionSlot = -1;
        int selectionPress = 0;
        while (Arrays.stream(counts).sum() > 0) {
            int mask = 0;
            for (int i = 0; i < 9; i++) if (counts[i] > 0) mask |= 1 << i;
            // Independent oracle: key history or min/max queued slot.
            int expected = -1;
            if (order == HotbarOrder.HERZIUM) {
                int superseded = current == selectionSlot ? selectionPress : 0;
                // Walking the history backwards, a slot's first hit is its last press.
                for (int i = keys.length - 1; i >= 0; i--) {
                    int slot = keys[i];
                    if (counts[slot] == 0 || (slot != current && i + 1 <= superseded)) continue;
                    expected = slot;
                    break;
                }
            } else if (order == HotbarOrder.VANILLA) {
                for (int i = 0; i < 9; i++) if (counts[i] > 0) expected = i;
            } else {
                for (int i = 0; i < 9; i++) if (counts[i] > 0) { expected = i; break; }
            }
            check(expected, policy.preview(mask, order, current), "preview matches independent policy");
            policy.beginPass(order, current);
            int committed = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (counts[slot] == 0) continue;
                counts[slot]--; // Vanilla consumes exactly once per mapping.
                boolean accept = policy.acceptSelection(slot);
                if (order == HotbarOrder.VANILLA) check(true, accept, "Vanilla setter passthrough");
                if (accept) committed = slot;
            }
            check(expected, committed, "actual consumed winner matches preview");
            if (committed >= 0) {
                current = committed;
                if (order == HotbarOrder.HERZIUM) {
                    selectionSlot = committed;
                    for (int i = keys.length - 1; i >= 0; i--) if (keys[i] == committed) { selectionPress = i + 1; break; }
                }
            }
        }
        if (order == HotbarOrder.HERZIUM) {
            check(keys[keys.length - 1], current, "queued clicks never undo the last input");
        }
    }

    private static void check(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
