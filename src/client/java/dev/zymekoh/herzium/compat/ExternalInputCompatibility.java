package dev.zymekoh.herzium.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Read-only discovery for mouse-input pipelines owned by other client mods. */
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
     * <p>GLFW cursor placement is not authoritative: a later window-input change
     * can replace a landing that was already requested. KoHs Inventory Tweaks
     * verifies Cursor Landing after the transition, so Herzium must not rewrite
     * the Vanilla Raw Input window mode while that cursor pipeline is present.</p>
     */
    public static boolean cursorPipelineOwnerPresent() {
        return INVENTORY_TWEAKS_PRESENT;
    }

    public static boolean externalRawInputPresent() {
        return RAW_INPUT_BUFFER_PRESENT || IXERIS_PRESENT;
    }

    public static boolean competingExternalPipelinesPresent() {
        return RAW_INPUT_BUFFER_PRESENT && IXERIS_PRESENT;
    }
}
