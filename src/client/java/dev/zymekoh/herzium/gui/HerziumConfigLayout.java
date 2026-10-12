package dev.zymekoh.herzium.gui;

/**
 * Logical-pixel bounds of the settings screen: tabs in a rail on the left, the switches of the
 * chosen tab in the middle, and what the pointed switch does on the right.
 *
 * <p>When the window narrows, the rail loses its labels and keeps its icons; narrower still, the
 * explanation moves under the switches, and on the smallest screens it gives its room to them.
 * Nothing overlaps and nothing leaves the panel at any size; {@code hotbarOrderTest} checks it
 * over thousands of sizes. Pure Java, so that test needs no Minecraft.</p>
 */
public record HerziumConfigLayout(Box panel, Box rail, Box list, Box info, Box done, boolean titleVisible,
        boolean railLabels, int tabHeight, int tabGap, int rowHeight, int rowGap, int visibleRows) {
    public static final int TABS = 4;

    public record Box(int x, int y, int width, int height) {
        public int right() { return x + width; }

        public int bottom() { return y + height; }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        public boolean empty() { return width <= 0 || height <= 0; }
    }

    public static HerziumConfigLayout fit(int screenWidth, int screenHeight) {
        return fit(screenWidth, screenHeight, 60);
    }

    /** @param tabLabelWidth the widest tab label, in logical pixels */
    public static HerziumConfigLayout fit(int screenWidth, int screenHeight, int tabLabelWidth) {
        int width = Math.max(1, Math.min(620, screenWidth - 12));
        int height = Math.max(1, Math.min(330, screenHeight - 12));
        Box panel = new Box((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
        boolean tiny = height < 110;
        int pad = tiny ? 3 : Math.max(4, Math.min(10, width / 44));
        int gap = tiny ? 2 : 6;
        boolean title = height >= 150;
        int header = title ? 26 : 0;

        int doneHeight = Math.max(8, Math.min(20, height / 8));
        int inner = Math.max(1, width - pad * 2);
        int doneWidth = Math.min(150, inner);
        Box done = new Box(panel.x() + (width - doneWidth) / 2, panel.bottom() - pad - doneHeight, doneWidth, doneHeight);

        int top = panel.y() + pad + header;
        int bodyHeight = Math.max(TABS, done.y() - gap - top);

        boolean labels = width >= 340;
        int railWidth = labels
                ? Math.min(Math.max(72, tabLabelWidth + 34), Math.max(72, inner * 3 / 10))
                : Math.min(26, Math.max(8, inner / 6));
        Box rail = new Box(panel.x() + pad, top, railWidth, bodyHeight);
        int tabGap = bodyHeight >= TABS * 14 ? 3 : bodyHeight >= TABS * 9 ? 1 : 0;
        int tabHeight = Math.max(1, Math.min(24, (bodyHeight - (TABS - 1) * tabGap) / TABS));

        int restX = rail.right() + gap;
        int restWidth = Math.max(1, panel.right() - pad - restX);
        Box list;
        Box info;
        if (width >= 430) {
            // Beside the switches.
            int infoWidth = Math.max(140, Math.min(230, restWidth * 42 / 100));
            list = new Box(restX, top, restWidth - infoWidth - gap, bodyHeight);
            info = new Box(list.right() + gap, top, infoWidth, bodyHeight);
        } else if (bodyHeight >= 150) {
            // Under them.
            int infoHeight = Math.max(44, bodyHeight * 38 / 100);
            list = new Box(restX, top, restWidth, bodyHeight - infoHeight - gap);
            info = new Box(restX, list.bottom() + gap, restWidth, infoHeight);
        } else {
            list = new Box(restX, top, restWidth, bodyHeight);
            info = new Box(restX, list.bottom(), 0, 0);
        }

        int rowGap = list.height() >= 60 ? 3 : 1;
        int rowHeight = Math.max(1, Math.min(20, list.height() >= 44 ? 20 : list.height()));
        int visibleRows = Math.max(1, (list.height() + rowGap) / (rowHeight + rowGap));
        return new HerziumConfigLayout(panel, rail, list, info, done, title, labels, tabHeight, tabGap,
                rowHeight, rowGap, visibleRows);
    }

    /** The bounds of tab {@code index}, 0 to {@link #TABS} - 1. */
    public Box tab(int index) {
        return new Box(rail.x(), rail.y() + index * (tabHeight + tabGap), rail.width(), tabHeight);
    }

    /** The bounds of the visible row {@code index}, 0 to {@link #visibleRows} - 1. */
    public Box row(int index) {
        return new Box(list.x(), list.y() + index * (rowHeight + rowGap), list.width(), rowHeight);
    }
}
