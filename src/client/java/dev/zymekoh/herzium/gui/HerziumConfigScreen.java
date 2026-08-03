package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class HerziumConfigScreen extends Screen {
    private static final Component TITLE = Component.translatable("herzium.config.title");
    private static final Component SUBTITLE = Component.translatable("herzium.config.subtitle");
    private static final Component KOHSIUM_SUBTITLE = Component.translatable("herzium.config.subtitle.kohsium");
    private static final Component IMMEDIATE_HOTBAR_OPTION =
            Component.translatable("herzium.option.immediate_hotbar_selection");
    private static final Component IMMEDIATE_HOTBAR_DESCRIPTION =
            Component.translatable("herzium.option.immediate_hotbar_selection.description");

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int headerHeight;
    private int footerHeight;
    private int firstCardX;
    private int firstCardY;
    private int cardWidth;
    private int cardHeight;
    private int firstOptionTextX;
    private int firstOptionTextY;
    private int optionTextWidth;
    private int immediateHotbarTitleLineLimit;
    private int optionDescriptionLineLimit;

    public HerziumConfigScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.calculateLayout();
        HerziumConfig config = HerziumConfig.get();
        int desiredButtonHeight = this.cardHeight < 48 ? 18 : this.cardHeight < 62 ? 20 : 26;
        int compactButtonHeight = Math.min(desiredButtonHeight, Math.max(1, this.cardHeight - 6));
        int maxToggleWidth = Math.max(1, this.cardWidth - Math.min(72, Math.max(1, this.cardWidth / 2)));
        int minToggleWidth = Math.min(58, maxToggleWidth);
        int toggleWidth = Mth.clamp(Math.min(124, this.cardWidth / 3), minToggleWidth, maxToggleWidth);
        int controlInset = Math.min(10, Math.max(3, this.cardWidth / 24));
        int firstToggleX = this.firstCardX + this.cardWidth - toggleWidth - controlInset;
        int firstToggleY = this.firstCardY + (this.cardHeight - compactButtonHeight) / 2;

        int optionTextInset = Math.min(12, Math.max(4, this.cardWidth / 24));
        this.firstOptionTextX = this.firstCardX + optionTextInset;
        this.optionTextWidth = Math.max(1, firstToggleX - this.firstOptionTextX - 8);
        int textInsetY = this.cardHeight < 48 ? 3 : 6;
        this.firstOptionTextY = this.firstCardY + textInsetY;

        int availableTextHeight = Math.max(0, this.cardHeight - textInsetY * 2);
        int availableLineSlots = availableTextHeight / 10;
        boolean descriptionFits = this.cardHeight >= 56 && this.optionTextWidth >= 92;
        int preferredTitleLines = 2;
        this.immediateHotbarTitleLineLimit = Math.min(
                this.font.split(IMMEDIATE_HOTBAR_OPTION, Math.max(24, this.optionTextWidth)).size(),
                Math.min(preferredTitleLines, availableLineSlots));
        int descriptionSpace = availableTextHeight - this.immediateHotbarTitleLineLimit * 10 - 3;
        this.optionDescriptionLineLimit = descriptionFits
                ? Math.min(2, Math.max(0, descriptionSpace / 10))
                : 0;

        AnimatedPurpleButton hotbarButton = this.addRenderableWidget(new AnimatedPurpleButton(
                firstToggleX,
                firstToggleY,
                toggleWidth,
                compactButtonHeight,
                stateText(config.immediateHotbarSelection()),
                config::immediateHotbarSelection,
                button -> {
                    boolean enabled = !config.immediateHotbarSelection();
                    config.setImmediateHotbarSelection(enabled);
                    button.setMessage(stateText(enabled));
                }));
        hotbarButton.setTooltip(Tooltip.create(optionTooltip(IMMEDIATE_HOTBAR_OPTION, IMMEDIATE_HOTBAR_DESCRIPTION)));

        int doneWidth = Math.min(180, Math.max(1, this.panelWidth - 8));
        int doneHeight = this.panelHeight < 205 ? 20 : 24;
        int doneY = this.panelY + this.panelHeight - this.footerHeight
                + Math.max(4, (this.footerHeight - doneHeight) / 2);
        this.addRenderableWidget(new AnimatedPurpleButton(
                this.panelX + (this.panelWidth - doneWidth) / 2,
                doneY,
                doneWidth,
                doneHeight,
                Component.translatable("gui.done"),
                () -> false,
                button -> this.onClose()));
    }

    private void calculateLayout() {
        int horizontalMargin = this.width < 360 ? 6 : this.width < 640 ? 14 : 36;
        int verticalMargin = this.height < 220 ? 6 : this.height < 360 ? 14 : 36;
        int maxPanelWidth = Math.max(1, this.width - horizontalMargin * 2);
        int maxPanelHeight = Math.max(1, this.height - verticalMargin * 2);
        int minPanelWidth = Math.min(250, maxPanelWidth);
        int minPanelHeight = Math.min(132, maxPanelHeight);
        int preferredWidth = this.width < 540 ? maxPanelWidth : 500;
        int preferredHeight = 174;

        this.panelWidth = Mth.clamp(preferredWidth, minPanelWidth, maxPanelWidth);
        this.panelHeight = Mth.clamp(preferredHeight, minPanelHeight, maxPanelHeight);
        this.panelX = (this.width - this.panelWidth) / 2;
        this.panelY = (this.height - this.panelHeight) / 2;
        this.headerHeight = this.panelHeight < 150 ? 30 : 48;
        this.footerHeight = this.panelHeight < 150 ? 24 : 34;

        int contentPadding = Math.min(this.panelWidth / 4, this.panelWidth < 420 ? 10 : 14);
        int bodyTop = this.panelY + this.headerHeight;
        int footerTop = this.panelY + this.panelHeight - this.footerHeight;
        int verticalPadding = this.panelHeight < 150 ? 3 : 6;
        int cardAreaX = this.panelX + contentPadding;
        int cardAreaWidth = Math.max(1, this.panelWidth - contentPadding * 2);
        int cardAreaTop = bodyTop + verticalPadding;
        int cardAreaHeight = Math.max(1, footerTop - verticalPadding - cardAreaTop);
        this.cardWidth = cardAreaWidth;
        this.cardHeight = Math.min(70, cardAreaHeight);
        this.firstCardX = cardAreaX;
        this.firstCardY = cardAreaTop + Math.max(0, (cardAreaHeight - this.cardHeight) / 2);
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (this.minecraft.level == null) {
            this.extractPanorama(graphics, partialTick);
        }

        graphics.fillGradient(0, 0, this.width, this.height, 0x62080412, 0x780C0518);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        long time = System.nanoTime() / 1_000_000L;
        float pulse = 0.5F + 0.5F * (float) Math.sin(time / 420.0F);
        int borderAlpha = 150 + (int) (pulse * 70.0F);

        fillRounded(
                graphics,
                this.panelX,
                this.panelY,
                this.panelWidth,
                this.panelHeight,
                0x9C130A21,
                0xB20B0614);
        drawOutline(
                graphics,
                this.panelX + 1,
                this.panelY + 1,
                this.panelWidth - 2,
                this.panelHeight - 2,
                argb(borderAlpha, 153, 58, 222));

        int titleY = this.panelY + (this.headerHeight < 40 ? 8 : 11);
        graphics.centeredText(this.font, TITLE, this.panelX + this.panelWidth / 2, titleY, 0xFFF0DCFF);
        if (this.headerHeight >= 46) {
            graphics.centeredText(
                    this.font,
                    KoHsiumIntegration.present() ? KOHSIUM_SUBTITLE : SUBTITLE,
                    this.panelX + this.panelWidth / 2,
                    titleY + 16,
                    0xFFB89ACF);
        }

        this.drawOptionCard(
                graphics,
                this.firstCardX,
                this.firstCardY,
                this.firstOptionTextX,
                this.firstOptionTextY,
                IMMEDIATE_HOTBAR_OPTION,
                IMMEDIATE_HOTBAR_DESCRIPTION,
                this.immediateHotbarTitleLineLimit);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawOptionCard(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int textX,
            int textY,
            Component title,
            Component description,
            int titleLineLimit) {
        fillRounded(
                graphics,
                x,
                y,
                this.cardWidth,
                this.cardHeight,
                0x721F1031,
                0x8812091F);
        drawOutline(
                graphics,
                x + 1,
                y + 1,
                this.cardWidth - 2,
                this.cardHeight - 2,
                0x8E7135A3);

        int titleLines = drawWrapped(
                graphics,
                title,
                textX,
                textY,
                this.optionTextWidth,
                0xFFF1E6FF,
                titleLineLimit);

        if (this.optionDescriptionLineLimit > 0) {
            drawWrapped(
                    graphics,
                    description,
                    textX,
                    textY + titleLines * 10 + 3,
                    this.optionTextWidth,
                    0xFFC1A7D2,
                    this.optionDescriptionLineLimit);
        }
    }

    private int drawWrapped(
            GuiGraphicsExtractor graphics,
            Component text,
            int x,
            int y,
            int width,
            int color,
            int maxLines) {
        List<FormattedCharSequence> lines = this.font.split(text, Math.max(24, width));
        int count = Math.min(lines.size(), maxLines);
        for (int index = 0; index < count; index++) {
            graphics.text(this.font, lines.get(index), x, y + index * 10, color);
        }
        return count;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return this.parent != null && this.parent.isPauseScreen();
    }

    private static Component stateText(boolean enabled) {
        return Component.translatable(enabled ? "herzium.state.enabled" : "herzium.state.disabled");
    }

    private static Component optionTooltip(Component title, Component description) {
        return Component.empty().append(title).append("\n").append(description);
    }

    private static void fillRounded(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int topColor,
            int bottomColor) {
        graphics.fillGradient(x + 3, y, x + width - 3, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + 3, x + width, y + height - 3, topColor, bottomColor);
    }

    private static void drawOutline(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
