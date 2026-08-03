package dev.zymekoh.herzium.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class AnimatedPurpleButton extends AbstractButton {
    private final Consumer<AnimatedPurpleButton> onPress;
    private final BooleanSupplier selected;
    private float hoverProgress;
    private long lastFrameTime = System.nanoTime() / 1_000_000L;

    AnimatedPurpleButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            BooleanSupplier selected,
            Consumer<AnimatedPurpleButton> onPress) {
        super(x, y, width, height, message);
        this.selected = selected;
        this.onPress = onPress;
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

        float pulse = 0.5F + 0.5F * (float) Math.sin(currentTime / 230.0F);
        boolean selectedState = this.selected.getAsBoolean();
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        int topAlpha = selectedState ? 174 : 118;
        int bottomAlpha = selectedState ? 150 : 96;
        int topRed = lerp(55, 112, this.hoverProgress);
        int topGreen = lerp(17, 36, this.hoverProgress);
        int topBlue = lerp(82, 156, this.hoverProgress);
        int bottomRed = lerp(29, 73, this.hoverProgress);
        int bottomGreen = lerp(8, 19, this.hoverProgress);
        int bottomBlue = lerp(49, 107, this.hoverProgress);

        fillRounded(
                graphics,
                x,
                y,
                width,
                height,
                argb(topAlpha, topRed, topGreen, topBlue),
                argb(bottomAlpha, bottomRed, bottomGreen, bottomBlue));

        int borderAlpha = 145 + (int) (this.hoverProgress * 85.0F);
        int borderBlue = 210 + (int) (pulse * 35.0F);
        drawOutline(graphics, x + 1, y + 1, width - 2, height - 2, argb(borderAlpha, 191, 91, borderBlue));

        if (selectedState && height >= 8) {
            graphics.fill(x + 2, y + 3, x + 4, y + height - 3, 0xE2D17AFF);
        }

        if (this.hoverProgress > 0.01F) {
            int shimmerWidth = Math.max(8, (int) (width * 0.22F));
            int travel = width + shimmerWidth;
            int shimmerX = x - shimmerWidth + (int) ((currentTime % 900L) / 900.0F * travel);
            int clippedStart = Mth.clamp(shimmerX, x + 2, x + width - 2);
            int clippedEnd = Mth.clamp(shimmerX + shimmerWidth, x + 2, x + width - 2);
            if (clippedEnd > clippedStart) {
                graphics.fill(
                        clippedStart,
                        y + 2,
                        clippedEnd,
                        y + height - 2,
                        argb((int) (35.0F * this.hoverProgress), 238, 190, 255));
            }
        }

        this.extractDefaultLabel(
                graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    private static void fillRounded(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int topColor,
            int bottomColor) {
        graphics.fillGradient(x + 2, y, x + width - 2, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + 2, x + width, y + height - 2, topColor, bottomColor);
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

    private static int lerp(int start, int end, float progress) {
        return start + Math.round((end - start) * progress);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
