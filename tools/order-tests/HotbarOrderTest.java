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
        check(8, policy.preview(257, HotbarOrder.HERZIUM), "duplicate binding deterministic tie");
        check(0, policy.preview(257, HotbarOrder.VANILLA_REVERSED), "duplicate binding reverse");
        policy.reset(); policy.recordPress(1 << 8); policy.recordPress(1 << 0);
        policy.beginPass(HotbarOrder.HERZIUM);
        policy.recordPress(1 << 8); // Later events cannot alter a pass already underway.
        check(true, policy.acceptSelection(0), "frozen pass first winner");
        check(false, policy.acceptSelection(8), "frozen pass ignores late event");
        check(8, policy.preview(257, HotbarOrder.HERZIUM), "next generation receives late event");
        policy.reset(); check(8, policy.preview(257, HotbarOrder.HERZIUM), "unknown event order uses Vanilla tie");
        check(-1, policy.preview(0, HotbarOrder.HERZIUM), "empty queue has no invented input");

        // The same policy sees slot masks regardless of mouse, keysym or scancode.
        for (String binding : new String[] {"mouse.left", "mouse.right", "mouse.side", "keysym", "scancode"}) {
            policy.reset(); policy.recordPress(1 << 8); policy.recordPress(1 << 0);
            check(0, policy.preview(257, HotbarOrder.HERZIUM), binding);
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
        while (Arrays.stream(counts).sum() > 0) {
            int mask = 0;
            for (int i = 0; i < 9; i++) if (counts[i] > 0) mask |= 1 << i;
            // Independent oracle: key history or min/max queued slot.
            int expected = -1;
            if (order == HotbarOrder.HERZIUM) {
                for (int i = keys.length - 1; i >= 0; i--) if (counts[keys[i]] > 0) { expected = keys[i]; break; }
            } else if (order == HotbarOrder.VANILLA) {
                for (int i = 0; i < 9; i++) if (counts[i] > 0) expected = i;
            } else {
                for (int i = 0; i < 9; i++) if (counts[i] > 0) { expected = i; break; }
            }
            check(expected, policy.preview(mask, order), "preview matches independent policy");
            policy.beginPass(order);
            int committed = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (counts[slot] == 0) continue;
                counts[slot]--; // Vanilla consumes exactly once per mapping.
                boolean accept = policy.acceptSelection(slot);
                if (order == HotbarOrder.VANILLA) check(true, accept, "Vanilla setter passthrough");
                if (accept) committed = slot;
            }
            check(expected, committed, "actual consumed winner matches preview");
        }
    }

    private static void check(Object expected, Object actual, String label) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
