package dev.zymekoh.herzium;

import dev.zymekoh.herzium.compat.ExternalInputCompatibility;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import dev.zymekoh.herzium.config.HerziumConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class HerziumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HerziumConfig.loadAsync();
        Herzium.LOGGER.info(
                "Herzium initialized. A render-only hotbar preview for ordinary non-combat items, removal "
                        + "of their equip transition, and shorter decorative start-up transitions are active. "
                        + "Vanilla retains the real selection, actions and packets. Herzium does not increase "
                        + "FPS or require Fabric API; VSync, frame limits, Raw Input and cursor placement remain "
                        + "Vanilla-owned.");

        if (FabricLoader.getInstance().isModLoaded("exordium")) {
            Herzium.LOGGER.info("Exordium detected; its HUD frame buffer will be bypassed.");
        }

        if (KoHsiumIntegration.present()) {
            Herzium.LOGGER.info(
                    "KoHsium detected. Herzium does not change Raw Input, Smooth Camera, or cursor placement; "
                            + "KoHsium and Vanilla retain their normal ownership.");
        }
        if (ExternalInputCompatibility.cursorPipelineOwnerPresent()) {
            Herzium.LOGGER.info(
                    "KoHs Inventory Tweaks detected. Herzium never calls a Raw Input or cursor-position API; "
                            + "Cursor Landing and Vanilla retain exclusive cursor ownership.");
        }

        // These detections are informational only. Herzium does not attempt to
        // arm, disarm, mediate, or repair any mouse-input pipeline.
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            Herzium.LOGGER.warn(
                    "Raw Input Buffer and Ixeris are both loaded. Their external pipelines overlap and one "
                            + "should be removed; Herzium did not change either pipeline or Vanilla Raw Input.");
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            Herzium.LOGGER.info(
                    "Raw Input Buffer detected. Herzium did not change its pipeline or Vanilla Raw Input.");
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            Herzium.LOGGER.info(
                    "Ixeris detected. Herzium did not change its pipeline or Vanilla Raw Input.");
        }

        if (ExternalInputCompatibility.rawInputBufferPresent()
                && !ExternalInputCompatibility.inventoryTweaksPresent()) {
            Herzium.LOGGER.warn(
                    "KoHs Inventory Tweaks is not loaded, so Cursor Landing and the Raw Input Buffer cursor "
                            + "adapter are unavailable in this session.");
        }
    }
}
