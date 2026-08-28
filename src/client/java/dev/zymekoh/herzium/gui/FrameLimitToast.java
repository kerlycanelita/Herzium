package dev.zymekoh.herzium.gui;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Says out loud that the player's own frame limit is being honoured.
 *
 * <p>Herzium removes the throttles Minecraft applies without asking -- the menu
 * cap, the AFK cap -- but it does not override a limit the player typed in
 * themselves. That is the right call and it is also invisible: someone who set
 * 120 fps and installed a mod advertising uncapped rendering would reasonably
 * conclude the mod is broken. This is the one line of interface that closes
 * that gap.</p>
 */
public final class FrameLimitToast implements Toast {
    /** How long the toast stays fully visible before it retires itself. */
    private static final long VISIBLE_MS = 3_000L;
    private static final int WIDTH = 220;
    private static final int HEIGHT = 44;
    private static final int ICON = 16;

    /** One advisory per limit value per world, so it informs without nagging. */
    private static int announcedLimit = -1;

    private final int limit;
    private final Component title;
    private final List<FormattedCharSequence> messageLines;
    private Visibility visibility = Visibility.SHOW;

    private FrameLimitToast(int limit, Font font) {
        this.limit = limit;
        this.title = Component.translatable("herzium.toast.fps_limit.title", limit);
        this.messageLines = font.split(
                Component.translatable("herzium.toast.fps_limit.message"),
                WIDTH - ICON - 26);
    }

    /**
     * Shows the advisory once the player is actually back at the controls.
     *
     * <p>Deliberately not fired the moment the limit is read: a toast thrown
     * while the world is still loading, or over an open screen, is a toast
     * nobody sees. It waits for a frame with a level and no screen.</p>
     */
    public static void maybeAnnounce(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }

        int limit = minecraft.options.framerateLimit().get();
        if (limit >= Options.UNLIMITED_FRAMERATE_CUTOFF || limit == announcedLimit) {
            return;
        }

        announcedLimit = limit;
        minecraft.getToastManager().addToast(new FrameLimitToast(limit, minecraft.font));
    }

    /** Called when the client enters a different world. */
    public static void resetSession() {
        announcedLimit = -1;
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        this.visibility = fullyVisibleForMs >= VISIBLE_MS ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Object getToken() {
        // Keyed on the class, so a second advisory replaces the first instead of
        // stacking two of the same warning down the side of the screen.
        return FrameLimitToast.class;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        HerziumTheme.fillRounded(graphics, 0, 0, WIDTH, HEIGHT, 0xEE241038, 0xF4160925);
        HerziumTheme.drawOutline(graphics, 0, 0, WIDTH, HEIGHT, HerziumTheme.PANE_OUTLINE);
        graphics.fill(0, 2, 2, HEIGHT - 2, HerziumTheme.TEXT_WARN);

        // The manager slides the whole toast in; this is the second, smaller
        // motion that makes the contents look like they arrived rather than
        // like they were painted on.
        float settle = ease(fullyVisibleForMs, 220L);
        int drop = Math.round(4.0F * (1.0F - settle));

        drawWarningGlyph(graphics, 9, 9 + drop, settle);

        graphics.text(
                font,
                this.title,
                ICON + 18,
                7 + drop,
                withAlpha(HerziumTheme.TEXT_TITLE, settle),
                false);

        for (int line = 0; line < Math.min(2, this.messageLines.size()); line++) {
            float fade = ease(fullyVisibleForMs - 60L - line * 50L, 260L);
            graphics.text(
                    font,
                    this.messageLines.get(line),
                    ICON + 18,
                    19 + line * 10 + drop,
                    withAlpha(HerziumTheme.TEXT_BODY, fade),
                    false);
        }

        drawCountdownBar(graphics, fullyVisibleForMs);
    }

    /** The bar that shows how long is left before this disappears. */
    private static void drawCountdownBar(GuiGraphicsExtractor graphics, long fullyVisibleForMs) {
        int trackLeft = 4;
        int trackRight = WIDTH - 4;
        int y = HEIGHT - 5;
        graphics.fill(trackLeft, y, trackRight, y + 2, HerziumTheme.SCROLL_TRACK);

        float remaining = 1.0F - Mth.clamp((float) fullyVisibleForMs / VISIBLE_MS, 0.0F, 1.0F);
        int width = Math.round((trackRight - trackLeft) * remaining);
        if (width > 0) {
            graphics.fill(trackLeft, y, trackLeft + width, y + 2, HerziumTheme.TEXT_WARN);
            graphics.fill(trackLeft, y, trackLeft + width, y + 1, HerziumTheme.ACCENT);
        }
    }

    /**
     * A warning triangle drawn as pixels rather than shipped as a texture.
     *
     * <p>Built from fills so it inherits the mod's palette and scales with the
     * layout instead of being a bitmap that would have to be redrawn for every
     * size and re-tinted by hand.</p>
     */
    private static void drawWarningGlyph(GuiGraphicsExtractor graphics, int x, int y, float fade) {
        int body = withAlpha(HerziumTheme.TEXT_WARN, fade);
        int edge = withAlpha(0xFFFFE2A8, fade);
        int mark = withAlpha(0xFF2A1608, fade);
        int centre = x + ICON / 2;

        for (int row = 1; row < ICON; row++) {
            int half = Math.max(1, Math.round(row * (ICON / 2.0F) / ICON));
            int left = centre - half;
            int right = centre + half;
            graphics.fill(left, y + row, right, y + row + 1, body);
            graphics.fill(left, y + row, left + 1, y + row + 1, edge);
            graphics.fill(right - 1, y + row, right, y + row + 1, edge);
        }
        graphics.fill(centre - ICON / 2, y + ICON - 1, centre + ICON / 2, y + ICON, edge);

        // The exclamation mark, cut into the triangle.
        graphics.fill(centre - 1, y + 6, centre + 1, y + ICON - 5, mark);
        graphics.fill(centre - 1, y + ICON - 4, centre + 1, y + ICON - 3, mark);
    }

    /** Smoothstep on a 0..1 ramp, clamped at both ends. */
    private static float ease(long elapsedMillis, long durationMillis) {
        if (elapsedMillis <= 0L) {
            return 0.0F;
        }
        if (elapsedMillis >= durationMillis) {
            return 1.0F;
        }

        float t = (float) elapsedMillis / durationMillis;
        return t * t * (3.0F - 2.0F * t);
    }

    private static int withAlpha(int argb, float fade) {
        int alpha = Mth.clamp(Math.round((argb >>> 24) * fade), 0, 255);
        return alpha << 24 | argb & 0x00FFFFFF;
    }
}
