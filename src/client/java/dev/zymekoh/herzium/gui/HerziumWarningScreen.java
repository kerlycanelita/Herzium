package dev.zymekoh.herzium.gui;

import dev.zymekoh.herzium.compat.ExternalInputCompatibility;
import dev.zymekoh.herzium.config.HerziumConfig;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Start-up advisory, shown once after the initial resource reload.
 *
 * <p>There is no panel. The warning sits directly on the particle field, so
 * the first thing Herzium shows is the thing Herzium is: the field moving at
 * whatever rate the machine can manage, with the text on top of it.</p>
 *
 * <p>Everything fades in on a timeline, and the buttons deliberately arrive two
 * seconds late. A warning that can be dismissed on the same reflex that
 * launched the game is not a warning, and this one is about hardware.</p>
 */
public final class HerziumWarningScreen extends Screen {
    private static final Component TITLE = Component.translatable("herzium.warning.title");
    private static final Component MESSAGE = Component.translatable("herzium.warning.message");
    private static final int TIP_COUNT = 6;

    /** How long after the advisory appears before it can be dismissed. */
    private static final long BUTTON_DELAY_MS = 2_000L;
    private static final long TIP_PERIOD_MS = 4_200L;
    private static final long TIP_FADE_MS = 420L;

    private final Runnable continuation;
    private final HerziumTheme.ParticleField particles = new HerziumTheme.ParticleField();
    private final List<Notice> notices = new ArrayList<>();
    private final long openedAtMillis = System.nanoTime() / 1_000_000L;

    private List<FormattedCharSequence> messageLines = List.of();
    private AnimatedPurpleButton acceptButton;
    private AnimatedPurpleButton dismissButton;
    private int contentTop;
    private int contentWidth;
    private int buttonRowY;
    private boolean buttonsRevealed;
    private boolean completed;

    public HerziumWarningScreen(Runnable continuation) {
        super(TITLE);
        this.continuation = continuation;
    }

    @Override
    protected void init() {
        this.contentWidth = Mth.clamp(Math.round(this.width * 0.62F), Math.min(240, this.width - 16), 520);
        this.messageLines = this.font.split(MESSAGE, Math.max(24, this.contentWidth));
        this.particles.resize(this.width, this.height);
        this.buildNotices();

        int blockHeight = 22 + this.messageLines.size() * 11 + 10 + this.notices.size() * 20;
        this.contentTop = Math.max(14, (this.height - blockHeight) / 2 - 12);
        this.buttonRowY = Math.max(
                this.contentTop + blockHeight + 12,
                this.height - (this.height < 260 ? 44 : 62));

        this.addButtons();
    }

    /**
     * Builds the per-mod notices, each with the accuracy it actually deserves.
     *
     * <p>Herzium supports Raw Input Buffer and Ixeris: when either is installed
     * it stops forcing Vanilla Raw Input so two implementations do not feed the
     * same mouse deltas. Telling the player to uninstall a mod Herzium is built
     * to coexist with would be wrong, so those two are stated, not condemned.
     * The one genuine "remove one of these" case is the two of them together,
     * which neither supports, and that is the only notice marked as a
     * problem.</p>
     *
     * <p>Exordium is a third case: nothing breaks, but Herzium bypasses its HUD
     * cache, so the part of Exordium the player installed it for is not doing
     * anything while Herzium is present. That is worth knowing and is not worth
     * an alarm.</p>
     */
    private void buildNotices() {
        this.notices.clear();
        boolean rawInputBuffer = ExternalInputCompatibility.rawInputBufferPresent();
        boolean ixeris = ExternalInputCompatibility.ixerisPresent();

        if (rawInputBuffer && ixeris) {
            this.notices.add(new Notice(
                    "rawinputbuffer",
                    Component.translatable("herzium.warning.notice.both_pipelines"),
                    HerziumTheme.TEXT_BAD));
        } else if (rawInputBuffer) {
            this.notices.add(new Notice(
                    "rawinputbuffer",
                    Component.translatable("herzium.warning.notice.raw_input_buffer"),
                    HerziumTheme.TEXT_WARN));
        } else if (ixeris) {
            this.notices.add(new Notice(
                    "ixeris",
                    Component.translatable("herzium.warning.notice.ixeris"),
                    HerziumTheme.TEXT_WARN));
        }

        if (FabricLoader.getInstance().isModLoaded("exordium")) {
            this.notices.add(new Notice(
                    "exordium",
                    Component.translatable("herzium.warning.notice.exordium"),
                    HerziumTheme.TEXT_MUTED));
        }
    }

    private void addButtons() {
        int gap = this.width < 340 ? 6 : 10;
        int available = Math.max(1, this.width - 32);
        int buttonWidth = Math.min(190, Math.max(1, (available - gap) / 2));
        int buttonHeight = this.height < 260 ? 18 : 22;
        int totalWidth = buttonWidth * 2 + gap;
        int x = (this.width - totalWidth) / 2;

        this.acceptButton = this.addRenderableWidget(new AnimatedPurpleButton(
                x,
                this.buttonRowY,
                buttonWidth,
                buttonHeight,
                Component.translatable("herzium.warning.accept"),
                () -> true,
                button -> this.finish(false)));
        this.dismissButton = this.addRenderableWidget(new AnimatedPurpleButton(
                x + buttonWidth + gap,
                this.buttonRowY,
                buttonWidth,
                buttonHeight,
                Component.translatable("herzium.warning.dont_show"),
                () -> false,
                button -> this.finish(true)));

        // Hidden rather than merely inactive: an invisible-but-present button
        // would still take the keyboard focus ring before it can be used.
        this.buttonsRevealed = this.elapsedMillis() >= BUTTON_DELAY_MS;
        this.setButtonsVisible(this.buttonsRevealed);
    }

    private void setButtonsVisible(boolean visible) {
        this.acceptButton.visible = visible;
        this.acceptButton.active = visible;
        this.dismissButton.visible = visible;
        this.dismissButton.active = visible;
        if (visible) {
            this.setInitialFocus(this.acceptButton);
        }
    }

    private long elapsedMillis() {
        return System.nanoTime() / 1_000_000L - this.openedAtMillis;
    }

    private void finish(boolean rememberAcknowledgement) {
        if (this.completed) {
            return;
        }

        this.completed = true;
        if (rememberAcknowledgement) {
            HerziumConfig.get().acknowledgeStartupWarning();
        }
        this.continuation.run();
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        // Opaque: this runs before the title screen exists, so there is nothing
        // behind it worth showing through.
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
        long elapsed = now - this.openedAtMillis;

        this.particles.render(graphics, this.width, this.height, mouseX, mouseY, now);

        if (!this.buttonsRevealed && elapsed >= BUTTON_DELAY_MS) {
            this.buttonsRevealed = true;
            this.setButtonsVisible(true);
        }
        if (this.buttonsRevealed) {
            // A short rise as they arrive, so they read as appearing rather
            // than as having been there all along.
            int lift = Math.round(8.0F * (1.0F - ease(elapsed - BUTTON_DELAY_MS, 260L)));
            this.acceptButton.setY(this.buttonRowY + lift);
            this.dismissButton.setY(this.buttonRowY + lift);
        }

        int centerX = this.width / 2;
        int y = this.contentTop;

        float titleFade = ease(elapsed, 420L);
        graphics.centeredText(
                this.font,
                TITLE,
                centerX,
                y - Math.round(6.0F * (1.0F - titleFade)),
                withAlpha(HerziumTheme.TEXT_TITLE, titleFade));
        y += 14;

        int ruleWidth = Math.round(Math.min(this.contentWidth, 200) * ease(elapsed, 620L));
        if (ruleWidth > 1) {
            graphics.fill(centerX - ruleWidth / 2, y, centerX + ruleWidth / 2, y + 1, HerziumTheme.DIVIDER);
        }
        y += 10;

        int textX = centerX - this.contentWidth / 2;
        for (int line = 0; line < this.messageLines.size(); line++) {
            float fade = ease(elapsed - 160L - line * 45L, 380L);
            if (fade <= 0.0F) {
                continue;
            }
            graphics.text(
                    this.font,
                    this.messageLines.get(line),
                    textX,
                    y + line * 11,
                    withAlpha(HerziumTheme.TEXT_PRIMARY, fade));
        }
        y += this.messageLines.size() * 11 + 10;

        for (int index = 0; index < this.notices.size(); index++) {
            float fade = ease(elapsed - 520L - index * 140L, 420L);
            if (fade <= 0.0F) {
                continue;
            }
            this.drawNotice(graphics, this.notices.get(index), textX, y + index * 20, fade);
        }

        this.drawTip(graphics, centerX, now, elapsed);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawNotice(GuiGraphicsExtractor graphics, Notice notice, int x, int y, float fade) {
        int iconSize = 16;
        Identifier icon = ModIconLoader.iconFor(notice.modId());
        int textX = x;

        if (icon != null) {
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    icon,
                    x,
                    y,
                    0.0F,
                    0.0F,
                    iconSize,
                    iconSize,
                    iconSize,
                    iconSize,
                    withAlpha(0xFFFFFFFF, fade));
            textX += iconSize + 6;
        }

        // The bar carries the severity, so the sentence itself can stay calm.
        graphics.fill(textX - 4, y + 1, textX - 2, y + iconSize - 1, withAlpha(notice.color(), fade));

        List<FormattedCharSequence> lines = this.font.split(
                notice.text(),
                Math.max(24, this.contentWidth - (textX - x)));
        int drawn = Math.min(2, lines.size());
        int block = drawn * 10;
        for (int line = 0; line < drawn; line++) {
            graphics.text(
                    this.font,
                    lines.get(line),
                    textX,
                    y + (iconSize - block) / 2 + line * 10,
                    withAlpha(HerziumTheme.TEXT_BODY, fade));
        }
    }

    private void drawTip(GuiGraphicsExtractor graphics, int centerX, long now, long elapsed) {
        if (this.height < 200) {
            return;
        }

        long sinceStart = Math.max(0L, elapsed);
        int index = (int) ((sinceStart / TIP_PERIOD_MS) % TIP_COUNT);
        long intoTip = sinceStart % TIP_PERIOD_MS;
        float fadeIn = ease(intoTip, TIP_FADE_MS);
        float fadeOut = ease(TIP_PERIOD_MS - intoTip, TIP_FADE_MS);
        float fade = Math.min(fadeIn, fadeOut) * ease(elapsed - 900L, 500L);
        if (fade <= 0.0F) {
            return;
        }

        graphics.centeredText(
                this.font,
                Component.translatable("herzium.warning.tip." + index),
                centerX,
                this.height - 16,
                withAlpha(HerziumTheme.TEXT_MUTED, fade));
    }

    /** Smoothstep on a 0..1 ramp, clamped at both ends. */
    private static float ease(long elapsedMillis, long durationMillis) {
        if (elapsedMillis <= 0L) {
            return 0.0F;
        }
        if (elapsedMillis >= durationMillis) {
            return 1.0F;
        }

        float t = (float) elapsedMillis / durationMillis;
        return t * t * (3.0F - 2.0F * t);
    }

    private static int withAlpha(int argb, float fade) {
        int alpha = Mth.clamp(Math.round((argb >>> 24) * fade), 0, 255);
        return alpha << 24 | argb & 0x00FFFFFF;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private record Notice(String modId, Component text, int color) {
    }
}
