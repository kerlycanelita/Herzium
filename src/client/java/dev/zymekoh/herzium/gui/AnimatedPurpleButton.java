package dev.zymekoh.herzium.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class AnimatedPurpleButton extends AbstractButton {
    private final Consumer<AnimatedPurpleButton> onPress;
    private final BooleanSupplier selected;
    private final boolean toggleStyle;
    /** Where the label starts: -1 centred, -2 none (an icon-only tab), otherwise pixels from the left edge. */
    int labelInset = -1;
    private float hoverProgress;
    private float selectionProgress = Float.NaN;
    private long lastFrameTime = System.nanoTime() / 1_000_000L;

    AnimatedPurpleButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            BooleanSupplier selected,
            Consumer<AnimatedPurpleButton> onPress) {
        this(x, y, width, height, message, selected, onPress, false);
    }

    AnimatedPurpleButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            BooleanSupplier selected,
            Consumer<AnimatedPurpleButton> onPress,
            boolean toggleStyle) {
        super(x, y, width, height, message);
        this.selected = selected;
        this.onPress = onPress;
        this.toggleStyle = toggleStyle;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.accept(this);
    }

    @Override
    protected void extractContents(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        long currentTime = System.nanoTime() / 1_000_000L;
        long elapsed = Mth.clamp(currentTime - this.lastFrameTime, 0L, 50L);
        this.lastFrameTime = currentTime;

        float target = this.isHoveredOrFocused() ? 1.0F : 0.0F;
        float response = 1.0F - (float) Math.exp(-elapsed / 70.0F);
        this.hoverProgress += (target - this.hoverProgress) * response;

        boolean selectedState = this.selected.getAsBoolean();
        float selectedTarget = selectedState ? 1.0F : 0.0F;
        if (Float.isNaN(this.selectionProgress)) {
            this.selectionProgress = selectedTarget;
        } else {
            this.selectionProgress += (selectedTarget - this.selectionProgress) * response;
        }
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        if (this.toggleStyle) {
            this.drawToggle(graphics, x, y, width, height, selectedState);
            return;
        }

        int topAlpha = selectedState ? 174 : 118;
        int bottomAlpha = selectedState ? 150 : 96;
        int topRed = lerp(55, 112, this.hoverProgress);
        int topGreen = lerp(17, 36, this.hoverProgress);
        int topBlue = lerp(82, 156, this.hoverProgress);
        int bottomRed = lerp(29, 73, this.hoverProgress);
        int bottomGreen = lerp(8, 19, this.hoverProgress);
        int bottomBlue = lerp(49, 107, this.hoverProgress);

        HerziumTheme.fillRounded(
                graphics,
                x,
                y,
                width,
                height,
                HerziumTheme.argb(topAlpha, topRed, topGreen, topBlue),
                HerziumTheme.argb(bottomAlpha, bottomRed, bottomGreen, bottomBlue));

        int borderAlpha = 120 + (int) (this.hoverProgress * 75.0F);
        HerziumTheme.drawOutline(
                graphics,
                x + 1,
                y + 1,
                width - 2,
                height - 2,
                HerziumTheme.argb(borderAlpha, 169, 116, 240));

        if (selectedState && height >= 8) {
            graphics.fill(x + 2, y + 3, x + 4, y + height - 3, 0xE2D17AFF);
        }

        if (this.labelInset == -1) {
            this.extractDefaultLabel(
                    graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        } else if (this.labelInset >= 0 && height >= 9) {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            graphics.text(font, fitted(font, width - this.labelInset - 4), x + this.labelInset, y + (height - 8) / 2,
                    selectedState ? HerziumTheme.TEXT_TITLE : HerziumTheme.TEXT_PRIMARY);
        }
    }

    /** The label, cut with an ellipsis when the control is narrower than its words. */
    private String fitted(net.minecraft.client.gui.Font font, int room) {
        String label = this.getMessage().getString();
        return font.width(label) <= room ? label
                : font.plainSubstrByWidth(label, Math.max(0, room - font.width("…"))) + "…";
    }

    /** A row: its name on the left, and on the right a switch whose knob slides as it changes. */
    private void drawToggle(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            boolean selectedState) {
        int glow = Math.round(this.hoverProgress * 26.0F);
        HerziumTheme.fillRounded(graphics, x, y, width, height,
                HerziumTheme.argb(120, 46 + glow, 22 + glow / 2, 66 + glow),
                HerziumTheme.argb(120, 26 + glow, 12 + glow / 2, 42 + glow));
        HerziumTheme.drawOutline(graphics, x, y, width, height,
                HerziumTheme.argb(46 + Math.round(this.hoverProgress * 110.0F), 169, 116, 240));

        int switchHeight = Math.max(4, Math.min(10, height - 4));
        int switchWidth = Math.min(22, Math.max(8, width / 4));
        int switchX = x + width - switchWidth - 5;
        int switchY = y + (height - switchHeight) / 2;
        int track = mix(HerziumTheme.TOGGLE_TRACK_OFF, HerziumTheme.TOGGLE_TRACK_ON, this.selectionProgress);
        HerziumTheme.fillRounded(graphics, switchX, switchY, switchWidth, switchHeight, track, track);
        int knob = Math.max(2, switchHeight - 4);
        int knobX = switchX + 2 + Math.round((switchWidth - knob - 4) * this.selectionProgress);
        graphics.fill(knobX, switchY + 2, knobX + knob, switchY + 2 + knob,
                this.active ? HerziumTheme.TOGGLE_KNOB : HerziumTheme.TEXT_OFF);

        // The change itself: a streak that crosses the row while the knob travels.
        float travel = selectedState ? this.selectionProgress : 1.0F - this.selectionProgress;
        if (travel > 0.02F && travel < 0.98F) {
            int head = x + Math.round(width * travel);
            graphics.fill(Math.max(x + 2, head - 28), y + height - 2, Math.min(x + width - 2, head), y + height - 1,
                    HerziumTheme.argb(Math.round(230 * (1.0F - travel)), 209, 122, 255));
        }

        if (height >= 9) {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            graphics.text(font, fitted(font, switchX - x - 10), x + 6, y + (height - 8) / 2,
                    this.active ? HerziumTheme.TEXT_PRIMARY : HerziumTheme.TEXT_OFF);
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    private static int mix(int from, int to, float progress) {
        return HerziumTheme.argb(lerp(from >>> 24, to >>> 24, progress), lerp(from >> 16 & 255, to >> 16 & 255, progress),
                lerp(from >> 8 & 255, to >> 8 & 255, progress), lerp(from & 255, to & 255, progress));
    }

    private static int lerp(int start, int end, float progress) {
        return start + Math.round((end - start) * progress);
    }
}
