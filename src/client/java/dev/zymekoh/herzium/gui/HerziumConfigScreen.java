package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.config.HerziumConfig.Feature;
import dev.zymekoh.herzium.gui.HerziumConfigLayout.Box;
import dev.zymekoh.herzium.input.HotbarOrder;
import dev.zymekoh.herzium.input.HotbarOrderController;
import dev.zymekoh.herzium.input.ResponseMeter;
import dev.zymekoh.herzium.input.ShapeMonitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Tabs in a rail on the left, the switches of the chosen tab in the middle, and on the right what
 * the pointed switch does. Geometry is {@link HerziumConfigLayout}'s; this class only fills it.
 */
public final class HerziumConfigScreen extends Screen {
    private enum Tab {
        ORDER, FEEL, MONITOR, KOHS;

        String key() { return "herzium.tab." + name().toLowerCase(Locale.ROOT); }
    }

    /** One control of a tab: an order to choose, or a switch. Exactly one of the two is set. */
    private record Row(HotbarOrder order, Feature feature, String key) {
        static Row of(HotbarOrder order) { return new Row(order, null, order.translationKey()); }

        static Row of(Feature feature) { return new Row(null, feature, feature.key()); }
    }

    private static final Identifier[] CLOCK = frames("clock", 64);
    private static final Identifier[] COMPASS = frames("compass", 32);
    private static final Identifier FEATHER = Identifier.withDefaultNamespace("textures/item/feather.png");
    private static final long ENTRANCE_MILLIS = 320L;
    private static final long WIPE_MILLIS = 180L;
    private static final int LINE = 10;

    /** The entrance plays once per game session, and the screen reopens on the tab it was left on. */
    private static boolean entered;
    private static Tab tab = Tab.ORDER;

    private final Screen parent;
    private final HerziumTheme.ParticleField particles = new HerziumTheme.ParticleField();
    private final AnimatedPurpleButton[] tabButtons = new AnimatedPurpleButton[HerziumConfigLayout.TABS];
    private final List<AnimatedPurpleButton> rowButtons = new ArrayList<>();
    private final List<FormattedCharSequence> stats = new ArrayList<>();
    private final KohsPage kohs = new KohsPage(true, this);
    private final String version = FabricLoader.getInstance().getModContainer("herzium")
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
    private HerziumConfigLayout layout;
    private List<Row> rows = List.of();
    private int rowScroll;
    private Row topic;
    private List<FormattedCharSequence> note = List.of();
    private List<FormattedCharSequence> body = List.of();
    private int infoScroll;
    private long openedAt;
    private long wipeAt;
    private long statsAt;

    public HerziumConfigScreen(Screen parent) {
        super(Component.translatable("herzium.config.title"));
        this.parent = parent;
    }

    private static Identifier[] frames(String item, int count) {
        Identifier[] frames = new Identifier[count];
        for (int frame = 0; frame < count; frame++) {
            frames[frame] = Identifier.withDefaultNamespace(String.format(Locale.ROOT, "textures/item/%s_%02d.png", item, frame));
        }
        return frames;
    }

    private static List<Row> rowsOf(Tab tab) {
        return switch (tab) {
            case ORDER -> List.of(Row.of(HotbarOrder.HERZIUM), Row.of(HotbarOrder.VANILLA), Row.of(HotbarOrder.VANILLA_REVERSED));
            case FEEL -> List.of(Row.of(Feature.PREVIEW), Row.of(Feature.PREVIEW_RECOVERY), Row.of(Feature.SWAP_PREVIEW),
                    Row.of(Feature.COMBAT_PREVIEW), Row.of(Feature.SLOT_POP), Row.of(Feature.NO_EQUIP_DIP),
                    Row.of(Feature.SMOOTH_INDICATOR));
            case MONITOR -> List.of(Row.of(Feature.SHAPE_MONITOR), Row.of(Feature.RESPONSE_METER));
            // The KoHs tab is a page of its own, the one KoHs Death Effects has.
            case KOHS -> List.of();
        };
    }

    /** Why a switch does nothing right now, as a translation key; null when it applies. */
    private static String blockedBy(Feature feature) {
        return feature == Feature.PREVIEW_RECOVERY && !HerziumConfig.get().on(Feature.PREVIEW)
                ? "herzium.config.option.needs_preview" : null;
    }

    @Override
    protected void init() {
        long now = millis();
        if (openedAt == 0L) {
            openedAt = entered ? now - ENTRANCE_MILLIS : now;
            if (tab == Tab.KOHS) kohs.enter();
        }
        entered = true;
        int labelWidth = 0;
        for (Tab each : Tab.values()) labelWidth = Math.max(labelWidth, font.width(Component.translatable(each.key())));
        layout = HerziumConfigLayout.fit(width, height, labelWidth);
        particles.resize(width, height);
        rows = rowsOf(tab);
        rowScroll = Mth.clamp(rowScroll, 0, Math.max(0, rows.size() - layout.visibleRows()));

        for (Tab each : Tab.values()) {
            Box box = layout.tab(each.ordinal());
            AnimatedPurpleButton button = new AnimatedPurpleButton(box.x(), box.y(), box.width(), box.height(),
                    Component.translatable(each.key()), () -> tab == each, pressed -> open(each));
            button.labelInset = layout.railLabels() ? iconSize(box) + 9 : -2;
            tabButtons[each.ordinal()] = addRenderableWidget(button);
        }
        rowButtons.clear();
        for (int index = 0; index < layout.visibleRows() && rowScroll + index < rows.size(); index++) {
            Row row = rows.get(rowScroll + index);
            Box box = layout.row(index);
            AnimatedPurpleButton button = new AnimatedPurpleButton(box.x(), box.y(), box.width(), box.height(),
                    Component.translatable(row.key()),
                    () -> row.feature() != null ? HerziumConfig.get().on(row.feature())
                            : row.order() != null && HerziumConfig.get().hotbarOrder() == row.order(),
                    pressed -> press(row), row.feature() != null);
            if (row.feature() == null) button.labelInset = 8;
            rowButtons.add(addRenderableWidget(button));
        }
        Box done = layout.done();
        addRenderableWidget(new AnimatedPurpleButton(done.x(), done.y(), done.width(), done.height(),
                CommonComponents.GUI_DONE, () -> false, pressed -> onClose()));
        refresh();
        if (!rows.isEmpty()) explain(topic != null && rows.contains(topic) ? topic : rows.get(0));
    }

    /** Where the KoHs page draws: the room of the switches and of their explanation together. */
    private KohsKit.Rect kohsBody() {
        Box list = layout.list();
        Box info = layout.info();
        int right = info.empty() ? list.right() : Math.max(list.right(), info.right());
        int bottom = info.empty() ? list.bottom() : Math.max(list.bottom(), info.bottom());
        return new KohsKit.Rect(list.x(), list.y(), right - list.x(), bottom - list.y());
    }

    private void open(Tab next) {
        if (next == tab) return;
        tab = next;
        rowScroll = 0;
        topic = null;
        wipeAt = millis();
        if (next == Tab.KOHS) kohs.enter();
        rebuildWidgets();
    }

    private void press(Row row) {
        if (row.feature() != null) HerziumConfig.get().toggle(row.feature());
        else HerziumConfig.get().setHotbarOrder(row.order());
        refresh();
        explain(row);
    }

    /** A switch that cannot act in the current order, or without the one it depends on, is shown resting. */
    private void refresh() {
        for (int index = 0; index < rowButtons.size(); index++) {
            Feature feature = rows.get(rowScroll + index).feature();
            rowButtons.get(index).active = feature == null || blockedBy(feature) == null;
        }
    }

    private void explain(Row row) {
        topic = row;
        infoScroll = 0;
        int room = Math.max(1, layout.info().width() - 16);
        Component text = Component.translatable(row.key() + ".description");
        if (row.order() != null) {
            text = text.copy().append("\n\n").append(Component.translatable(row.order() == HotbarOrder.VANILLA
                    ? "herzium.config.vanilla_note" : "herzium.config.alternate_note"));
        }
        String blocked = row.feature() == null ? null : blockedBy(row.feature());
        note = blocked == null ? List.of() : font.split(Component.translatable(blocked), room);
        body = font.split(text, room);
    }

    /** The explanation follows the control under the pointer, or the focused one. */
    private void followPointer() {
        for (int index = 0; index < rowButtons.size(); index++) {
            Row row = rows.get(rowScroll + index);
            if (rowButtons.get(index).isHoveredOrFocused() && !row.equals(topic)) explain(row);
        }
    }

    /** The live numbers of the Monitor tab. Rebuilt four times a second, not once a frame. */
    private void refreshStats(long now) {
        if (now - statsAt < 250L && !stats.isEmpty()) return;
        statsAt = now;
        stats.clear();
        // Wrapped like the text above them: a long translation goes on the next line, never off the panel.
        int room = Math.max(1, layout.info().width() - 16);
        for (Component line : new Component[] {
                Component.translatable("herzium.monitor.frame", ms(ResponseMeter.lastFrameMs()), ms(ResponseMeter.averageFrameMs())),
                Component.translatable("herzium.monitor.tick", ms(ResponseMeter.lastTickMs()), ms(ResponseMeter.averageTickMs())),
                Component.translatable("herzium.monitor.samples", ResponseMeter.samples()),
                Component.translatable("herzium.monitor.paired", HotbarOrderController.pairedSlips()),
                null,
                Component.translatable("herzium.monitor.ticks", ShapeMonitor.ticks()),
                Component.translatable("herzium.monitor.slot_after_click", ShapeMonitor.slotAfterClick()),
                Component.translatable("herzium.monitor.repeated_slot", ShapeMonitor.repeatedSlot()),
                Component.translatable("herzium.monitor.double_placement", ShapeMonitor.doublePlacement())}) {
            if (line == null) stats.add(FormattedCharSequence.EMPTY);
            else stats.addAll(font.split(line, room));
        }
    }

    private static String ms(float value) {
        return value < 0.0F ? "-" : String.format(Locale.ROOT, "%.1f", value);
    }

    private static long millis() { return System.nanoTime() / 1_000_000L; }

    private static int iconSize(Box box) {
        return Math.min(16, Math.min(box.width(), box.height()) - 2);
    }

    private int infoContentHeight() {
        int lines = note.size() + body.size() + (tab == Tab.MONITOR ? stats.size() + 1 : 0);
        return lines * LINE + (note.isEmpty() ? 0 : 4);
    }

    private int infoViewHeight() { return layout.info().height() - 26; }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        HerziumTheme.backdrop(graphics, width, height);
        graphics.fill(0, 0, width, height, 0x66080410);
        particles.render(graphics, width, height, mouseX, mouseY, millis());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = millis();
        followPointer();
        Box panel = layout.panel();
        graphics.nextStratum();
        HerziumTheme.fillRounded(graphics, panel.x(), panel.y(), panel.width(), panel.height(),
                HerziumTheme.PANEL_TOP, HerziumTheme.PANEL_BOTTOM);
        HerziumTheme.drawOutline(graphics, panel.x(), panel.y(), panel.width(), panel.height(),
                HerziumTheme.pulsingBorder(now, 96, 70));
        if (layout.titleVisible()) drawHeader(graphics, panel, now);
        Box list = layout.list();
        HerziumTheme.drawScrollbar(graphics, list.x() + 7, list.y(), list.width(), list.height(), rowScroll,
                Math.max(0, rows.size() - layout.visibleRows()));
        if (tab == Tab.KOHS) kohs.render(graphics, font, kohsBody(), mouseX, mouseY, 1.0F);
        else drawInfo(graphics, now);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        for (Tab each : Tab.values()) drawIcon(graphics, each, now);
        graphics.nextStratum();
        drawWipe(graphics, now);
        drawEntrance(graphics, panel, now);
    }

    /** The title, the version, and under them the speed line: a wave with a pulse running along it. */
    private void drawHeader(GuiGraphicsExtractor graphics, Box panel, long now) {
        int left = panel.x() + 10;
        int right = panel.right() - 10;
        graphics.text(font, title, left, panel.y() + 8, HerziumTheme.TEXT_TITLE);
        if (right - left > font.width(title) + font.width(version) + 12) {
            graphics.text(font, version, right - font.width(version), panel.y() + 8, HerziumTheme.TEXT_MUTED);
        }
        int y = panel.y() + 22;
        int span = Math.max(1, right - left);
        int pulse = left + (int) (now * span / 1400L % span);
        for (int x = left; x < right; x += 6) {
            int lift = Math.round((float) Math.sin(x * 0.07F - now / 160.0F) * 1.4F);
            int near = Math.max(0, 60 - Math.abs(x - pulse));
            graphics.fill(x, y + lift, Math.min(right, x + 6), y + lift + 1,
                    HerziumTheme.argb(70 + near * 3, 169 + near, 116, 240));
        }
    }

    private void drawInfo(GuiGraphicsExtractor graphics, long now) {
        Box info = layout.info();
        if (info.empty() || topic == null) return;
        HerziumTheme.fillRounded(graphics, info.x(), info.y(), info.width(), info.height(),
                HerziumTheme.PANE_TOP, HerziumTheme.PANE_BOTTOM);
        HerziumTheme.drawOutline(graphics, info.x(), info.y(), info.width(), info.height(), HerziumTheme.PANE_OUTLINE);
        if (info.height() < 40) return;
        int x = info.x() + 8;
        graphics.enableScissor(info.x() + 2, info.y() + 2, info.right() - 2, info.bottom() - 2);
        graphics.text(font, Component.translatable(topic.key()), x, info.y() + 7, HerziumTheme.TEXT_ACCENT);
        HerziumTheme.hairline(graphics, x, info.y() + 19, info.width() - 16);
        graphics.disableScissor();

        int top = info.y() + 23;
        int maxScroll = Math.max(0, infoContentHeight() - infoViewHeight());
        infoScroll = Mth.clamp(infoScroll, 0, maxScroll);
        graphics.enableScissor(info.x() + 2, top, info.right() - 2, info.bottom() - 3);
        int y = top - infoScroll;
        for (FormattedCharSequence line : note) {
            graphics.text(font, line, x, y, HerziumTheme.TEXT_WARN);
            y += LINE;
        }
        if (!note.isEmpty()) y += 4;
        for (FormattedCharSequence line : body) {
            graphics.text(font, line, x, y, HerziumTheme.TEXT_BODY);
            y += LINE;
        }
        if (tab == Tab.MONITOR) {
            refreshStats(now);
            y += LINE;
            for (FormattedCharSequence line : stats) {
                graphics.text(font, line, x, y, HerziumTheme.TEXT_ACCENT);
                y += LINE;
            }
        }
        graphics.disableScissor();
        HerziumTheme.drawScrollbar(graphics, info.x(), top, info.width(), info.bottom() - 3 - top, infoScroll, maxScroll);
    }

    /** A tab's mark is the game's own art, and it never rests: the clock runs, the compass hunts, the rest float. */
    private void drawIcon(GuiGraphicsExtractor graphics, Tab each, long now) {
        Box box = layout.tab(each.ordinal());
        int size = iconSize(box);
        if (size < 6) return;
        boolean lively = tab == each || tabButtons[each.ordinal()].isHoveredOrFocused();
        Identifier icon = switch (each) {
            case ORDER -> COMPASS[lively ? (int) (now / 45L % 32L)
                    : Math.floorMod(Math.round((float) Math.sin(now / 600.0F) * 3.0F), 32)];
            case FEEL -> FEATHER;
            case MONITOR -> CLOCK[(int) (now / (lively ? 30L : 90L) % 64L)];
            case KOHS -> ModIconLoader.iconFor("herzium");
        };
        if (icon == null) icon = FEATHER;
        int room = (box.height() - size) / 2;
        int bob = Math.round((float) Math.sin(now / (lively ? 110.0F : 420.0F) + each.ordinal() * 1.3F) * Math.min(room, lively ? 2 : 1));
        int x = layout.railLabels() ? box.x() + 5 : box.x() + (box.width() - size) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, x, box.y() + room + bob, 0.0F, 0.0F, size, size, size, size,
                lively ? 0xFFFFFFFF : 0xB4FFFFFF);
    }

    /** Changing tab: a slanted blade crosses the page and the new one is what it leaves behind. */
    private void drawWipe(GuiGraphicsExtractor graphics, long now) {
        long elapsed = now - wipeAt;
        if (wipeAt == 0L || elapsed >= WIPE_MILLIS) return;
        Box list = layout.list();
        Box info = layout.info();
        int left = list.x();
        int right = info.empty() ? list.right() : Math.max(list.right(), info.right());
        int top = list.y();
        int bottom = info.empty() ? list.bottom() : Math.max(list.bottom(), info.bottom());
        float progress = elapsed / (float) WIPE_MILLIS;
        progress = 1.0F - (1.0F - progress) * (1.0F - progress);
        int slant = Math.min(48, (bottom - top) / 2);
        int slices = 8;
        for (int slice = 0; slice < slices; slice++) {
            int y0 = top + (bottom - top) * slice / slices;
            int y1 = top + (bottom - top) * (slice + 1) / slices;
            int edge = left - slant + Math.round((right - left + slant + 20) * progress) + slant * (slices - slice) / slices;
            int from = Mth.clamp(edge, left, right);
            graphics.fill(from, y0, right, y1, 0xFF160D22);
            graphics.fill(Mth.clamp(edge - 3, left, right), y0, from, y1, 0xFFE9C4FF);
            graphics.fill(Mth.clamp(edge - 22, left, right), y0, Mth.clamp(edge - 3, left, right), y1, 0x669A4BE8);
        }
    }

    /** First opening of the session: the panel is cut open from its middle. */
    private void drawEntrance(GuiGraphicsExtractor graphics, Box panel, long now) {
        long elapsed = now - openedAt;
        if (elapsed >= ENTRANCE_MILLIS) return;
        float progress = elapsed / (float) ENTRANCE_MILLIS;
        progress = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
        int half = panel.width() / 2 + 1;
        int open = Math.round(half * progress);
        int middle = panel.x() + panel.width() / 2;
        int alpha = Math.round(255 * (1.0F - progress));
        graphics.fill(panel.x(), panel.y(), middle - open, panel.bottom(), 0xFF0B0612);
        graphics.fill(middle + open, panel.y(), panel.right(), panel.bottom(), 0xFF0B0612);
        graphics.fill(Math.max(panel.x(), middle - open - 2), panel.y(), middle - open, panel.bottom(),
                HerziumTheme.argb(alpha, 233, 196, 255));
        graphics.fill(middle + open, panel.y(), Math.min(panel.right(), middle + open + 2), panel.bottom(),
                HerziumTheme.argb(alpha, 233, 196, 255));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tab == Tab.KOHS && kohs.mouseClicked(font, kohsBody(), event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (layout.info().contains(mouseX, mouseY)) {
            infoScroll = Mth.clamp(infoScroll - (int) Math.round(vertical * 20), 0,
                    Math.max(0, infoContentHeight() - infoViewHeight()));
            return true;
        }
        int maxScroll = Math.max(0, rows.size() - layout.visibleRows());
        if (maxScroll > 0 && layout.list().contains(mouseX, mouseY)) {
            int next = Mth.clamp(rowScroll - (int) Math.signum(vertical), 0, maxScroll);
            if (next != rowScroll) {
                rowScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}
