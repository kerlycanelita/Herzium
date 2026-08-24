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

        this.extractDefaultLabel(
                graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    private void drawToggle(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            boolean selectedState) {
        int trackColor = selectedState
                ? HerziumTheme.TOGGLE_TRACK_ON
                : HerziumTheme.TOGGLE_TRACK_OFF;
        HerziumTheme.fillRounded(graphics, x, y, width, height, trackColor, trackColor);
        HerziumTheme.drawOutline(
                graphics,
                x,
                y,
                width,
                height,
                HerziumTheme.argb(110 + Math.round(this.hoverProgress * 70.0F), 169, 116, 240));

        int knobWidth = Math.max(5, Math.min(7, height - 6));
        int knobHeight = Math.max(6, height - 6);
        int knobTravel = Math.max(0, width - knobWidth - 6);
        int knobX = x + 3 + Math.round(knobTravel * this.selectionProgress);
        int knobY = y + (height - knobHeight) / 2;
        graphics.fill(
                knobX + 1,
                knobY,
                knobX + knobWidth - 1,
                knobY + knobHeight,
                HerziumTheme.TOGGLE_KNOB);
        graphics.fill(
                knobX,
                knobY + 1,
                knobX + knobWidth,
                knobY + knobHeight - 1,
                HerziumTheme.TOGGLE_KNOB);

        int labelLeft = selectedState ? x + 3 : x + knobWidth + 5;
        int labelRight = selectedState ? x + width - knobWidth - 5 : x + width - 3;
        graphics.centeredText(
                Minecraft.getInstance().font,
                this.getMessage(),
                (labelLeft + labelRight) / 2,
                y + Math.max(1, (height - 9) / 2),
                HerziumTheme.TEXT_PRIMARY);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    private static int lerp(int start, int end, float progress) {
        return start + Math.round((end - start) * progress);
    }
}
