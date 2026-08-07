package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.zymekoh.herzium.gui.StartupPixelFont;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

/** Lightweight loading overlay for versions whose LoadingOverlay has no tick method. */
@Mixin(value = LoadingOverlay.class, priority = 2000)
abstract class LoadingOverlayMixin {
    @Unique
    private static final List<String> HERZIUM_TIPS = List.of(
            "No VSync. No waiting. Just frames.",
            "Herzium keeps your HUD moving at your refresh rate.",
            "Loading pixels at an unreasonable speed...",
            "Fast hands, smooth HUD, vanilla mechanics.",
            "Resource packs still require real work. Magic has limits.");

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
    private long herzium$nextTipAt;

    @Unique
    private boolean herzium$finishHandled;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void herzium$initialize(
            Minecraft minecraft,
            ReloadInstance reload,
            Consumer<Optional<Throwable>> onFinish,
            boolean fadeIn,
            CallbackInfo ci) {
        this.herzium$tipIndex = ThreadLocalRandom.current().nextInt(HERZIUM_TIPS.size());
        this.herzium$nextTipAt = System.nanoTime() / 1_000_000L + 2800L;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void herzium$renderFastLoadingScreen(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci) {
        if (this.reload.isDone()) {
            this.herzium$finishReload();
            ci.cancel();
            return;
        }

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int centerX = width / 2;
        long now = System.nanoTime() / 1_000_000L;
        if (now >= this.herzium$nextTipAt) {
            this.herzium$tipIndex = (this.herzium$tipIndex + 1) % HERZIUM_TIPS.size();
            this.herzium$nextTipAt = now + 2800L;
        }

        graphics.fillGradient(0, 0, width, height, 0xFF08030F, 0xFF210735);
        StartupPixelFont.drawCentered(graphics, "HERZIUM", centerX, 12, 2, 0xFFF6ECFF);
        if (height >= 150) {
            StartupPixelFont.drawCentered(
                    graphics, "Loading at full speed", centerX, 26, 1, 0xFFC5A6DF);
        }

        float progress = Mth.clamp(this.reload.getActualProgress(), 0.0F, 1.0F);
        int barWidth = Math.min(320, Math.max(80, width - 32));
        int barX = centerX - barWidth / 2;
        int barY = Math.max(48, height - 18);
        int filled = Math.round((barWidth - 2) * progress);
        graphics.fill(barX, barY, barX + barWidth, barY + 5, 0xA02C1740);
        graphics.fill(barX + 1, barY + 1, barX + 1 + filled, barY + 4, 0xFFE29BFF);

        String tip = HERZIUM_TIPS.get(this.herzium$tipIndex);
        List<String> lines = StartupPixelFont.wrap(tip, Math.max(24, width - 28), 1);
        int visibleLines = Math.min(height < 150 ? 1 : 2, lines.size());
        int tipY = Math.max(36, barY - 11 - visibleLines * 7);
        for (int line = 0; line < visibleLines; line++) {
            StartupPixelFont.drawCentered(
                    graphics, lines.get(line), centerX, tipY + line * 7, 1, 0xFFDCC5EB);
        }

        this.herzium$drawSpiral(graphics, centerX, (36 + tipY) / 2, Math.min(38, Math.max(8, height / 7)), now);
        ci.cancel();
    }

    @Unique
    private void herzium$finishReload() {
        if (!this.herzium$finishHandled) {
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.onFinish.accept(Optional.of(throwable));
            }
            this.herzium$finishHandled = true;

            if (this.minecraft.screen != null) {
                Window window = this.minecraft.getWindow();
                this.minecraft.screen.resize(
                        this.minecraft,
                        window.getGuiScaledWidth(),
                        window.getGuiScaledHeight());
            }
        }
        if (this.minecraft.getOverlay() == (Object) this) {
            this.minecraft.setOverlay(null);
        }
    }

    @Unique
    private void herzium$drawSpiral(GuiGraphics graphics, int centerX, int centerY, int radius, long now) {
        float rotation = (float) (now % 2400L) / 2400.0F * Mth.TWO_PI;
        for (int index = 0; index < 24; index++) {
            float path = index / 23.0F;
            float distance = 2.0F + path * (radius - 2.0F);
            float angle = rotation - path * Mth.TWO_PI * 2.65F;
            int x = centerX + Math.round(Mth.cos(angle) * distance);
            int y = centerY + Math.round(Mth.sin(angle) * distance);
            int alpha = 70 + Math.round(path * 185.0F);
            graphics.fill(x - 1, y - 1, x + 2, y + 2, alpha << 24 | 0x00D77DFF);
        }
    }
}
