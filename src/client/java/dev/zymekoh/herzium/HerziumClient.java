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
                "Herzium initialized: fast loading, conditional instant equip, Priority Hotbar preview, and "
                        + "active-window uncapped rendering are active. Inventory backgrounds remain "
                        + "Vanilla-owned, and the hotbar preview never writes the real selection.");

        if (FabricLoader.getInstance().isModLoaded("exordium")) {
            Herzium.LOGGER.info("Exordium detected; its HUD frame buffer will be bypassed.");
        }
        if (KoHsiumIntegration.present()) {
            Herzium.LOGGER.info(
                    "KoHsium cooperation active: Herzium owns active-window VSync/FPS policy and "
                            + "conditional item-equip rendering; Vanilla owns hotbar input and inactive pacing, "
                            + "and KoHsium owns "
                            + "editable input controls.");
        }
        if (ExternalInputCompatibility.cursorPipelineOwnerPresent()) {
            Herzium.LOGGER.info(
                    "KoHs Inventory Tweaks detected: it owns Cursor Landing across screen transitions. "
                            + "Herzium skips its start-up Vanilla Raw Input window rewrite and leaves the "
                            + "player's setting unchanged; "
                            + "active-window VSync/FPS policy remains owned by Herzium.");
        } else if (!KoHsiumIntegration.present()) {
            Herzium.LOGGER.info(
                    "No external cursor-pipeline owner detected: Herzium retains its existing start-up "
                            + "Vanilla Raw Input window policy.");
        }
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            if (KoHsiumIntegration.present()
                    || ExternalInputCompatibility.cursorPipelineOwnerPresent()) {
                Herzium.LOGGER.warn(
                        "Raw Input Buffer and Ixeris are both loaded. Their external pipelines still overlap "
                                + "and one of them should be removed. Herzium left Vanilla Raw Input unchanged "
                                + "because another installed mod owns the input-window decision.");
            } else {
                Herzium.LOGGER.warn(
                        "Raw Input Buffer and Ixeris are both loaded. Herzium disabled only Vanilla Raw Input; "
                                + "the two external pipelines still overlap and one of them should be removed.");
            }
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            if (KoHsiumIntegration.present()
                    || ExternalInputCompatibility.cursorPipelineOwnerPresent()) {
                Herzium.LOGGER.warn(
                        "Raw Input Buffer detected. Herzium left Vanilla Raw Input unchanged because another "
                                + "installed mod owns the input-window decision; Raw Input Buffer still owns "
                                + "its external mouse-delta pipeline.");
            } else {
                Herzium.LOGGER.warn(
                        "Raw Input Buffer detected. Vanilla Raw Input is disabled to avoid duplicate ownership. "
                                + "Cursor Landing requires KoHs Inventory Tweaks for its verified recentering adapter.");
            }
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            if (KoHsiumIntegration.present()
                    || ExternalInputCompatibility.cursorPipelineOwnerPresent()) {
                Herzium.LOGGER.warn(
                        "Ixeris detected. Herzium left Vanilla Raw Input unchanged because another installed mod "
                                + "owns the input-window decision; Ixeris still owns its external input thread.");
            } else {
                Herzium.LOGGER.warn(
                        "Ixeris detected. Vanilla Raw Input is disabled while Ixeris owns its external input pipeline.");
            }
        }
        if (ExternalInputCompatibility.rawInputBufferPresent()
                && !ExternalInputCompatibility.inventoryTweaksPresent()) {
            Herzium.LOGGER.warn(
                    "KoHs Inventory Tweaks is not loaded, so Cursor Landing and the Raw Input Buffer cursor adapter "
                            + "are unavailable in this session.");
        }
    }
}
