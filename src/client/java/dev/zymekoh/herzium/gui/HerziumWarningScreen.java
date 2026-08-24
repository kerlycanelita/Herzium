package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.config.HerziumConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/** Startup advisory shown once after the initial resource reload completes. */
public final class HerziumWarningScreen extends Screen {
    private static final Component TITLE =
            Component.translatable("herzium.warning.title");
    private static final Component MESSAGE =
            Component.translatable("herzium.warning.message");
    private static final Component QUOTE =
            Component.translatable("herzium.warning.quote").withStyle(ChatFormatting.ITALIC);
    private final Runnable continuation;
    private final HerziumTheme.ParticleField particles = new HerziumTheme.ParticleField();
    private List<FormattedCharSequence> messageLines = List.of();
    private List<FormattedCharSequence> quoteLines = List.of();
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int footerHeight;
    private int bodyX;
    private int bodyTop;
    private int bodyWidth;
    private int bodyHeight;
    private int maxScroll;
    private int scrollOffset;
    private boolean completed;

    public HerziumWarningScreen(Runnable continuation) {
        super(TITLE);
        this.continuation = continuation;
    }

    @Override
    protected void init() {
        this.calculateLayout();
        this.prepareText();
        this.particles.resize(this.width, this.height);
        this.addButtons();
    }

    private void calculateLayout() {
        int horizontalMargin = Mth.clamp(this.width / 24, 4, 24);
        int verticalMargin = Mth.clamp(this.height / 24, 4, 18);
        int maxPanelWidth = Math.max(1, this.width - horizontalMargin * 2);
        int maxPanelHeight = Math.max(1, this.height - verticalMargin * 2);
        int minPanelWidth = Math.min(280, maxPanelWidth);
        int minPanelHeight = Math.min(170, maxPanelHeight);
        int preferredWidth = this.width < 650 ? maxPanelWidth : 570;
        int preferredHeight = this.height < 320 ? maxPanelHeight : 286;

        this.panelWidth = Mth.clamp(preferredWidth, minPanelWidth, maxPanelWidth);
        this.panelHeight = Mth.clamp(preferredHeight, minPanelHeight, maxPanelHeight);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.headerHeight = this.panelHeight < 150 ? 31 : this.panelHeight < 210 ? 42 : 56;
        this.footerHeight = this.panelHeight < 150 ? 48 : 58;

        int contentMargin = Mth.clamp(this.panelWidth / 28, 6, 16);
        this.bodyX = this.panelX + contentMargin;
        this.bodyTop = this.panelY + this.headerHeight;
        this.bodyWidth = Math.max(1, this.panelWidth - contentMargin * 2);
        this.bodyHeight = Math.max(
                1,
                this.panelY + this.panelHeight - this.footerHeight - this.bodyTop);
    }

    private void prepareText() {
        int textWidth = Math.max(24, this.bodyWidth - 18);
        this.messageLines = this.font.split(MESSAGE, textWidth);
        this.quoteLines = this.font.split(QUOTE, textWidth);
        int contentHeight = this.messageLines.size() * 10
                + 10
                + this.quoteLines.size() * 10;
        this.maxScroll = Math.max(0, contentHeight - Math.max(1, this.bodyHeight - 12));
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScroll);
    }

    private void addButtons() {
        int footerTop = this.panelY + this.panelHeight - this.footerHeight;
        int gap = this.panelWidth < 330 ? 4 : 8;
        int buttonHeight = this.panelHeight < 150 ? 18 : 22;
        int availableWidth = Math.max(1, this.panelWidth - 24);
        boolean stacked = availableWidth < 286;

        AnimatedPurpleButton continueButton;
        if (stacked) {
            int buttonWidth = Math.min(220, availableWidth);
            int totalHeight = buttonHeight * 2 + gap;
            int x = this.panelX + (this.panelWidth - buttonWidth) / 2;
            int y = footerTop + Math.max(2, (this.footerHeight - totalHeight) / 2);
            continueButton = this.addRenderableWidget(this.createContinueButton(x, y, buttonWidth, buttonHeight));
            this.addRenderableWidget(this.createQuitButton(x, y + buttonHeight + gap, buttonWidth, buttonHeight));
        } else {
            int groupWidth = Math.min(430, availableWidth);
            int buttonWidth = Math.max(1, (groupWidth - gap) / 2);
            int x = this.panelX + (this.panelWidth - groupWidth) / 2;
            int y = footerTop + Math.max(2, (this.footerHeight - buttonHeight) / 2);
            continueButton = this.addRenderableWidget(this.createContinueButton(x, y, buttonWidth, buttonHeight));
            this.addRenderableWidget(this.createQuitButton(x + buttonWidth + gap, y, buttonWidth, buttonHeight));
        }

        this.setInitialFocus(continueButton);
    }

    private AnimatedPurpleButton createContinueButton(int x, int y, int width, int height) {
        return new AnimatedPurpleButton(
                x,
                y,
                width,
                height,
                Component.translatable("herzium.warning.continue"),
                () -> true,
                button -> this.continueToMinecraft());
    }

    private AnimatedPurpleButton createQuitButton(int x, int y, int width, int height) {
        return new AnimatedPurpleButton(
                x,
                y,
                width,
                height,
                Component.translatable("herzium.warning.quit"),
                () -> false,
                button -> this.minecraft.stop());
    }

    private void continueToMinecraft() {
        if (this.completed) {
            return;
        }

        this.completed = true;
        HerziumConfig.get().acknowledgeStartupWarning();
        this.continuation.run();
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // Opaque here, unlike the config screen: this runs before the title
        // screen exists, so there is nothing behind it worth showing through.
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF1C0838, 0xFF0A0316);
        graphics.fillGradient(0, 0, this.width, Math.max(1, this.height / 2), 0x8A3A1160, 0x003A1160);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        long now = System.nanoTime() / 1_000_000L;
        // Same order as the config screen: field, surfaces, then text.
        this.particles.render(graphics, this.width, this.height, mouseX, mouseY, now);
        this.drawPanel(graphics, now);
        this.drawBody(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, long now) {
        HerziumTheme.fillRounded(
                graphics,
                this.panelX,
                this.panelY,
                this.panelWidth,
                this.panelHeight,
                0xE02A0F49,
                0xEA150723);

        // Faster than the config screen's breathing border on purpose: this
        // screen is an advisory and is meant to read as urgent.
        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 180.0F);
        int borderAlpha = 185 + Math.round(pulse * 60.0F);
        HerziumTheme.drawOutline(
                graphics,
                this.panelX + 1,
                this.panelY + 1,
                this.panelWidth - 2,
                this.panelHeight - 2,
                HerziumTheme.argb(borderAlpha, 205, 105, 255));

        int warningSize = this.headerHeight < 45 ? 15 : 20;
        int warningX = this.panelX + Mth.clamp(this.panelWidth / 24, 6, 14);
        int warningY = this.panelY + (this.headerHeight - warningSize) / 2;
        HerziumTheme.fillRounded(graphics, warningX, warningY, warningSize, warningSize, 0xE09124C2, 0xDB4B0B72);
        graphics.centeredText(
                this.font,
                Component.literal("!"),
                warningX + warningSize / 2,
                warningY + Math.max(2, (warningSize - 9) / 2),
                0xFFFFFFFF);

        int titleX = this.panelX + this.panelWidth / 2;
        int titleY = this.panelY + Math.max(7, (this.headerHeight - 9) / 2);
        graphics.centeredText(this.font, TITLE, titleX, titleY, HerziumTheme.TEXT_TITLE);

        HerziumTheme.fillRounded(
                graphics,
                this.bodyX,
                this.bodyTop,
                this.bodyWidth,
                this.bodyHeight,
                HerziumTheme.PANE_TOP,
                HerziumTheme.PANE_BOTTOM);
        HerziumTheme.drawOutline(
                graphics,
                this.bodyX,
                this.bodyTop,
                this.bodyWidth,
                this.bodyHeight,
                HerziumTheme.PANE_OUTLINE);
    }

    private void drawBody(GuiGraphicsExtractor graphics) {
        int textX = this.bodyX + 7;
        int textY = this.bodyTop + 6 - this.scrollOffset;
        int clipRight = this.bodyX + this.bodyWidth;
        int clipBottom = this.bodyTop + this.bodyHeight;
        graphics.enableScissor(this.bodyX + 1, this.bodyTop + 1, clipRight - 1, clipBottom - 1);

        for (FormattedCharSequence line : this.messageLines) {
            graphics.text(this.font, line, textX, textY, HerziumTheme.TEXT_PRIMARY);
            textY += 10;
        }

        textY += 10;
        for (FormattedCharSequence line : this.quoteLines) {
            graphics.text(this.font, line, textX, textY, HerziumTheme.TEXT_ACCENT);
            textY += 10;
        }
        graphics.disableScissor();

        HerziumTheme.drawScrollbar(
                graphics,
                this.bodyX,
                this.bodyTop,
                this.bodyWidth,
                this.bodyHeight,
                this.scrollOffset,
                this.maxScroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= this.bodyX
                && mouseX <= this.bodyX + this.bodyWidth
                && mouseY >= this.bodyTop
                && mouseY <= this.bodyTop + this.bodyHeight
                && this.maxScroll > 0) {
            this.scrollOffset = Mth.clamp(
                    this.scrollOffset - (int) Math.round(scrollY * 18.0),
                    0,
                    this.maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
