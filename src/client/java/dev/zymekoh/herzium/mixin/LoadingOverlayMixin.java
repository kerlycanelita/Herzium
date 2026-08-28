package dev.zymekoh.herzium.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
 *
 * <p>The initial reload happens before Minecraft's translated font resources
 * are safe to use. {@link StartupPixelFont} therefore draws resource-free
 * ASCII text. The selected language code is still available, so every
 * {@code es_*} locale receives accent-free Spanish literals while every other
 * locale falls back to English. This keeps the screen bilingual without
 * reintroducing the missing-glyph and shader failures fixed in 1.8.4.</p>
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
    private static final String HERZIUM_SUBTITLE_ENGLISH = "Finishing Vanilla setup";

    @Unique
    private static final String HERZIUM_SUBTITLE_SPANISH = "Terminando la configuracion de Vanilla";

    @Unique
    private static final List<String> HERZIUM_TIPS_ENGLISH = List.of(
            "Removing waiting, not work.",
            "The hotbar preview is visual only.",
            "Vanilla still selects the real slot.",
            "Combat items keep Vanilla's hand animation.",
            "VSync and frame limits stay in Minecraft.",
            "Resource packs still require real work.",
            "No extra packets. No faster attacks.",
            "A clear hotbar press can appear next frame.",
            "High refresh displays make the preview easier to see.",
            "Removing decorative delays.");

    @Unique
    private static final List<String> HERZIUM_TIPS_SPANISH = List.of(
            "Herzium elimina esperas, no trabajo.",
            "La vista previa de hotbar es solo visual.",
            "Vanilla selecciona la ranura real.",
            "Los objetos de combate conservan la animacion de mano.",
            "VSync y el limite de FPS siguen en Minecraft.",
            "Los paquetes de recursos aun requieren trabajo real.",
            "No hay paquetes extra ni ataques mas rapidos.",
            "Una pulsacion clara aparece en el siguiente frame.",
            "Una pantalla rapida hace mas visible la diferencia.",
            "Quitando retrasos decorativos.");

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ReloadInstance reload;

    @Shadow
    private long fadeOutStart;

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
    private String herzium$subtitle = HERZIUM_SUBTITLE_ENGLISH;

    @Unique
    private List<String> herzium$tips = HERZIUM_TIPS_ENGLISH;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void herzium$initializeLoadingScreen(
            Minecraft minecraft,
            ReloadInstance reload,
            Consumer<Optional<Throwable>> onFinish,
            boolean fadeIn,
            CallbackInfo ci) {
        long now = System.nanoTime() / 1_000_000L;
        String languageCode = minecraft.getLanguageManager().getSelected();
        boolean spanish = languageCode != null && languageCode.toLowerCase(java.util.Locale.ROOT).startsWith("es_");
        this.herzium$subtitle = spanish ? HERZIUM_SUBTITLE_SPANISH : HERZIUM_SUBTITLE_ENGLISH;
        this.herzium$tips = spanish ? HERZIUM_TIPS_SPANISH : HERZIUM_TIPS_ENGLISH;
        this.herzium$tipIndex = ThreadLocalRandom.current().nextInt(this.herzium$tips.size());
        this.herzium$tipShownAt = now;
        this.herzium$nextTipAt = now + HERZIUM_TIP_TIME_MS;
    }

    /**
     * Skips the decorative wait before the fade-out may begin.
     *
     * <p>Vanilla's {@code tick()} runs untouched: it still calls
     * {@code checkExceptions()}, still forwards success or failure to
     * {@code onFinish}, still stamps {@code fadeOutStart} and still re-lays out
     * the screen the callback installed, now that the real fonts are loaded.
     * Herzium only reports the fade-in gate as already satisfied, so the
     * sequence starts on the tick the reload finishes instead of 1000 ms
     * later.</p>
     *
     * <p>This replaces an outright cancel of {@code tick()} that reimplemented
     * the whole close sequence by hand. That version worked, but any step
     * Mojang added to the method would have been dropped silently, with neither
     * a compile error nor a mixin error to show for it.</p>
     */
    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;isReadyToFadeOut()Z"),
            require = 1)
    private boolean herzium$skipFadeInWait(boolean vanillaReady) {
        return true;
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
            StartupPixelFont.drawCentered(graphics, this.herzium$subtitle, centerX, titleY + 14, 1, 0xFFC5A6DF);
        }

        int maxBarWidth = Math.max(1, width - 32);
        int minBarWidth = Math.min(120, maxBarWidth);
        int desiredBarWidth = width < 420 ? maxBarWidth : 320;
        int barWidth = Mth.clamp(desiredBarWidth, minBarWidth, maxBarWidth);
        int barX = centerX - barWidth / 2;
        int barY = Math.max(34, height - 18);

        String tip = this.herzium$tips.get(this.herzium$tipIndex);
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

        // Vanilla removes the overlay from this same method, but only once its
        // two-second fade-out has elapsed -- and that code never runs because
        // the frame below is cancelled. Once tick() has stamped fadeOutStart,
        // every check vanilla makes before closing has already passed, so the
        // overlay goes now instead. The identity guard keeps a replacement
        // overlay installed by an error handler or another mod from being
        // erased.
        if (this.fadeOutStart > -1L && this.minecraft.getOverlay() == (Object) this) {
            this.minecraft.setOverlay(null);
        }

        ci.cancel();
    }

    @Unique
    private void herzium$advanceTip(long now) {
        if (now < this.herzium$nextTipAt) {
            return;
        }

        int previous = this.herzium$tipIndex;
        if (this.herzium$tips.size() > 1) {
            do {
                this.herzium$tipIndex = ThreadLocalRandom.current().nextInt(this.herzium$tips.size());
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
        // Keep the resource-independent startup overlay cheap enough that it does not
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
