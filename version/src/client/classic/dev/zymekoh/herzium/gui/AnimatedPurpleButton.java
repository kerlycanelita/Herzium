package dev.zymekoh.herzium.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class AnimatedPurpleButton extends AbstractButton {
    private final Consumer<AnimatedPurpleButton> action;
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
            Consumer<AnimatedPurpleButton> action) {
        super(x, y, width, height, message);
        this.selected = selected;
        this.action = action;
    }

    @Override
    public void onPress() {
        this.action.accept(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime() / 1_000_000L;
        long elapsed = Mth.clamp(now - this.lastFrameTime, 0L, 50L);
        this.lastFrameTime = now;
        float target = this.isHoveredOrFocused() ? 1.0F : 0.0F;
        float response = 1.0F - (float) Math.exp(-elapsed / 70.0F);
        this.hoverProgress += (target - this.hoverProgress) * response;

        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();
        boolean selectedState = this.selected.getAsBoolean();
        int topColor = argb(
                selectedState ? 174 : 118,
                lerp(55, 112, this.hoverProgress),
                lerp(17, 36, this.hoverProgress),
                lerp(82, 156, this.hoverProgress));
        int bottomColor = argb(
                selectedState ? 150 : 96,
                lerp(29, 73, this.hoverProgress),
                lerp(8, 19, this.hoverProgress),
                lerp(49, 107, this.hoverProgress));

        fillRounded(graphics, x, y, width, height, topColor, bottomColor);
        drawOutline(graphics, x + 1, y + 1, width - 2, height - 2, 0xD0BF5BEA);
        if (selectedState && height >= 8) {
            graphics.fill(x + 2, y + 3, x + 4, y + height - 3, 0xE2D17AFF);
        }
        this.renderString(graphics, Minecraft.getInstance().font, 0xFFFFFFFF);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    private static void fillRounded(
            GuiGraphics graphics, int x, int y, int width, int height, int topColor, int bottomColor) {
        graphics.fillGradient(x + 2, y, x + width - 2, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + 2, x + width, y + height - 2, topColor, bottomColor);
    }

    private static void drawOutline(
            GuiGraphics graphics, int x, int y, int width, int height, int color) {
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
