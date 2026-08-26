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

        // Raw Input Buffer is the one mod Herzium still stands down for, and not
        // out of deference: it reads the Win32 raw stream alongside GLFW's.
        // Ixeris is reported separately because it is the opposite case -- it
        // needs raw mouse motion switched on, not off.
        if (ExternalInputCompatibility.competingExternalPipelinesPresent()) {
            Herzium.LOGGER.warn(
                    "Raw Input Buffer and Ixeris are both loaded. Herzium left raw mouse motion off for Raw "
                            + "Input Buffer, which also stops Ixeris from arming its own handler; the two "
                            + "still overlap and one of them should be removed.");
        } else if (ExternalInputCompatibility.rawInputBufferPresent()) {
            Herzium.LOGGER.warn(
                    "Raw Input Buffer detected. Raw mouse motion is left off so two implementations do not "
                            + "deliver the same movement; Raw Input Buffer keeps its own pipeline.");
        } else if (ExternalInputCompatibility.ixerisPresent()) {
            Herzium.LOGGER.info(
                    "Ixeris detected. Raw mouse motion is enabled, which is the switch Ixeris listens to for "
                            + "its own buffered raw input. Herzium used to disable it here, which left the "
                            + "pointer on the operating system's accelerated path with no raw input at all.");
        }

        if (ExternalInputCompatibility.rawInputBufferPresent()
                && !ExternalInputCompatibility.inventoryTweaksPresent()) {
            Herzium.LOGGER.warn(
                    "KoHs Inventory Tweaks is not loaded, so Cursor Landing and the Raw Input Buffer cursor "
                            + "adapter are unavailable in this session.");
        }
    }
}
