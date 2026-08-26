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
                "Herzium initialized. Always on, with no in-game options: uncapped rendering while the "
                        + "window is active, forced Vanilla Raw Input, instant equip for ordinary items, "
                        + "Priority Hotbar preview, and a start-up with no decorative waits.");

        if (FabricLoader.getInstance().isModLoaded("exordium")) {
            Herzium.LOGGER.info("Exordium detected; its HUD frame buffer will be bypassed.");
        }

        // Herzium no longer stands down for either of these. Both are still
        // reported, because a player whose cursor or camera behaves differently
        // than it used to deserves to find the reason in the log rather than
        // guess at it.
        if (KoHsiumIntegration.present()) {
            Herzium.LOGGER.info(
                    "KoHsium detected. Herzium now takes the Vanilla Raw Input window mode regardless, so "
                            + "KoHsium's editable input controls no longer decide it. KoHsium keeps everything "
                            + "else it owns.");
        }
        if (ExternalInputCompatibility.cursorPipelineOwnerPresent()) {
            Herzium.LOGGER.warn(
                    "KoHs Inventory Tweaks detected. Herzium rewrites the Vanilla Raw Input window mode at "
                            + "start-up and no longer yields that decision, so it can race Cursor Landing's "
                            + "placement when a screen opens. If the pointer lands centred instead of where "
                            + "Cursor Landing put it, this is why.");
        }

        // Raw Input Buffer and Ixeris are a different case from the two above.
        // They drive their own low-level mouse pipeline, so turning Vanilla's on
        // as well means two implementations feeding the same deltas. That is not
        // deference, so it survives the change.
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            Herzium.LOGGER.warn(
                    "Raw Input Buffer and Ixeris are both loaded. Herzium disabled only Vanilla Raw Input; "
                            + "the two external pipelines still overlap and one of them should be removed.");
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            Herzium.LOGGER.warn(
                    "Raw Input Buffer detected. Vanilla Raw Input is disabled to avoid two implementations "
                            + "owning the same mouse deltas; Raw Input Buffer keeps its own pipeline.");
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            Herzium.LOGGER.warn(
                    "Ixeris detected. Vanilla Raw Input is disabled while Ixeris owns its external input pipeline.");
        }

        if (ExternalInputCompatibility.rawInputBufferPresent()
                && !ExternalInputCompatibility.inventoryTweaksPresent()) {
            Herzium.LOGGER.warn(
                    "KoHs Inventory Tweaks is not loaded, so Cursor Landing and the Raw Input Buffer cursor "
                            + "adapter are unavailable in this session.");
        }
    }
}
