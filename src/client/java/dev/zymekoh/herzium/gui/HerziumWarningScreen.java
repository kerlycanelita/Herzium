package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.config.HerziumConfig;
import java.util.List;
import java.util.Random;
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
    private static final int PARTICLE_COUNT = 58;

    private final Runnable continuation;
    private final Particle[] particles = new Particle[PARTICLE_COUNT];
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
    private long lastParticleFrame = System.nanoTime() / 1_000_000L;
    private boolean completed;

    public HerziumWarningScreen(Runnable continuation) {
        super(TITLE);
        this.continuation = continuation;
    }

    @Override
    protected void init() {
        this.calculateLayout();
        this.prepareText();
        this.initializeParticles();
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
        graphics.fillGradient(0, 0, this.width, this.height, 0xFF06020C, 0xFF190426);
        graphics.fillGradient(0, 0, this.width, Math.max(1, this.height / 2), 0x77240A3A, 0x00240A3A);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        long now = System.nanoTime() / 1_000_000L;
        this.drawParticles(graphics, mouseX, mouseY, now);
        this.drawPanel(graphics, now);
        this.drawBody(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanel(GuiGraphicsExtractor graphics, long now) {
        fillRounded(
                graphics,
                this.panelX,
                this.panelY,
                this.panelWidth,
                this.panelHeight,
                0xE0180828,
                0xEA0B0412);

        float pulse = 0.5F + 0.5F * (float) Math.sin(now / 180.0F);
        int borderAlpha = 180 + Math.round(pulse * 65.0F);
        drawOutline(
                graphics,
                this.panelX + 1,
                this.panelY + 1,
                this.panelWidth - 2,
                this.panelHeight - 2,
                argb(borderAlpha, 205, 93, 255));

        int warningSize = this.headerHeight < 45 ? 15 : 20;
        int warningX = this.panelX + Mth.clamp(this.panelWidth / 24, 6, 14);
        int warningY = this.panelY + (this.headerHeight - warningSize) / 2;
        fillRounded(graphics, warningX, warningY, warningSize, warningSize, 0xE09124C2, 0xDB4B0B72);
        graphics.centeredText(
                this.font,
                Component.literal("!"),
                warningX + warningSize / 2,
                warningY + Math.max(2, (warningSize - 9) / 2),
                0xFFFFFFFF);

        int titleX = this.panelX + this.panelWidth / 2;
        int titleY = this.panelY + Math.max(7, (this.headerHeight - 9) / 2);
        graphics.centeredText(this.font, TITLE, titleX, titleY, 0xFFF4E8FF);

        graphics.fillGradient(
                this.bodyX,
                this.bodyTop,
                this.bodyX + this.bodyWidth,
                this.bodyTop + this.bodyHeight,
                0x8B12071D,
                0xA5080310);
        drawOutline(
                graphics,
                this.bodyX,
                this.bodyTop,
                this.bodyWidth,
                this.bodyHeight,
                0x8A743497);
    }

    private void drawBody(GuiGraphicsExtractor graphics) {
        int textX = this.bodyX + 7;
        int textY = this.bodyTop + 6 - this.scrollOffset;
        int clipRight = this.bodyX + this.bodyWidth;
        int clipBottom = this.bodyTop + this.bodyHeight;
        graphics.enableScissor(this.bodyX + 1, this.bodyTop + 1, clipRight - 1, clipBottom - 1);

        for (FormattedCharSequence line : this.messageLines) {
            graphics.text(this.font, line, textX, textY, 0xFFE6D9EC);
            textY += 10;
        }

        textY += 10;
        for (FormattedCharSequence line : this.quoteLines) {
            graphics.text(this.font, line, textX, textY, 0xFFE2A8FF);
            textY += 10;
        }
        graphics.disableScissor();

        if (this.maxScroll > 0) {
            int trackX = this.bodyX + this.bodyWidth - 5;
            int trackTop = this.bodyTop + 3;
            int trackHeight = Math.max(1, this.bodyHeight - 6);
            int thumbHeight = Math.max(8, trackHeight * trackHeight / (trackHeight + this.maxScroll));
            int thumbTravel = Math.max(0, trackHeight - thumbHeight);
            int thumbY = trackTop + (this.maxScroll == 0 ? 0 : thumbTravel * this.scrollOffset / this.maxScroll);
            graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackHeight, 0x663B1B4D);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFD278FF);
        }
    }

    private void initializeParticles() {
        Random random = new Random(0x4845525A49554DL ^ (long) this.width << 32 ^ this.height);
        for (int index = 0; index < this.particles.length; index++) {
            this.particles[index] = new Particle(
                    random.nextFloat() * Math.max(1, this.width),
                    random.nextFloat() * Math.max(1, this.height),
                    125.0F + random.nextFloat() * 260.0F,
                    -18.0F + random.nextFloat() * 36.0F,
                    1 + random.nextInt(3),
                    55 + random.nextInt(105));
        }
        this.lastParticleFrame = System.nanoTime() / 1_000_000L;
    }

    private void drawParticles(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            long now) {
        float elapsedSeconds = Mth.clamp((now - this.lastParticleFrame) / 1000.0F, 0.0F, 0.05F);
        this.lastParticleFrame = now;
        float reactionRadius = Mth.clamp(Math.min(this.width, this.height) / 5.0F, 28.0F, 62.0F);
        float reactionRadiusSquared = reactionRadius * reactionRadius;

        for (Particle particle : this.particles) {
            particle.x += particle.speedX * elapsedSeconds;
            particle.y += particle.speedY * elapsedSeconds;
            if (particle.x > this.width + 18.0F) {
                particle.x = -18.0F;
            }
            if (particle.y < -8.0F) {
                particle.y = this.height + 8.0F;
            } else if (particle.y > this.height + 8.0F) {
                particle.y = -8.0F;
            }

            float dx = particle.x - mouseX;
            float dy = particle.y - mouseY;
            float distanceSquared = dx * dx + dy * dy;
            float mouseInfluence = distanceSquared >= reactionRadiusSquared
                    ? 0.0F
                    : 1.0F - (float) Math.sqrt(distanceSquared) / reactionRadius;
            int alpha = Mth.clamp(
                    particle.baseAlpha + Math.round(mouseInfluence * (255 - particle.baseAlpha)),
                    0,
                    255);
            int x = Math.round(particle.x);
            int y = Math.round(particle.y);
            int trail = Math.max(4, particle.size * 4);
            graphics.fill(
                    x - trail,
                    y,
                    x,
                    y + particle.size,
                    argb(Math.max(8, alpha / 4), 150, 54, 232));
            graphics.fill(
                    x,
                    y,
                    x + particle.size,
                    y + particle.size,
                    argb(alpha, 215, 125, 255));
        }
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

    private static void fillRounded(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int topColor,
            int bottomColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int radius = Math.min(3, Math.min(width / 2, height / 2));
        graphics.fillGradient(x + radius, y, x + width - radius, y + height, topColor, bottomColor);
        graphics.fillGradient(x, y + radius, x + width, y + height - radius, topColor, bottomColor);
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

    private static final class Particle {
        private float x;
        private float y;
        private final float speedX;
        private final float speedY;
        private final int size;
        private final int baseAlpha;

        private Particle(
                float x,
                float y,
                float speedX,
                float speedY,
                int size,
                int baseAlpha) {
            this.x = x;
            this.y = y;
            this.speedX = speedX;
            this.speedY = speedY;
            this.size = size;
            this.baseAlpha = baseAlpha;
        }
    }
}
