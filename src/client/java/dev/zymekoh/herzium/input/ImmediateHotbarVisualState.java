package dev.zymekoh.herzium.input;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes immediate hotbar changes to the first-person renderer without
 * touching gameplay state. Input callbacks and rendering run on the client,
 * while the volatile revision also keeps the hand-off safe if another input
 * backend dispatches from a different thread.
 */
public final class ImmediateHotbarVisualState {
    private static final AtomicLong REVISION = new AtomicLong();

    private ImmediateHotbarVisualState() {
    }

    public static void markSelectionChanged() {
        REVISION.incrementAndGet();
    }

    public static long revision() {
        return REVISION.get();
    }
}
