package dev.zymekoh.herzium;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.compat.ExternalInputCompatibility;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class HerziumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HerziumConfig.loadAsync();
        Herzium.LOGGER.info(
                "Herzium initialized: fast loading, render-only hotbar priority, conditional instant equip, "
                        + "and active-window uncapped rendering are active. Inventory backgrounds remain Vanilla-owned.");

        if (FabricLoader.getInstance().isModLoaded("exordium")) {
            Herzium.LOGGER.info("Exordium detected; its HUD frame buffer will be bypassed.");
        }
        if (KoHsiumIntegration.present()) {
            Herzium.LOGGER.info(
                    "KoHsium cooperation active: Herzium owns active-window VSync/FPS policy and "
                            + "the visual hotbar preview; Vanilla owns inactive pacing and KoHsium owns "
                            + "editable input controls.");
        }
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            Herzium.LOGGER.warn(
                    "Raw Input Buffer and Ixeris are both loaded. Herzium disabled only Vanilla Raw Input; "
                            + "the two external pipelines still overlap and one of them should be removed.");
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            Herzium.LOGGER.warn(
                    "Raw Input Buffer detected. Vanilla Raw Input is disabled to avoid duplicate ownership. "
                            + "Cursor Landing requires KoHs Inventory Tweaks for its verified recentering adapter.");
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            Herzium.LOGGER.warn(
                    "Ixeris detected. Vanilla Raw Input is disabled while Ixeris owns its external input pipeline.");
        }
        if (ExternalInputCompatibility.rawInputBufferPresent()
                && !ExternalInputCompatibility.inventoryTweaksPresent()) {
            Herzium.LOGGER.warn(
                    "KoHs Inventory Tweaks is not loaded, so Cursor Landing and the Raw Input Buffer cursor adapter "
                            + "are unavailable in this session.");
        }
    }
}
