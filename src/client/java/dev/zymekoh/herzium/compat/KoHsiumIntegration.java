package dev.zymekoh.herzium.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Read-only discovery for the cooperative KoHsium ownership contract. */
public final class KoHsiumIntegration {
    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("kohsium");

    private KoHsiumIntegration() {
    }

    public static boolean present() {
        return PRESENT;
    }
}
