package dev.zymekoh.herzium.gui;

/**
 * Logical-pixel bounds shared by the screen and its small-screen checks.
 *
 * <p>Top to bottom: the title (when there is room), the order button, the burst options, the
 * scrollable explanation and Done. The options are stacked one per row when the panel has room
 * for them and a readable explanation; otherwise they share a single row. The explanation is the
 * first thing to shrink, down to nothing on the smallest screens; the buttons never overlap.</p>
 */
public record HerziumConfigLayout(int x, int y, int width, int height, int padding,
        int buttonHeight, int modeY, int doneY, int textTop, int textBottom, boolean titleVisible,
        int optionsY, int optionHeight, int optionColumns, int optionGap) {
    public static final int OPTIONS = 3;
    /** Room the explanation needs to be worth stacking the options: two lines. */
    private static final int MIN_TEXT = 22;
    private static final int ROW_GAP = 2;

    public static HerziumConfigLayout fit(int screenWidth, int screenHeight) {
        return fit(screenWidth, screenHeight, 150);
    }

    /**
     * @param optionLabelWidth the widest option label with its switch, in logical pixels: the
     *                         options only share a row when each one still has that much width
     */
    public static HerziumConfigLayout fit(int screenWidth, int screenHeight, int optionLabelWidth) {
        int width = Math.max(1, Math.min(380, screenWidth - 16));
        int height = Math.max(1, Math.min(256, screenHeight - 16));
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;
        int padding = Math.min(10, Math.max(1, width / 8));
        int buttonHeight = Math.min(22, Math.max(8, height / 7));
        boolean title = height >= 110;
        // Very short panels spend their few pixels on controls, not margins.
        boolean tiny = height < 80;
        int edge = tiny ? 2 : 6;
        int verticalGap = tiny ? 1 : 4;
        int modeY = y + (title ? 28 : edge);
        int doneY = y + height - buttonHeight - edge;
        int inner = Math.max(1, width - padding * 2);
        int gap = Math.min(4, Math.max(1, inner / 60));
        int optionsY = modeY + buttonHeight + verticalGap;
        int optionHeight = Math.min(18, Math.max(8, buttonHeight - 4));
        int stacked = OPTIONS * optionHeight + (OPTIONS - 1) * ROW_GAP;
        int room = doneY - 6 - optionsY;
        int columns;
        if (room >= stacked + MIN_TEXT) {
            columns = 1;
        } else {
            columns = OPTIONS;
            // Not even one row of the usual height fits above Done: the row takes what is left.
            optionHeight = Math.max(1, Math.min(optionHeight, doneY - verticalGap - optionsY));
        }
        boolean wideEnough = (inner - (OPTIONS - 1) * gap) / OPTIONS >= optionLabelWidth;
        if (columns == 1 && wideEnough && room < stacked + MIN_TEXT * 3) {
            // A tall explanation matters more than a column of short switches.
            columns = OPTIONS;
        }
        int rows = columns == 1 ? OPTIONS : 1;
        int optionsBottom = optionsY + rows * optionHeight + (rows - 1) * ROW_GAP;
        // On the smallest screens there is no room left for the explanation: it gets zero height.
        int textTop = Math.min(optionsBottom + 6, doneY);
        return new HerziumConfigLayout(x, y, width, height, padding, buttonHeight,
                modeY, doneY, textTop, Math.max(textTop, doneY - 6), title,
                optionsY, optionHeight, columns, gap);
    }

    /** The bounds of option {@code index} (0 to 2): x, y, width, height. */
    public int[] option(int index) {
        int inner = Math.max(1, this.width - this.padding * 2);
        if (this.optionColumns == 1) {
            return new int[] {this.x + this.padding, this.optionsY + index * (this.optionHeight + ROW_GAP), inner,
                    this.optionHeight};
        }
        int each = Math.max(1, (inner - (this.optionColumns - 1) * this.optionGap) / this.optionColumns);
        return new int[] {this.x + this.padding + index * (each + this.optionGap), this.optionsY, each, this.optionHeight};
    }
}
