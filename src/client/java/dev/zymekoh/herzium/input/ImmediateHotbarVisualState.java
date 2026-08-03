package dev.zymekoh.herzium.input;

/**
 * Publishes immediate hotbar changes to the first-person renderer without
 * touching gameplay state. Input callbacks and rendering run on the client,
 * while the volatile revision also keeps the hand-off safe if another input
 * backend dispatches from a different thread.
 */
public final class ImmediateHotbarVisualState {
    private static volatile long revision;

    private ImmediateHotbarVisualState() {
    }

    public static void markSelectionChanged() {
        revision++;
    }

    public static long revision() {
        return revision;
    }
}
