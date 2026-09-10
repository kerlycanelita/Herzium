package dev.zymekoh.herzium.gui;

/** Logical-pixel bounds shared by the screen and its small-screen checks. */
public record HerziumConfigLayout(int x, int y, int width, int height, int padding,
        int buttonHeight, int modeY, int doneY, int textTop, int textBottom, boolean titleVisible) {
    public static HerziumConfigLayout fit(int screenWidth, int screenHeight) {
        int width = Math.max(1, Math.min(360, screenWidth - 16));
        int height = Math.max(1, Math.min(210, screenHeight - 16));
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int padding = Math.min(10, Math.max(1, width / 8));
        int buttonHeight = Math.min(22, Math.max(8, height / 6));
        boolean title = height >= 96;
        int modeY = y + (title ? 30 : 6);
        int doneY = y + height - buttonHeight - 6;
        int textTop = modeY + buttonHeight + 8;
        return new HerziumConfigLayout(x, y, width, height, padding, buttonHeight,
                modeY, doneY, textTop, Math.max(textTop, doneY - 8), title);
    }
}
