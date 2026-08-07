package dev.zymekoh.herzium.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Tiny fill-only font for the first resource reload.
 *
 * <p>Minecraft's normal font and text shaders do not exist yet while the
 * initial loading overlay is being rendered. Using them there logs missing
 * glyph/shader errors and can disturb the resource reload. This renderer only
 * emits GUI rectangles, so startup messages remain visible without depending
 * on resources that are still loading.</p>
 */
public final class StartupPixelFont {
    private static final int GLYPH_WIDTH = 3;
    private static final int GLYPH_HEIGHT = 5;

    private StartupPixelFont() {
    }

    public static int lineHeight(int scale) {
        return GLYPH_HEIGHT * Math.max(1, scale);
    }

    public static int width(String text, int scale) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int pixelScale = Math.max(1, scale);
        return text.length() * (GLYPH_WIDTH + 1) * pixelScale - pixelScale;
    }

    public static void drawCentered(
            GuiGraphicsExtractor graphics,
            String text,
            int centerX,
            int y,
            int scale,
            int color) {
        draw(graphics, text, centerX - width(text, scale) / 2, y, scale, color);
    }

    public static void draw(
            GuiGraphicsExtractor graphics,
            String text,
            int x,
            int y,
            int scale,
            int color) {
        int pixelScale = Math.max(1, scale);
        int cursorX = x;
        for (int index = 0; index < text.length(); index++) {
            int glyph = glyph(Character.toUpperCase(text.charAt(index)));
            drawGlyph(graphics, glyph, cursorX, y, pixelScale, color);
            cursorX += (GLYPH_WIDTH + 1) * pixelScale;
        }
    }

    public static List<String> wrap(String text, int maxWidth, int scale) {
        int pixelScale = Math.max(1, scale);
        int maxCharacters = Math.max(1, (maxWidth + pixelScale) / ((GLYPH_WIDTH + 1) * pixelScale));
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();

        for (String word : text.trim().split("\\s+")) {
            if (word.length() > maxCharacters) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                int offset = 0;
                while (word.length() - offset > maxCharacters) {
                    lines.add(word.substring(offset, offset + maxCharacters));
                    offset += maxCharacters;
                }
                if (offset < word.length()) {
                    line.append(word.substring(offset));
                }
                continue;
            }

            int proposedLength = line.isEmpty() ? word.length() : line.length() + 1 + word.length();
            if (proposedLength > maxCharacters) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }

        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.isEmpty() ? List.of("") : List.copyOf(lines);
    }

    private static void drawGlyph(
            GuiGraphicsExtractor graphics,
            int glyph,
            int x,
            int y,
            int scale,
            int color) {
        for (int row = 0; row < GLYPH_HEIGHT; row++) {
            int rowBits = glyph >> ((GLYPH_HEIGHT - 1 - row) * GLYPH_WIDTH) & 0b111;
            int column = 0;
            while (column < GLYPH_WIDTH) {
                while (column < GLYPH_WIDTH && (rowBits & 1 << (GLYPH_WIDTH - 1 - column)) == 0) {
                    column++;
                }
                int runStart = column;
                while (column < GLYPH_WIDTH && (rowBits & 1 << (GLYPH_WIDTH - 1 - column)) != 0) {
                    column++;
                }
                if (runStart < column) {
                    graphics.fill(
                            x + runStart * scale,
                            y + row * scale,
                            x + column * scale,
                            y + (row + 1) * scale,
                            color);
                }
            }
        }
    }

    private static int glyph(char character) {
        return switch (character) {
            case 'A' -> 0b010_101_111_101_101;
            case 'B' -> 0b110_101_110_101_110;
            case 'C' -> 0b011_100_100_100_011;
            case 'D' -> 0b110_101_101_101_110;
            case 'E' -> 0b111_100_110_100_111;
            case 'F' -> 0b111_100_110_100_100;
            case 'G' -> 0b011_100_101_101_011;
            case 'H' -> 0b101_101_111_101_101;
            case 'I' -> 0b111_010_010_010_111;
            case 'J' -> 0b001_001_001_101_010;
            case 'K' -> 0b101_101_110_101_101;
            case 'L' -> 0b100_100_100_100_111;
            case 'M' -> 0b101_111_111_101_101;
            case 'N' -> 0b101_111_111_111_101;
            case 'O' -> 0b010_101_101_101_010;
            case 'P' -> 0b110_101_110_100_100;
            case 'Q' -> 0b010_101_101_111_011;
            case 'R' -> 0b110_101_110_101_101;
            case 'S' -> 0b011_100_010_001_110;
            case 'T' -> 0b111_010_010_010_010;
            case 'U' -> 0b101_101_101_101_111;
            case 'V' -> 0b101_101_101_101_010;
            case 'W' -> 0b101_101_111_111_101;
            case 'X' -> 0b101_101_010_101_101;
            case 'Y' -> 0b101_101_010_010_010;
            case 'Z' -> 0b111_001_010_100_111;
            case '0' -> 0b111_101_101_101_111;
            case '1' -> 0b010_110_010_010_111;
            case '2' -> 0b110_001_010_100_111;
            case '3' -> 0b110_001_010_001_110;
            case '4' -> 0b101_101_111_001_001;
            case '5' -> 0b111_100_110_001_110;
            case '6' -> 0b011_100_110_101_010;
            case '7' -> 0b111_001_010_010_010;
            case '8' -> 0b010_101_010_101_010;
            case '9' -> 0b010_101_011_001_110;
            case '.' -> 0b000_000_000_000_010;
            case ',' -> 0b000_000_000_010_100;
            case ':' -> 0b000_010_000_010_000;
            case '\'' -> 0b010_010_000_000_000;
            case '-' -> 0b000_000_111_000_000;
            case '!' -> 0b010_010_010_000_010;
            case '?' -> 0b110_001_010_000_010;
            case '/' -> 0b001_001_010_100_100;
            case '+' -> 0b000_010_111_010_000;
            case '%' -> 0b101_001_010_100_101;
            default -> 0;
        };
    }
}
