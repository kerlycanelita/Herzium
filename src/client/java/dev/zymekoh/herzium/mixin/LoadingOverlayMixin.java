package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.herzium.gui.StartupPixelFont;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces fixed-duration loading fades with a lightweight Herzium screen.
 * Resource preparation and vanilla error handling are kept intact.
 */
@Mixin(value = LoadingOverlay.class, priority = 2000)
abstract class LoadingOverlayMixin {
    @Unique
    private static final long HERZIUM_TIP_TIME_MS = 2800L;

    @Unique
    private static final long HERZIUM_TIP_FADE_MS = 240L;

    @Unique
    private static final String HERZIUM_TITLE = "HERZIUM";

    @Unique
    private static final String HERZIUM_SUBTITLE = "Loading at full speed";

    @Unique
    private static final List<String> HERZIUM_TIPS = List.of(
            "No VSync. No waiting. Just frames.",
            "Herzium keeps your HUD moving at your refresh rate.",
            "Loading pixels at an unreasonable speed...",
            "Your FPS cap has left the game.",
            "Tip: your display refresh rate matters too.",
            "Fast hands, smooth HUD, vanilla mechanics.",
            "Warming up the purple engine...",
            "Mining the loading screen. Almost there.",
            "Herzium never invents player positions.",
            "Resource packs still require real work. Magic has limits.",
            "High refresh rates deserve high-frequency visuals.",
            "Removing decorative delays one millisecond at a time.");

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> onFinish;

    @Unique
    private int herzium$tipIndex;

    @Unique
    private long herzium$tipShownAt;

    @Unique
    private long herzium$nextTipAt;

    @Unique
    private int herzium$cachedTipWidth = -1;

    @Unique
    private int herzium$cachedTipIndex = -1;

    @Unique
    private List<String> herzium$cachedTipLines = List.of();

    @Unique
    private boolean herzium$finishHandled;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void herzium$initializeLoadingScreen(
            Minecraft minecraft,
            ReloadInstance reload,
            Consumer<Optional<Throwable>> onFinish,
            boolean fadeIn,
            CallbackInfo ci) {
        long now = System.nanoTime() / 1_000_000L;
        this.herzium$tipIndex = ThreadLocalRandom.current().nextInt(HERZIUM_TIPS.size());
        this.herzium$tipShownAt = now;
        this.herzium$nextTipAt = now + HERZIUM_TIP_TIME_MS;
    }

    /**
     * Vanilla keeps this overlay for a two-second fade after the reload is
     * complete. Herzium closes it on the same tick, after all checks succeed.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void herzium$finishAsSoonAsResourcesAreReady(CallbackInfo ci) {
        if (this.herzium$finishHandled) {
            if (this.minecraft.getOverlay() == (Object) this) {
                this.minecraft.setOverlay(null);
            }
            ci.cancel();
            return;
        }

        if (!this.reload.isDone()) {
            return;
        }

        try {
            this.reload.checkExceptions();
            this.onFinish.accept(Optional.empty());
        } catch (Throwable throwable) {
            this.onFinish.accept(Optional.of(throwable));
        }
        this.herzium$finishHandled = true;

        // Match vanilla's post-reload lifecycle: callbacks may replace the
        // current screen, and that screen must be laid out with the newly
        // loaded fonts/resources before the overlay is removed.
        if (this.minecraft.screen != null) {
            Window window = this.minecraft.getWindow();
            this.minecraft.screen.init(
                    window.getGuiScaledWidth(),
                    window.getGuiScaledHeight());
        }

        // Do not erase a replacement overlay installed by an error handler or
        // another mod while the reload callback was running.
        if (this.minecraft.getOverlay() == (Object) this) {
            this.minecraft.setOverlay(null);
        }

        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void herzium$drawFastLoadingScreen(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        long now = System.nanoTime() / 1_000_000L;

        this.herzium$advanceTip(now);

        graphics.fillGradient(0, 0, width, height, 0xFF08030F, 0xFF210735);
        graphics.fillGradient(0, 0, width, Math.max(1, height / 3), 0x661A0730, 0x001A0730);

        int titleY = Math.max(6, Math.min(16, height / 12));
        StartupPixelFont.drawCentered(graphics, HERZIUM_TITLE, centerX, titleY, 2, 0xFFF6ECFF);
        if (height >= 150) {
            StartupPixelFont.drawCentered(graphics, HERZIUM_SUBTITLE, centerX, titleY + 14, 1, 0xFFC5A6DF);
        }

        int maxBarWidth = Math.max(1, width - 32);
        int minBarWidth = Math.min(120, maxBarWidth);
        int desiredBarWidth = width < 420 ? maxBarWidth : 320;
        int barWidth = Mth.clamp(desiredBarWidth, minBarWidth, maxBarWidth);
        int barX = centerX - barWidth / 2;
        int barY = Math.max(34, height - 18);

        String tip = HERZIUM_TIPS.get(this.herzium$tipIndex);
        int tipWidth = Math.max(24, width - 28);
        List<String> tipLines = this.herzium$tipLines(tip, tipWidth);
        int maxTipLines = height < 150 ? 1 : 2;
        int visibleTipLines = Math.min(maxTipLines, tipLines.size());
        int tipY = Math.max(titleY + 14, barY - 11 - visibleTipLines * 7);
        int tipAlpha = this.herzium$tipAlpha(now);
        int tipColor = herzium$argb(tipAlpha, 218, 197, 235);

        for (int line = 0; line < visibleTipLines; line++) {
            StartupPixelFont.drawCentered(graphics, tipLines.get(line), centerX, tipY + line * 7, 1, tipColor);
        }

        int spiralTop = height >= 150 ? titleY + 29 : titleY + 13;
        int spiralBottom = Math.max(spiralTop + 18, tipY - 6);
        int spiralHeight = Math.max(18, spiralBottom - spiralTop);
        int spiralCenterY = spiralTop + spiralHeight / 2;
        int maxRadius = Math.max(7, Math.min(Math.min(width / 5, spiralHeight / 2 - 1), 48));
        int pixelSize = Math.max(2, Math.min(5, Math.min(width, height) / 75));
        this.herzium$drawPixelSpiral(graphics, centerX, spiralCenterY, maxRadius, pixelSize, now);

        float progress = Mth.clamp(this.reload.getActualProgress(), 0.0F, 1.0F);
        int progressWidth = Math.round((barWidth - 2) * progress);
        graphics.fill(barX, barY, barX + barWidth, barY + 5, 0xA02C1740);
        graphics.fill(barX + 1, barY + 1, barX + 1 + progressWidth, barY + 4, 0xFFE29BFF);
        graphics.fill(barX + 1, barY + 1, barX + 1 + progressWidth, barY + 2, 0xFFFFFFFF);

        if (height >= 170) {
            StartupPixelFont.drawCentered(
                    graphics,
                    Math.round(progress * 100.0F) + "%",
                    centerX,
                    barY - 8,
                    1,
                    0xFFCDB7DE);
        }

        ci.cancel();
    }

    @Unique
    private void herzium$advanceTip(long now) {
        if (now < this.herzium$nextTipAt) {
            return;
        }

        int previous = this.herzium$tipIndex;
        if (HERZIUM_TIPS.size() > 1) {
            do {
                this.herzium$tipIndex = ThreadLocalRandom.current().nextInt(HERZIUM_TIPS.size());
            } while (this.herzium$tipIndex == previous);
        }

        this.herzium$tipShownAt = now;
        this.herzium$nextTipAt = now + HERZIUM_TIP_TIME_MS;
    }

    @Unique
    private int herzium$tipAlpha(long now) {
        float fadeIn = Mth.clamp((float) (now - this.herzium$tipShownAt) / HERZIUM_TIP_FADE_MS, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((float) (this.herzium$nextTipAt - now) / HERZIUM_TIP_FADE_MS, 0.0F, 1.0F);
        return Math.round(255.0F * Math.min(fadeIn, fadeOut));
    }

    @Unique
    private List<String> herzium$tipLines(String tip, int width) {
        if (this.herzium$cachedTipWidth != width || this.herzium$cachedTipIndex != this.herzium$tipIndex) {
            this.herzium$cachedTipWidth = width;
            this.herzium$cachedTipIndex = this.herzium$tipIndex;
            this.herzium$cachedTipLines = StartupPixelFont.wrap(tip, width, 1);
        }
        return this.herzium$cachedTipLines;
    }

    @Unique
    private void herzium$drawPixelSpiral(
            GuiGraphicsExtractor graphics,
            int centerX,
            int centerY,
            int maxRadius,
            int pixelSize,
            long now) {
        // Keep the uncapped startup overlay cheap enough that it does not
        // compete noticeably with resource-loading workers.
        int points = Mth.clamp(maxRadius / 3 + 16, 20, 32);
        float rotation = (float) (now % 2400L) / 2400.0F * Mth.TWO_PI;

        for (int index = 0; index < points; index++) {
            float path = (float) index / Math.max(1, points - 1);
            float radius = 2.0F + path * (maxRadius - 2.0F);
            float angle = rotation - path * Mth.TWO_PI * 2.65F;
            int x = centerX + Math.round(Mth.cos(angle) * radius / pixelSize) * pixelSize;
            int y = centerY + Math.round(Mth.sin(angle) * radius / pixelSize) * pixelSize;
            float pulse = 0.58F + 0.42F * Mth.sin(rotation * 1.7F + path * Mth.TWO_PI);
            int alpha = Math.round((70.0F + 185.0F * path) * pulse);
            int red = Math.round(130.0F + 95.0F * path);
            int color = herzium$argb(Mth.clamp(alpha, 0, 255), red, 65, 255);
            int size = index > points * 0.72F ? pixelSize + 1 : pixelSize;
            graphics.fill(x - size / 2, y - size / 2, x - size / 2 + size, y - size / 2 + size, color);
        }

        int core = Math.max(3, pixelSize + 1);
        graphics.fill(
                centerX - core / 2,
                centerY - core / 2,
                centerX - core / 2 + core,
                centerY - core / 2 + core,
                0xFFFFFFFF);
    }

    @Unique
    private static int herzium$argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
