package dev.zymekoh.herzium.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

/**
 * What the KoHs tab draws with: the colours and the handful of primitives of KoHs Death Effects'
 * screen that its {@code KohsPage} uses, so the tab is the same drawing in both mods. Decoration
 * only; nothing here reads or moves a widget.
 */
final class KohsKit {
    static final int TEXT = 0xFFF4EEFA;
    static final int TEXT_MUTED = 0xFFBDB8CF;
    static final int TEXT_DIM = 0xFF8E8AA0;
    static final int TITLE = 0xFFFFF7FF;
    private static final int ACCENT = 0xFFA855F7;
    private static final int ACCENT_BRIGHT = 0xFFE9D5FF;
    private static final int CARD_BORDER_HOVER = 0xE0C084FC;
    private static final int BUTTON_TOP = 0xE03A1668;
    private static final int BUTTON_BOTTOM = 0xE61D0D32;
    private static final int BUTTON_HOVER_TOP = 0xF06D28D9;
    private static final int BUTTON_HOVER_BOTTOM = 0xF02E1052;
    /** Rows of a round shape per stratum: see {@link #isolate}. */
    private static final int ROWS_PER_STRATUM = 24;

    private KohsKit() {
    }

    /** A rectangle of the screen, in GUI pixels. */
    record Rect(int x, int y, int width, int height) {
        static final Rect EMPTY = new Rect(0, 0, 0, 0);

        int right() { return this.x + this.width; }

        int bottom() { return this.y + this.height; }

        int centerX() { return this.x + this.width / 2; }

        int centerY() { return this.y + this.height / 2; }

        boolean contains(double pointX, double pointY) {
            return pointX >= this.x && pointX < right() && pointY >= this.y && pointY < bottom();
        }
    }

    /** {@code color} with its alpha multiplied by {@code factor}. */
    static int fade(int color, float factor) {
        return Math.round(((color >>> 24) & 255) * clamp01(factor)) << 24 | (color & 0xFFFFFF);
    }

    static int withAlpha(int color, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | (color & 0xFFFFFF);
    }

    static int lerp(int from, int to, float amount) {
        float t = clamp01(amount);
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static float clamp01(float value) {
        return value < 0.0F ? 0.0F : Math.min(1.0F, value);
    }

    static float easeOutCubic(float value) {
        float inverse = 1.0F - clamp01(value);
        return 1.0F - inverse * inverse * inverse;
    }

    static float easeInOutSine(float value) {
        return (float) (0.5D - 0.5D * Math.cos(Math.PI * clamp01(value)));
    }

    /**
     * Starts a new GUI stratum. Minecraft places every GUI element by testing its bounds against
     * the elements already in the stratum, so a shape drawn from hundreds of spans gets a stratum
     * of its own, opened before and after it.
     */
    static void isolate(GuiGraphicsExtractor graphics) {
        graphics.nextStratum();
    }

    /** A panel with 3px rounded corners and a vertical gradient. */
    static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int top, int bottom) {
        if (width < 6 || height < 6) {
            if (width > 0 && height > 0) {
                graphics.fillGradient(x, y, x + width, y + height, top, bottom);
            }
            return;
        }
        graphics.fillGradient(x + 3, y, x + width - 3, y + height, top, bottom);
        graphics.fillGradient(x + 1, y + 1, x + 3, y + height - 1, top, bottom);
        graphics.fillGradient(x + width - 3, y + 1, x + width - 1, y + height - 1, top, bottom);
        graphics.fillGradient(x, y + 3, x + 1, y + height - 3, top, bottom);
        graphics.fillGradient(x + width - 1, y + 3, x + width, y + height - 3, top, bottom);
    }

    static void roundedOutline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width < 6 || height < 6) {
            HerziumTheme.drawOutline(graphics, x, y, width, height, color);
            return;
        }
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x + 3, y, right - 3, y + 1, color);
        graphics.fill(x + 3, bottom - 1, right - 3, bottom, color);
        graphics.fill(x, y + 3, x + 1, bottom - 3, color);
        graphics.fill(right - 1, y + 3, right, bottom - 3, color);
        graphics.fill(x + 1, y + 1, x + 3, y + 2, color);
        graphics.fill(x + 1, y + 2, x + 2, y + 3, color);
        graphics.fill(right - 3, y + 1, right - 1, y + 2, color);
        graphics.fill(right - 2, y + 2, right - 1, y + 3, color);
        graphics.fill(x + 1, bottom - 2, x + 3, bottom - 1, color);
        graphics.fill(x + 1, bottom - 3, x + 2, bottom - 2, color);
        graphics.fill(right - 3, bottom - 2, right - 1, bottom - 1, color);
        graphics.fill(right - 2, bottom - 3, right - 1, bottom - 2, color);
    }

    /** A soft halo: outlines that fade as they move away from the rectangle. */
    static void halo(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, int layers,
            float strength) {
        int baseAlpha = (color >>> 24) & 255;
        for (int layer = 1; layer <= layers; layer++) {
            int alpha = Math.round(baseAlpha * strength * (1.0F - (layer - 1) / (float) layers) * 0.45F);
            if (alpha < 3) {
                break;
            }
            roundedOutline(graphics, x - layer, y - layer, width + layer * 2, height + layer * 2, withAlpha(color, alpha));
        }
    }

    /**
     * Text that honours a faded colour. Minecraft draws a nearly transparent text colour fully
     * opaque, so anything faded below visibility is skipped instead.
     */
    static void label(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        if (((color >>> 24) & 255) < 8 || text.isEmpty()) {
            return;
        }
        graphics.text(font, text, x, y, color, shadow);
    }

    static void line(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        if (((color >>> 24) & 255) < 8) {
            return;
        }
        graphics.text(font, text, x, y, color, false);
    }

    /** {@code text} cut to {@code room} pixels. */
    static String fit(Font font, String text, int room) {
        return ellipsis(font, text, room);
    }

    /** {@code text} cut to {@code room} pixels with an ellipsis where it was cut. */
    static String ellipsis(Font font, String text, int room) {
        if (font.width(text) <= room) {
            return text;
        }
        String dots = "…";
        if (room < font.width(dots)) {
            return "";
        }
        return font.plainSubstrByWidth(text, room - font.width(dots)).stripTrailing() + dots;
    }

    /** A glint running through a line of text every few seconds: the text again, brighter, inside a moving clip. */
    static void glint(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, double seconds) {
        double period = 4.5D;
        double sweep = 0.85D;
        double phase = seconds % period;
        int width = font.width(text);
        if (phase > sweep || width <= 0) {
            return;
        }
        int band = 9;
        int center = x - band + (int) Math.round((width + band * 2) * (phase / sweep));
        int left = Math.max(x, center - band);
        int right = Math.min(x + width, center + band);
        if (left >= right) {
            return;
        }
        graphics.enableScissor(left, y - 1, right, y + 9);
        label(graphics, font, text, x, y, 0xC8FFFFFF, false);
        graphics.disableScissor();
    }

    /** A filled diamond. */
    static void diamond(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        for (int row = -radius; row <= radius; row++) {
            int span = radius - Math.abs(row);
            graphics.fill(centerX - span, centerY + row, centerX + span + 1, centerY + row + 1, color);
        }
    }

    /** {@code text} drawn {@code scale} times larger, centred on {@code centerX}. */
    static void bigText(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y, float scale,
            int color, boolean shadow) {
        if (((color >>> 24) & 255) < 8 || text.isEmpty()) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX - font.width(text) * scale / 2.0F, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, 0, 0, color, shadow);
        graphics.pose().popMatrix();
    }

    /** A pixel ring from horizontal spans. */
    static void ring(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int thickness, int color) {
        if (radius <= 0 || ((color >>> 24) & 255) < 4) {
            return;
        }
        int inner = Math.max(0, radius - thickness);
        boolean many = radius > 6;
        for (int dy = -radius; dy <= radius; dy++) {
            if (many && (dy + radius) % ROWS_PER_STRATUM == 0) {
                isolate(graphics);
            }
            int outerSpan = (int) Math.round(Math.sqrt((double) radius * radius - (double) dy * dy));
            int innerSpan = Math.abs(dy) >= inner ? -1
                    : (int) Math.round(Math.sqrt((double) inner * inner - (double) dy * dy));
            if (innerSpan < 0) {
                graphics.fill(centerX - outerSpan, centerY + dy, centerX + outerSpan + 1, centerY + dy + 1, color);
            } else {
                graphics.fill(centerX - outerSpan, centerY + dy, centerX - innerSpan, centerY + dy + 1, color);
                graphics.fill(centerX + innerSpan + 1, centerY + dy, centerX + outerSpan + 1, centerY + dy + 1, color);
            }
        }
        if (many) {
            isolate(graphics);
        }
    }

    /** Angular marks on the four corners, like the clasps of a blade. */
    private static void bladeCorners(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int size, int color) {
        if (width < size * 2 + 4 || height < size * 2 + 4 || ((color >>> 24) & 255) < 4) {
            return;
        }
        int right = x + width;
        int bottom = y + height;
        graphics.fill(x - 1, y - 1, x + size, y, color);
        graphics.fill(x - 1, y, x, y + size, color);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, color);
        graphics.fill(right - size, y - 1, right + 1, y, color);
        graphics.fill(right, y, right + 1, y + size, color);
        graphics.fill(right - 2, y + 1, right - 1, y + 2, color);
        graphics.fill(x - 1, bottom, x + size, bottom + 1, color);
        graphics.fill(x - 1, bottom - size, x, bottom, color);
        graphics.fill(x + 1, bottom - 2, x + 2, bottom - 1, color);
        graphics.fill(right - size, bottom, right + 1, bottom + 1, color);
        graphics.fill(right, bottom - size, right + 1, bottom, color);
        graphics.fill(right - 2, bottom - 2, right - 1, bottom - 1, color);
    }

    /** One glass button: dark glass that brightens with {@code hover}; the primary one wears blade corners. */
    static void glassButton(GuiGraphicsExtractor graphics, int x, int y, int width, int height, String text, boolean primary,
            float hover, float fade) {
        if (fade <= 0.01F || width <= 0 || height <= 0) {
            return;
        }
        float lift = primary ? 0.35F + hover * 0.65F : hover;
        int border = lerp(CARD_BORDER_HOVER, ACCENT_BRIGHT, hover);
        if (hover > 0.05F) {
            halo(graphics, x, y, width, height, ACCENT, 3, hover * fade);
        }
        panel(graphics, x, y, width, height, fade(lerp(BUTTON_TOP, BUTTON_HOVER_TOP, lift), fade),
                fade(lerp(BUTTON_BOTTOM, BUTTON_HOVER_BOTTOM, lift), fade));
        roundedOutline(graphics, x, y, width, height, fade(border, fade));
        if (primary) {
            bladeCorners(graphics, x, y, width, height, 4, fade(border, 0.8F * fade));
        }
        Font font = Minecraft.getInstance().font;
        String label = fit(font, text, width - 8);
        label(graphics, font, label, x + (width - font.width(label)) / 2, y + (height - 8) / 2,
                fade(lerp(TEXT, 0xFFFFFFFF, hover), fade), true);
    }
}
