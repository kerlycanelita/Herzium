package dev.zymekoh.herzium.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.config.HerziumConfig;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics.InputSource;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Provides a render-only preview for logical hotbar bindings.
 *
 * <p>The class never consumes a {@link KeyMapping} click and never changes the
 * selected inventory slot. Vanilla therefore remains solely responsible for
 * committing the selection and emitting any carried-item packet. Herzium only
 * lets the HUD and first-person renderer display one unambiguous logical
 * binding while Vanilla reaches its next input tick. If distinct hotbar inputs
 * share that window, the single preview state follows Vanilla's exact
 * ascending-slot resolution instead of physical arrival order. This prevents a
 * provisional block or hand item from disagreeing with the untouched click
 * queue.</p>
 */
public final class ImmediateHotbarInput {
    private static final long FAIL_SAFE_PREVIEW_NANOS = 2_000_000_000L;
    private static final AtomicReference<PreviewState> PREVIEW = new AtomicReference<>();

    private ImmediateHotbarInput() {
    }

    /** Called after Vanilla has registered exactly one logical KeyMapping click. */
    public static void previewLogicalKey(InputConstants.Key logicalKey) {
        if (!HerziumConfig.get().hotbarPreview()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
                || player.isSpectator()
                || minecraft.screen != null
                || minecraft.getOverlay() != null) {
            return;
        }

        boolean creativeHotbarAction = player.hasInfiniteMaterials()
                && (minecraft.options.keyLoadHotbarActivator.isDown()
                        || minecraft.options.keySaveHotbarActivator.isDown());

        int matchedSlot = -1;
        if (!creativeHotbarAction) {
            for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
                KeyMapping mapping = minecraft.options.keyHotbarSlots[slot];
                if (((KeyMappingAccessor) mapping).herzium$getBoundKey().equals(logicalKey)) {
                    // When duplicate bindings exist, Vanilla resolves hotbar slots
                    // in ascending order, so the highest matching slot is final.
                    matchedSlot = slot;
                }
            }
        }
        if (matchedSlot < 0) {
            return;
        }

        InputSource source = logicalKey.getType() == InputConstants.Type.MOUSE
                ? InputSource.MOUSE
                : InputSource.KEYBOARD;
        registerPreviewCandidate(player, matchedSlot);
        CoreDiagnostics.recordHotbarInput(source, matchedSlot);
    }

    /** Returns a provisional render value; it never writes to the inventory. */
    public static int visualSelectedSlot(Inventory inventory, int vanillaSlot) {
        PreviewState state = PREVIEW.get();
        if (state == null) {
            return vanillaSlot;
        }
        // Checked here as well as in previewLogicalKey so that switching the
        // feature off drops a preview that is already in flight, instead of
        // leaving it on screen until the fail-safe deadline.
        if (!HerziumConfig.get().hotbarPreview() || !previewIsValid(state, inventory)) {
            clearPreview(state);
            return vanillaSlot;
        }
        return vanillaSlot == state.selectedSlotAtInput() ? state.slot() : vanillaSlot;
    }

    public static ItemStack visualMainHandItem(LocalPlayer player) {
        int vanillaSlot = player.getInventory().getSelectedSlot();
        int visualSlot = visualSelectedSlot(player.getInventory(), vanillaSlot);
        return visualSlot == vanillaSlot
                ? player.getMainHandItem()
                : player.getInventory().getItem(visualSlot);
    }

    /** Reads Vanilla's result after its ordinary hotbar loop and clears the preview. */
    public static void onVanillaHotbarTick(Minecraft minecraft) {
        PreviewState state = PREVIEW.get();
        LocalPlayer player = minecraft.player;
        if (state == null) {
            return;
        }
        if (player == null || state.player() != player) {
            clearPreview(state);
            return;
        }

        int vanillaSlot = player.getInventory().getSelectedSlot();
        CoreDiagnostics.recordVanillaConfirmation(vanillaSlot == state.slot());
        clearPreview(state);
    }

    /** A real Vanilla wheel change supersedes any provisional key preview. */
    public static void onVanillaScrollFinished(Minecraft minecraft, int selectedSlotBeforeScroll) {
        PreviewState state = PREVIEW.get();
        LocalPlayer player = minecraft.player;
        if (state == null || player == null || state.player() != player) {
            return;
        }
        if (player.getInventory().getSelectedSlot() != selectedSlotBeforeScroll) {
            clearPreview(state);
        }
    }

    public static void clearPreview() {
        PREVIEW.set(null);
    }

    /**
     * Frame-level safety net that also releases the retained {@link LocalPlayer}.
     *
     * <p>{@link #onVanillaHotbarTick(Minecraft)} is the only other place that
     * drops the preview, and it only runs while Vanilla is processing keybinds,
     * which it does not do without a loaded level. Letting the fail-safe
     * deadline merely make {@code previewIsValid} return {@code false} would
     * keep a strong reference to the player alive -- and through it the level,
     * its chunks and its entities -- until the next hotbar press in some later
     * session. Pressing a hotbar key and disconnecting inside that window is
     * enough to leak a whole {@code ClientLevel}. This runs on every frame
     * instead, so both a player swap and an expired preview free the state.</p>
     */
    public static void releaseStalePreview(Minecraft minecraft) {
        PreviewState state = PREVIEW.get();
        if (state == null) {
            return;
        }
        if (minecraft.player != state.player()
                || System.nanoTime() - state.startedNanos() > FAIL_SAFE_PREVIEW_NANOS) {
            clearPreview(state);
        }
    }

    private static void registerPreviewCandidate(LocalPlayer player, int slot) {
        long now = System.nanoTime();
        while (true) {
            PreviewState previous = PREVIEW.get();
            boolean previousIsCurrent = previous != null
                    && previous.player() == player
                    && now - previous.startedNanos() <= FAIL_SAFE_PREVIEW_NANOS;
            boolean newlyAmbiguous = previousIsCurrent && previous.slot() != slot && !previous.ambiguous();
            boolean ambiguous = previousIsCurrent && (previous.ambiguous() || previous.slot() != slot);
            int vanillaResolvedSlot = previousIsCurrent ? Math.max(previous.slot(), slot) : slot;
            int selectedSlotAtInput = previousIsCurrent
                    ? previous.selectedSlotAtInput()
                    : player.getInventory().getSelectedSlot();
            long startedNanos = previousIsCurrent ? previous.startedNanos() : now;
            PreviewState replacement = new PreviewState(
                    player,
                    vanillaResolvedSlot,
                    selectedSlotAtInput,
                    startedNanos,
                    ambiguous);
            if (PREVIEW.compareAndSet(previous, replacement)) {
                if (newlyAmbiguous) {
                    CoreDiagnostics.recordAmbiguousPreviewResolved();
                }
                return;
            }
        }
    }

    private static void clearPreview(PreviewState expected) {
        if (expected != null) {
            PREVIEW.compareAndSet(expected, null);
        }
    }

    private static boolean previewIsValid(PreviewState state, Inventory inventory) {
        if (state == null
                || state.slot() < 0
                || state.slot() >= Inventory.getSelectionSize()
                || state.player().getInventory() != inventory
                || Minecraft.getInstance().player != state.player()) {
            return false;
        }
        return System.nanoTime() - state.startedNanos() <= FAIL_SAFE_PREVIEW_NANOS;
    }

    private record PreviewState(
            LocalPlayer player,
            int slot,
            int selectedSlotAtInput,
            long startedNanos,
            boolean ambiguous) {
    }
}
