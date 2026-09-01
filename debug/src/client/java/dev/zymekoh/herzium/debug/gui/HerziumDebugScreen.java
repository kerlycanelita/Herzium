package dev.zymekoh.herzium.debug.gui;

import dev.zymekoh.herzium.debug.DebugCollector;
import dev.zymekoh.herzium.debug.DebugCollector.DebugEvent;
import dev.zymekoh.herzium.debug.DebugCollector.Level;
import dev.zymekoh.herzium.debug.DebugCollector.StatusSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Responsive, read-only live diagnostics for Herzium.
 *
 * <p>Freezing this view only freezes its rendered snapshot. Collection and the
 * persistent log writer continue normally in the background.</p>
 */
public final class HerziumDebugScreen extends Screen {
    private static final int PANEL = 0xE612071F;
    private static final int SURFACE = 0xD91D0D2A;
    private static final int SURFACE_ALT = 0xB92A103B;
    private static final int BORDER = 0xB98C4CC8;
    private static final int ACCENT = 0xFFBC72FF;
    private static final int TEXT = 0xFFF0E8FA;
    private static final int MUTED = 0xFFB8A8C8;
    private static final int GOOD = 0xFF75E6A7;
    private static final int WARN = 0xFFFFCD72;
    private static final int ERROR = 0xFFFF738D;
    private static final int LINE_HEIGHT = 10;

    private final Screen parent;
    private boolean frozen;
    private List<DebugEvent> frozenEvents = List.of();
    private List<DisplayLine> displayLines = List.of();
    private long cachedSequence = Long.MIN_VALUE;
    private int cachedTextWidth = -1;
    private int scrollFromBottom;
    private long copiedUntilNanos;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int contentX;
    private int contentWidth;
    private int logTop;
    private int logBottom;
    private int footerTop;
    private Button copyButton;
    private Button freezeButton;

    public HerziumDebugScreen(Screen parent) {
        super(Component.translatable("herzium_debug.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = Mth.clamp(Math.min(this.width, this.height) / 28, 6, 16);
        this.panelWidth = Math.min(900, Math.max(1, this.width - margin * 2));
        this.panelHeight = Math.min(560, Math.max(1, this.height - margin * 2));
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.contentX = this.panelX + 10;
        this.contentWidth = Math.max(1, this.panelWidth - 20);

        boolean compact = this.contentWidth < 500;
        int footerRows = compact ? 2 : 1;
        int buttonHeight = 20;
        int rowGap = 4;
        int footerHeight = footerRows * buttonHeight + (footerRows - 1) * rowGap + 12;
        this.footerTop = this.panelY + this.panelHeight - footerHeight;
        this.logTop = this.panelY + 68;
        this.logBottom = Math.max(this.logTop + LINE_HEIGHT, this.footerTop - 8);

        int gap = 4;
        int columns = compact ? 2 : 4;
        int buttonWidth = Math.max(40, (this.contentWidth - gap * (columns - 1)) / columns);
        int firstY = this.footerTop + 6;

        this.copyButton = this.addRenderableWidget(Button.builder(
                Component.translatable("herzium_debug.screen.copy"),
                button -> this.copyReport(button)).bounds(
                        this.contentX,
                        firstY,
                        buttonWidth,
                        buttonHeight).build());

        int markerColumn = compact ? 1 : 1;
        int markerRow = 0;
        this.addRenderableWidget(Button.builder(
                Component.translatable("herzium_debug.screen.marker"),
                button -> DebugCollector.addUserMarker()).bounds(
                        this.contentX + markerColumn * (buttonWidth + gap),
                        firstY + markerRow * (buttonHeight + rowGap),
                        buttonWidth,
                        buttonHeight).build());

        int freezeColumn = compact ? 0 : 2;
        int freezeRow = compact ? 1 : 0;
        this.freezeButton = this.addRenderableWidget(Button.builder(
                this.freezeLabel(),
                button -> this.toggleFreeze()).bounds(
                        this.contentX + freezeColumn * (buttonWidth + gap),
                        firstY + freezeRow * (buttonHeight + rowGap),
                        buttonWidth,
                        buttonHeight).build());

        int closeColumn = compact ? 1 : 3;
        int closeRow = compact ? 1 : 0;
        this.addRenderableWidget(Button.builder(
                Component.translatable("herzium_debug.screen.close"),
                button -> this.onClose()).bounds(
                        this.contentX + closeColumn * (buttonWidth + gap),
                        firstY + closeRow * (buttonHeight + rowGap),
                        buttonWidth,
                        buttonHeight).build());

        this.cachedTextWidth = -1;
        this.rebuildLinesIfNeeded();
    }

    private void copyReport(Button button) {
        Minecraft.getInstance().keyboardHandler.setClipboard(DebugCollector.fullReport());
        DebugCollector.info("UI", "Full report copied to clipboard");
        this.copiedUntilNanos = System.nanoTime() + 1_400_000_000L;
        button.setMessage(Component.translatable("herzium_debug.screen.copied"));
    }

    private void toggleFreeze() {
        this.frozen = !this.frozen;
        if (this.frozen) {
            this.frozenEvents = DebugCollector.snapshot(6_000);
        } else {
            this.frozenEvents = List.of();
            this.scrollFromBottom = 0;
        }
        this.cachedSequence = Long.MIN_VALUE;
        this.freezeButton.setMessage(this.freezeLabel());
        DebugCollector.info("UI", this.frozen ? "Viewer frozen; collection remains active" : "Viewer resumed live");
    }

    private Component freezeLabel() {
        return Component.translatable(this.frozen
                ? "herzium_debug.screen.live"
                : "herzium_debug.screen.freeze");
    }

    private void rebuildLinesIfNeeded() {
        long sequence = this.frozen
                ? (this.frozenEvents.isEmpty() ? -1 : this.frozenEvents.getLast().sequence())
                : DebugCollector.latestSequence();
        int textWidth = Math.max(40, this.contentWidth - 14);
        if (sequence == this.cachedSequence && textWidth == this.cachedTextWidth) {
            return;
        }

        List<DebugEvent> events = this.frozen ? this.frozenEvents : DebugCollector.snapshot(1_500);
        List<DisplayLine> rebuilt = new ArrayList<>();
        for (DebugEvent event : events) {
            List<FormattedCharSequence> wrapped = this.font.split(
                    Component.literal(event.displayLine()),
                    textWidth);
            int color = colorFor(event.level());
            for (FormattedCharSequence line : wrapped) {
                rebuilt.add(new DisplayLine(line, color));
            }
        }
        this.displayLines = List.copyOf(rebuilt);
        this.cachedSequence = sequence;
        this.cachedTextWidth = textWidth;
        this.clampScroll();
    }

    private void clampScroll() {
        int visible = Math.max(1, (this.logBottom - this.logTop - 8) / LINE_HEIGHT);
        int maximum = Math.max(0, this.displayLines.size() - visible);
        this.scrollFromBottom = Mth.clamp(this.scrollFromBottom, 0, maximum);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.contentX && mouseX < this.contentX + this.contentWidth
                && mouseY >= this.logTop && mouseY < this.logBottom) {
            int direction = scrollY > 0.0 ? 1 : scrollY < 0.0 ? -1 : 0;
            this.scrollFromBottom += direction * 3;
            this.clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 0xC20D0616, 0xD0080310);
        graphics.fillGradient(0, 0, this.width, Math.max(1, this.height / 2), 0x512F0E4A, 0x002F0E4A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.rebuildLinesIfNeeded();
        if (this.copyButton != null && this.copiedUntilNanos != 0L
                && System.nanoTime() >= this.copiedUntilNanos) {
            this.copiedUntilNanos = 0L;
            this.copyButton.setMessage(Component.translatable("herzium_debug.screen.copy"));
        }

        fillPanel(graphics, this.panelX, this.panelY, this.panelWidth, this.panelHeight, PANEL, BORDER);
        graphics.centeredText(this.font, this.title, this.width / 2, this.panelY + 9, TEXT);
        graphics.centeredText(
                this.font,
                Component.translatable("herzium_debug.screen.subtitle"),
                this.width / 2,
                this.panelY + 22,
                MUTED);

        StatusSnapshot status = DebugCollector.status();
        int statusTop = this.panelY + 36;
        fillPanel(graphics, this.contentX, statusTop, this.contentWidth, 26, SURFACE_ALT, BORDER);
        String firstStatus = String.format(
                Locale.ROOT,
                "FPS %.0f  frame %.2f/%.2f ms  ticks %.1f  slot %s  focus %s",
                status.fps(), status.averageFrameMs(), status.maximumFrameMs(),
                status.ticksPerSecond(), status.selectedSlot() < 0 ? "-" : status.selectedSlot() + 1,
                status.focused() ? "yes" : "no");
        String secondStatus = String.format(
                Locale.ROOT,
                "mouse %d  packets %d  HUD hooks %d  checks %d  issues %d",
                status.mouseMoves(), status.relevantPackets(), status.hudHooks(),
                status.staleChecks(), status.issues());
        graphics.text(this.font, trimToWidth(firstStatus, this.contentWidth - 10), this.contentX + 5, statusTop + 4, TEXT, false);
        graphics.text(this.font, trimToWidth(secondStatus, this.contentWidth - 10), this.contentX + 5, statusTop + 14,
                status.issues() == 0 ? GOOD : ERROR, false);

        fillPanel(graphics, this.contentX, this.logTop, this.contentWidth, this.logBottom - this.logTop, SURFACE, BORDER);
        this.drawLog(graphics);

        int privacyY = Math.max(this.logTop, this.footerTop - 7);
        graphics.centeredText(
                this.font,
                Component.translatable("herzium_debug.screen.privacy"),
                this.width / 2,
                privacyY,
                MUTED);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawLog(GuiGraphicsExtractor graphics) {
        int visible = Math.max(1, (this.logBottom - this.logTop - 8) / LINE_HEIGHT);
        int end = Math.max(0, this.displayLines.size() - this.scrollFromBottom);
        int start = Math.max(0, end - visible);
        int y = this.logTop + 4;

        graphics.enableScissor(this.contentX + 2, this.logTop + 2, this.contentX + this.contentWidth - 2, this.logBottom - 2);
        if (this.displayLines.isEmpty()) {
            graphics.text(
                    this.font,
                    Component.translatable("herzium_debug.screen.empty"),
                    this.contentX + 6,
                    y,
                    MUTED,
                    false);
        } else {
            for (int index = start; index < end; index++) {
                DisplayLine line = this.displayLines.get(index);
                graphics.text(this.font, line.text(), this.contentX + 6, y, line.color(), false);
                y += LINE_HEIGHT;
            }
        }
        graphics.disableScissor();

        int total = this.displayLines.size();
        if (total > visible) {
            int trackTop = this.logTop + 3;
            int trackHeight = Math.max(1, this.logBottom - this.logTop - 6);
            int thumbHeight = Math.max(8, trackHeight * visible / total);
            int maximum = total - visible;
            int offsetFromTop = maximum - this.scrollFromBottom;
            int thumbY = trackTop + (trackHeight - thumbHeight) * offsetFromTop / Math.max(1, maximum);
            graphics.fill(this.contentX + this.contentWidth - 4, trackTop,
                    this.contentX + this.contentWidth - 2, trackTop + trackHeight, 0x55401C55);
            graphics.fill(this.contentX + this.contentWidth - 4, thumbY,
                    this.contentX + this.contentWidth - 2, thumbY + thumbHeight, ACCENT);
        }
    }

    private String trimToWidth(String value, int width) {
        return this.font.width(value) <= width ? value : this.font.plainSubstrByWidth(value, Math.max(1, width - 8)) + "…";
    }

    private static void fillPanel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int fill,
            int border) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, border);
        graphics.fill(x, y, x + 1, y + height, border);
        graphics.fill(x + width - 1, y, x + width, y + height, border);
    }

    private static int colorFor(Level level) {
        return switch (level) {
            case TRACE -> MUTED;
            case INFO -> TEXT;
            case WARN -> WARN;
            case ERROR -> ERROR;
        };
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record DisplayLine(FormattedCharSequence text, int color) {
    }
}
