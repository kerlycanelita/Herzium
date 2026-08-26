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

    /** Kept for reporting: either mod changes how mouse deltas reach the game. */
    public static boolean externalRawInputPresent() {
        return RAW_INPUT_BUFFER_PRESENT || IXERIS_PRESENT;
    }

    /**
     * Whether Herzium must leave GLFW's raw mouse motion off.
     *
     * <p>Only Raw Input Buffer. It reads the Win32 raw input stream itself and
     * runs alongside GLFW's, so switching both on means two paths delivering
     * the same movement.</p>
     *
     * <p>Ixeris looks like the same case and is not, which is why it is named
     * here rather than lumped in. Its {@code GLFWMixin} intercepts
     * {@code glfwSetInputMode} for {@code GLFW_RAW_MOUSE_MOTION} (208901) and
     * forwards the value straight to {@code InputManager.setRawInput}: the flag
     * is not a competing setting, it is the switch Ixeris listens to for its own
     * buffered Win32 handler. Turning it off therefore does not prevent a
     * duplicate, it disables Ixeris's raw input as well, and the player is left
     * on the operating system's accelerated pointer path with no raw input at
     * all. Herzium turns it on and lets Ixeris take it.</p>
     */
    public static boolean rawMouseMotionOwnedElsewhere() {
        return RAW_INPUT_BUFFER_PRESENT;
    }

    public static boolean competingExternalPipelinesPresent() {
        return RAW_INPUT_BUFFER_PRESENT && IXERIS_PRESENT;
    }
}
