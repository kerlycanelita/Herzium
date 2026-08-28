package dev.zymekoh.herzium.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Read-only discovery for mouse-related client mods; Herzium never mutates their pipelines. */
public final class ExternalInputCompatibility {
    private static final FabricLoader LOADER = FabricLoader.getInstance();
    private static final boolean RAW_INPUT_BUFFER_PRESENT = LOADER.isModLoaded("rawinputbuffer");
    private static final boolean IXERIS_PRESENT = LOADER.isModLoaded("ixeris");
    private static final boolean INVENTORY_TWEAKS_PRESENT = LOADER.isModLoaded("kohs_inventory_tweaks");

    private ExternalInputCompatibility() {
    }

    public static boolean rawInputBufferPresent() {
        return RAW_INPUT_BUFFER_PRESENT;
    }

    public static boolean ixerisPresent() {
        return IXERIS_PRESENT;
    }

    public static boolean inventoryTweaksPresent() {
        return INVENTORY_TWEAKS_PRESENT;
    }

    /**
     * Returns whether another mod owns cursor placement across screen transitions.
     *
     * <p>This is reporting-only. Herzium never rewrites Raw Input or cursor
     * position, regardless of whether this owner is present.</p>
     */
    public static boolean cursorPipelineOwnerPresent() {
        return INVENTORY_TWEAKS_PRESENT;
    }

    public static boolean competingExternalPipelinesPresent() {
        return RAW_INPUT_BUFFER_PRESENT && IXERIS_PRESENT;
    }
}
