package dev.zymekoh.herzium.gui;

import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * The one place Herzium's screens agree on what purple means.
 *
 * <p>{@code HerziumConfigScreen} and {@code HerziumWarningScreen} each carried
 * their own copy of {@code fillRounded}, {@code drawOutline}, {@code argb}, a
 * {@code Particle} class and the whole particle field. Two copies of the same
 * drawing code drift, and they had: different densities, different reaction
 * radii, different trail lengths, and two different purples for the same kind
 * of surface. All of it lives here now.</p>
 */
public final class HerziumTheme {
    /** Full-screen wash. Violet and translucent: the panorama stays visible. */
    public static final int BACKDROP_TOP = 0x4A2A0B4E;
    public static final int BACKDROP_BOTTOM = 0x5E140628;
    /** Top-level panel. Translucent enough for the particle field to show through. */
    public static final int PANEL_TOP = 0x8C280E45;
    public static final int PANEL_BOTTOM = 0x9C160726;
    /** Inset surfaces: sub-panes and option rows. */
    public static final int PANE_TOP = 0x5E24103C;
    public static final int PANE_BOTTOM = 0x72150724;
    public static final int CARD_TOP = 0x6A2C1247;
    public static final int CARD_BOTTOM = 0x7E190829;
    public static final int PANE_OUTLINE = 0x78814AA8;
    public static final int CARD_OUTLINE = 0x8E8E4CBE;
    public static final int DIVIDER = 0x6D9A4CC6;

    public static final int TEXT_TITLE = 0xFFF6EAFF;
    public static final int TEXT_PRIMARY = 0xFFEDDFF7;
    public static final int TEXT_BODY = 0xFFD9CBE4;
    public static final int TEXT_MUTED = 0xFFA79BB2;
    public static final int TEXT_HEADING = 0xFFEBC9FF;
    public static final int TEXT_ACCENT = 0xFFD2A5F0;
    public static final int TEXT_GOOD = 0xFF9BE8B1;
    public static final int TEXT_WARN = 0xFFFFD18A;
    public static final int TEXT_BAD = 0xFFFF9D9D;

    public static final int SCROLL_TRACK = 0x5A3C1C4E;
    public static final int SCROLL_THUMB = 0xD6CE75F4;

    private HerziumTheme() {
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    /** The violet wash drawn over the panorama or the world. */
    public static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fillGradient(0, 0, width, height, BACKDROP_TOP, BACKDROP_BOTTOM);
    }

    /** Slow breathing border, used on the outermost panel of both screens. */
    public static int pulsingBorder(long nowMillis, int baseAlpha, int alphaSwing) {
        float pulse = 0.5F + 0.5F * (float) Math.sin(nowMillis / 520.0F);
        return argb(Mth.clamp(baseAlpha + Math.round(pulse * alphaSwing), 0, 255), 176, 84, 236);
    }

    /**
     * A filled rectangle with its four corner pixels cut away.
     *
     * <p>Two overlapping gradients rather than a real rounded rectangle: two
     * quads instead of an arc mesh, and at this radius the difference is not
     * visible.</p>
     */
    public static void fillRounded(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int topColor,
            int bottomColor) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int radius = Math.min(3, Math.min(width / 2, height / 2));
        graphics.fillGradient(x + radius, y, x + width - radius, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + radius, x + width, y + height - radius, topColor, bottomColor);
    }

    public static void drawOutline(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color) {
        if (width <= 1 || height <= 1) {
            return;
        }

        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** Draws a pane's scrollbar, or nothing when there is nothing to scroll. */
    public static void drawScrollbar(
            GuiGraphicsExtractor graphics,
            int paneX,
            int paneY,
            int paneWidth,
            int paneHeight,
            int scrollOffset,
            int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }

        int trackX = paneX + paneWidth - 5;
        int trackTop = paneY + 4;
        int trackHeight = Math.max(1, paneHeight - 8);
        int thumbHeight = Math.min(
                trackHeight,
                Math.max(8, trackHeight * trackHeight / Math.max(1, trackHeight + maxScroll)));
        int thumbTravel = Math.max(0, trackHeight - thumbHeight);
        int thumbY = trackTop + thumbTravel * scrollOffset / maxScroll;
        graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, SCROLL_TRACK);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, SCROLL_THUMB);
    }

    /**
     * The drifting purple field behind both screens.
     *
     * <p>Density is capped hard. This mod exists to run without a frame limit,
     * so the field has to stay affordable at a thousand frames a second, and
     * every particle is quads submitted on every one of them. Particles are
     * therefore split into depth tiers: the far ones are a single pixel-sized
     * quad, and only the mid and near ones pay for a trail. At the
     * {@link #MAX_PARTICLES} ceiling that is roughly 650 quads per frame rather
     * than the 840 a uniform field of the same size would cost, and it is what
     * gives the field depth instead of one flat sheet of identical dots.</p>
     *
     * <p>Motion is integrated against elapsed wall-clock time, clamped to 50 ms
     * per frame, so the field drifts at the same speed at 60 fps and at 900 and
     * cannot lurch after a stall.</p>
     */
    public static final class ParticleField {
        /**
         * Raise this only with a frame-time measurement in hand. Every extra
         * particle is one or two more quads on every frame of a screen that is
         * deliberately not frame limited.
         */
        public static final int MAX_PARTICLES = 420;
        private static final int MIN_PARTICLES = 160;
        private static final long PIXELS_PER_PARTICLE = 6_000L;
        private static final float MAX_FRAME_SECONDS = 0.05F;

        private Particle[] particles = new Particle[0];
        private long lastFrameMillis = System.nanoTime() / 1_000_000L;

        /** Rebuilds the field for a new screen size. Deterministic per size. */
        public void resize(int width, int height) {
            int safeWidth = Math.max(1, width);
            int safeHeight = Math.max(1, height);
            long area = (long) safeWidth * safeHeight;
            int count = Mth.clamp(
                    MIN_PARTICLES + (int) (area / PIXELS_PER_PARTICLE),
                    MIN_PARTICLES,
                    MAX_PARTICLES);

            Random random = new Random(0x4845525A49554DL ^ (long) safeWidth << 32 ^ safeHeight);
            this.particles = new Particle[count];
            for (int index = 0; index < count; index++) {
                // Depth drives size, brightness, speed and whether the particle
                // is worth a trail at all, so a single value produces the whole
                // near/far separation.
                float depth = random.nextFloat();
                boolean near = depth > 0.82F;
                boolean far = depth < 0.45F;
                int size = far ? 1 : near ? 3 + random.nextInt(2) : 2;
                int baseAlpha = far
                        ? 14 + random.nextInt(24)
                        : near ? 96 + random.nextInt(74) : 44 + random.nextInt(52);
                float speedScale = 0.45F + depth * 1.75F;
                this.particles[index] = new Particle(
                        random.nextFloat() * safeWidth,
                        random.nextFloat() * safeHeight,
                        (16.0F + random.nextFloat() * 46.0F) * speedScale,
                        (-11.0F + random.nextFloat() * 22.0F) * speedScale,
                        size,
                        baseAlpha,
                        far ? 0 : size * (near ? 3 : 2));
            }
            this.lastFrameMillis = System.nanoTime() / 1_000_000L;
        }

        public void render(
                GuiGraphicsExtractor graphics,
                int width,
                int height,
                int mouseX,
                int mouseY,
                long nowMillis) {
            float elapsedSeconds =
                    Mth.clamp((nowMillis - this.lastFrameMillis) / 1000.0F, 0.0F, MAX_FRAME_SECONDS);
            this.lastFrameMillis = nowMillis;

            float reactionRadius = Mth.clamp(Math.min(width, height) / 3.6F, 42.0F, 110.0F);
            float radiusSquared = reactionRadius * reactionRadius;

            for (Particle particle : this.particles) {
                float dx = particle.x - mouseX;
                float dy = particle.y - mouseY;
                float distanceSquared = dx * dx + dy * dy;
                float influence = distanceSquared >= radiusSquared
                        ? 0.0F
                        : 1.0F - (float) Math.sqrt(distanceSquared) / reactionRadius;

                if (influence > 0.0F) {
                    // Nearer particles are shoved harder, so the cursor carves a
                    // visible hole in the foreground while the far haze barely
                    // reacts.
                    float push = 46.0F * influence * influence * (0.4F + particle.size * 0.3F);
                    float inverseDistance = 1.0F / Math.max(1.0F, (float) Math.sqrt(distanceSquared));
                    particle.x += dx * inverseDistance * push * elapsedSeconds;
                    particle.y += dy * inverseDistance * push * elapsedSeconds;
                }

                particle.x += particle.speedX * elapsedSeconds;
                particle.y += particle.speedY * elapsedSeconds;
                if (particle.x > width + 12.0F) {
                    particle.x = -12.0F;
                } else if (particle.x < -12.0F) {
                    particle.x = width + 12.0F;
                }
                if (particle.y < -8.0F) {
                    particle.y = height + 8.0F;
                } else if (particle.y > height + 8.0F) {
                    particle.y = -8.0F;
                }

                int alpha = Mth.clamp(
                        particle.baseAlpha + Math.round(influence * influence * 150.0F),
                        6,
                        235);
                int x = Math.round(particle.x);
                int y = Math.round(particle.y);

                if (particle.trail > 0) {
                    graphics.fill(
                            x - particle.trail,
                            y,
                            x,
                            y + particle.size,
                            argb(Math.max(5, alpha / 3), 138, 58, 216));
                }
                graphics.fill(
                        x,
                        y,
                        x + particle.size,
                        y + particle.size,
                        argb(alpha, 216, 140, 252));
            }
        }

        private static final class Particle {
            private float x;
            private float y;
            private final float speedX;
            private final float speedY;
            private final int size;
            private final int baseAlpha;
            private final int trail;

            private Particle(
                    float x,
                    float y,
                    float speedX,
                    float speedY,
                    int size,
                    int baseAlpha,
                    int trail) {
                this.x = x;
                this.y = y;
                this.speedX = speedX;
                this.speedY = speedY;
                this.size = size;
                this.baseAlpha = baseAlpha;
                this.trail = trail;
            }
        }
    }
}
