package dev.zymekoh.herzium;

import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.compat.KoHsiumIntegration;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class HerziumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HerziumConfig.loadAsync();
        Herzium.LOGGER.info(
                "Herzium initialized: fast loading, low-latency input, and uncapped rendering are active.");

        if (FabricLoader.getInstance().isModLoaded("exordium")) {
            Herzium.LOGGER.info("Exordium detected; its HUD frame buffer will be bypassed.");
        }
        if (KoHsiumIntegration.present()) {
            Herzium.LOGGER.info(
                    "KoHsium cooperation active: Herzium owns VSync, focused/unfocused FPS pacing and "
                            + "immediate hotbar behavior; KoHsium owns editable input controls.");
        }
    }
}
