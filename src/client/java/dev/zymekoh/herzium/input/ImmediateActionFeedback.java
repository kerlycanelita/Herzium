package dev.zymekoh.herzium.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Records a render-only acknowledgement for Vanilla attack/use bindings.
 *
 * <p>The observer runs after {@link KeyMapping#click(InputConstants.Key)} has
 * recorded the real logical click. It deliberately does not consume that
 * click, invoke a gameplay method, mutate a delay/cooldown, or send a packet.
 * Consequently keyboard, scancode and mouse-button remaps all receive the same
 * next-frame visual acknowledgement while {@code Minecraft#handleKeybinds}
 * remains the sole authority for the action.</p>
 */
public final class ImmediateActionFeedback {
    private static final long PULSE_NANOS = 45_000_000L;

    private static final AtomicLong LAST_ATTACK_PRESS_NANOS = new AtomicLong();
    private static final AtomicLong LAST_USE_PRESS_NANOS = new AtomicLong();

    private ImmediateActionFeedback() {
    }

    /**
     * Called after Vanilla has queued a physical key/button as a logical click.
     *
     * <p>Nothing is acknowledged while the player is using an item. Vanilla's
     * {@code handleKeybinds} opens with {@code if (player.isUsingItem())} and,
     * inside it, drains the queued attack, use and pick clicks through empty
     * loop bodies: the presses are not deferred, they are thrown away. Drawing
     * a mark for one of those would report a registration the game is about to
     * discard, which reads as latency and is the opposite of what the mark is
     * for. So it is withheld exactly where Vanilla is silent.</p>
     *
     * <p>A miss is not a discard, and stays marked. Swinging at empty air still
     * runs the action; it simply hits nothing. Only inputs the game never acts
     * on at all are withheld.</p>
     *
     * <p>{@link net.minecraft.world.entity.LivingEntity#isUsingItem()} reads a
     * synchronized entity flag, so this remains a read-only render decision:
     * no click is consumed, no delay or cooldown is touched, and no packet is
     * added, removed or retimed.</p>
     */
    public static void observeLogicalKey(InputConstants.Key logicalKey) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || player.isSpectator()
                || minecraft.screen != null
                || minecraft.getOverlay() != null
                || player.isUsingItem()) {
            return;
        }

        long now = System.nanoTime();
        if (matches(minecraft.options.keyAttack, logicalKey)) {
            LAST_ATTACK_PRESS_NANOS.set(now);
        }
        if (matches(minecraft.options.keyUse, logicalKey)) {
            LAST_USE_PRESS_NANOS.set(now);
        }
    }

    /** Returns two independent, frame-time-based intensities in the range 0..1. */
    public static FeedbackSample sample() {
        long now = System.nanoTime();
        return new FeedbackSample(
                intensity(now, LAST_ATTACK_PRESS_NANOS.get()),
                intensity(now, LAST_USE_PRESS_NANOS.get()));
    }

    public static void reset() {
        LAST_ATTACK_PRESS_NANOS.set(0L);
        LAST_USE_PRESS_NANOS.set(0L);
    }

    private static boolean matches(KeyMapping mapping, InputConstants.Key logicalKey) {
        return ((KeyMappingAccessor) mapping).herzium$getBoundKey().equals(logicalKey);
    }

    private static float intensity(long now, long pressedAt) {
        if (pressedAt == 0L) {
            return 0.0F;
        }
        long age = now - pressedAt;
        if (age < 0L || age >= PULSE_NANOS) {
            return 0.0F;
        }
        float remaining = 1.0F - (float) age / (float) PULSE_NANOS;
        return remaining * remaining;
    }

    public record FeedbackSample(float attack, float use) {
        public boolean visible() {
            return this.attack > 0.0F || this.use > 0.0F;
        }
    }
}
