package dev.zymekoh.herzium.performance;

import java.util.concurrent.locks.LockSupport;

/** Enforces a render-thread ceiling of 10 FPS while Minecraft is unfocused. */
public final class InactiveFpsLimiter {
    public static final int INACTIVE_FPS = 10;
    private static final long FRAME_INTERVAL_NANOS = 1_000_000_000L / INACTIVE_FPS;
    private static long nextInactiveFrameNanos;

    private InactiveFpsLimiter() {
    }

    public static void limit(boolean windowActive) {
        if (windowActive) {
            nextInactiveFrameNanos = 0L;
            return;
        }

        long now = System.nanoTime();
        long deadline = nextInactiveFrameNanos;
        if (deadline == 0L
                || now >= deadline
                || deadline - now > FRAME_INTERVAL_NANOS) {
            deadline = now + FRAME_INTERVAL_NANOS;
        }

        while (!Thread.currentThread().isInterrupted()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                break;
            }
            LockSupport.parkNanos(remaining);
        }

        nextInactiveFrameNanos = System.nanoTime() + FRAME_INTERVAL_NANOS;
    }
}
