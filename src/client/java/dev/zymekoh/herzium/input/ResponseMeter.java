package dev.zymekoh.herzium.input;

/**
 * Measures, for each hotbar key, how long the screen and the game took to follow it.
 *
 * <p>Three moments of one press: the key event, the first frame whose hotbar
 * shows the new slot, and the tick that really selects it. The numbers are
 * measured, never estimated: with the preview off the frame simply comes after
 * the tick, and that is what is shown.</p>
 */
public final class ResponseMeter {
    private static long pressNanos;
    private static int pressSlot = -1;
    private static boolean drawn;
    private static volatile float lastFrameMs = -1.0F;
    private static volatile float lastTickMs = -1.0F;
    private static volatile float averageFrameMs = -1.0F;
    private static volatile float averageTickMs = -1.0F;
    private static volatile int samples;
    private static String hudLine = "";
    private static int hudLineSamples = -1;

    private ResponseMeter() {
    }

    /** A hotbar key that will move the selection to {@code slot} was just pressed. */
    public static void pressed(int slot) {
        long now = System.nanoTime();
        if (pressSlot >= 0 && now - pressNanos < 250_000_000L) {
            return;
        }
        pressNanos = now;
        pressSlot = slot;
        drawn = false;
    }

    /** The hotbar is being drawn with {@code slot} selected. */
    public static void drawing(int slot) {
        if (pressSlot == slot && !drawn) {
            drawn = true;
            lastFrameMs = (System.nanoTime() - pressNanos) / 1_000_000.0F;
        }
    }

    /** The game selected {@code slot} in its tick. */
    public static void committed(int slot) {
        if (pressSlot < 0) {
            return;
        }
        if (slot == pressSlot) {
            lastTickMs = (System.nanoTime() - pressNanos) / 1_000_000.0F;
            if (!drawn) {
                // Nothing showed it before the tick: the next frame will, a frame later.
                lastFrameMs = -1.0F;
            } else {
                averageFrameMs = blend(averageFrameMs, lastFrameMs);
            }
            averageTickMs = blend(averageTickMs, lastTickMs);
            samples++;
        }
        pressSlot = -1;
    }

    public static void reset() {
        pressSlot = -1;
        lastFrameMs = lastTickMs = averageFrameMs = averageTickMs = -1.0F;
        samples = 0;
        hudLineSamples = -1;
    }

    /** The HUD line of the meter; built again only when a new key was measured. */
    public static String hudLine() {
        if (hudLineSamples != samples) {
            hudLineSamples = samples;
            hudLine = samples == 0 ? "" : String.format(java.util.Locale.ROOT, "%s ms | tick %.1f ms",
                    lastFrameMs < 0.0F ? "-" : String.format(java.util.Locale.ROOT, "%.1f", lastFrameMs), lastTickMs);
        }
        return hudLine;
    }

    private static float blend(float average, float value) {
        return average < 0.0F ? value : average + (value - average) * 0.15F;
    }

    public static float lastFrameMs() { return lastFrameMs; }

    public static float lastTickMs() { return lastTickMs; }

    public static float averageFrameMs() { return averageFrameMs; }

    public static float averageTickMs() { return averageTickMs; }

    public static int samples() { return samples; }
}
