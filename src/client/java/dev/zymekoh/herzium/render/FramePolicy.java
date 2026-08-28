package dev.zymekoh.herzium.render;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * The single decision about when Herzium removes the frame limit.
 *
 * <p>Both frame-limit mixins ask this, so the two layers of the same policy
 * cannot drift apart and start disagreeing about the same frame.</p>
 */
public final class FramePolicy {
    private FramePolicy() {
    }

    /**
     * Whether the player typed a frame limit of their own.
     *
     * <p>Herzium removes the throttles Minecraft applies without asking. It does
     * not overrule a number the player chose: overriding an explicit setting is
     * the same mistake as writing to {@code options.txt}, just less visible.
     * {@link dev.zymekoh.herzium.gui.FrameLimitToast} tells them it is being
     * honoured, so an uncapping mod that appears to do nothing has a reason on
     * screen.</p>
     */
    public static boolean playerChoseALimit(Minecraft minecraft) {
        return minecraft.options.framerateLimit().get() < Options.UNLIMITED_FRAMERATE_CUTOFF;
    }

    /**
     * Whether this frame should ignore vanilla's pacing.
     *
     * <p>Vanilla throttles for five reasons and Herzium overrides two of them.
     * The three it hands back cost nothing that can be seen: a minimized window
     * shows nothing, a menu with no level behind it is a static image, and ten
     * minutes without a single input means nobody is there. Handing those back
     * is what stops a long session from ending slower than it started, because
     * holding the GPU at its power ceiling to render a title screen makes it
     * drop sustained clocks for the frames that do matter.</p>
     *
     * <p>{@code SHORT_AFK} is the exception, and only while a level is loaded.
     * Vanilla drops to 30 fps after sixty seconds without input, with mouse
     * movement counting as input. Sixty seconds is not away from the game: it
     * is standing in a queue, reading chat, or waiting for a fight to start.
     * The player is still looking at the world, and the first mouse movement
     * after the throttle lands arrives on a 33 ms frame, so a flick applies as
     * one lump instead of a sweep. That reads as the camera dragging, which is
     * exactly what it is. With no level loaded the same throttle is harmless
     * and is kept.</p>
     */
    public static boolean shouldRunUncapped(
            Minecraft minecraft,
            FramerateLimitTracker.FramerateThrottleReason reason) {
        // There is no "unfocused" throttle reason, so an unfocused window would
        // otherwise report NONE and stay uncapped in the background.
        if (!minecraft.isWindowActive()) {
            return false;
        }

        return switch (reason) {
            case NONE -> true;
            case SHORT_AFK -> minecraft.level != null;
            case WINDOW_ICONIFIED, LONG_AFK, OUT_OF_LEVEL_MENU -> false;
        };
    }
}
