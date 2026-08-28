package dev.zymekoh.herzium.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import dev.zymekoh.herzium.render.CombatItemClassifier;
import dev.zymekoh.herzium.Herzium;
import java.util.concurrent.atomic.AtomicInteger;
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

    /**
     * How many times in a row the preview may disagree with Vanilla before it
     * stops previewing for the rest of the world session.
     *
     * <p>A disagreement is not cosmetic: it means the HUD and the hand showed a
     * slot Vanilla did not end up selecting, so for up to one tick the player
     * saw an item they did not have. One-off disagreements are possible without
     * anything being wrong -- another mod can legitimately move the selection in
     * the same tick as the keypress -- so a single miss only drops that preview.
     * A run of them means something else owns hotbar selection and Herzium's
     * model of it is not valid here, and the honest response is to stop
     * guessing rather than keep being wrong quietly.</p>
     */
    private static final int MAX_CONSECUTIVE_MISMATCHES = 3;

    private static final AtomicReference<PreviewState> PREVIEW = new AtomicReference<>();
    private static final AtomicInteger CONSECUTIVE_MISMATCHES = new AtomicInteger();
    private static volatile boolean suspended;

    private ImmediateHotbarInput() {
    }

    /** Called after Vanilla has registered exactly one logical KeyMapping click. */
    public static void previewLogicalKey(InputConstants.Key logicalKey) {
        if (suspended) {
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

        registerPreviewCandidate(player, matchedSlot);
    }

    /** Returns a provisional render value; it never writes to the inventory. */
    public static int visualSelectedSlot(Inventory inventory, int vanillaSlot) {
        PreviewState state = PREVIEW.get();
        if (state == null) {
            return vanillaSlot;
        }
        // Checked here too, so a suspension takes effect on the very next
        // frame instead of leaving a preview on screen until the deadline.
        if (suspended || !previewIsValid(state, inventory)) {
            clearPreview(state);
            return vanillaSlot;
        }
        if (vanillaSlot != state.selectedSlotAtInput()) {
            return vanillaSlot;
        }

        // The hand cannot honour a preview for a combat item: it is mid-way
        // through Vanilla's equip transition, which this mod deliberately keeps.
        // Moving the HUD anyway would put the two surfaces in different states
        // -- highlight already on the new slot, hand still holding the old item
        // and dipping -- and that reads as the hotbar failing to respond.
        // Previewing only what the hand can follow keeps them in agreement:
        // ordinary items are instant everywhere, combat items are Vanilla
        // everywhere, and which one you get is predictable from the item.
        if (CombatItemClassifier.preservesVanillaEquipTransition(inventory.getItem(state.slot()))) {
            return vanillaSlot;
        }
        return state.slot();
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
        boolean matched = vanillaSlot == state.slot();
        clearPreview(state);

        if (matched) {
            CONSECUTIVE_MISMATCHES.set(0);
            return;
        }
        if (CONSECUTIVE_MISMATCHES.incrementAndGet() >= MAX_CONSECUTIVE_MISMATCHES) {
            suspended = true;
            Herzium.LOGGER.warn(
                    "Priority Hotbar disagreed with Vanilla's selection {} times in a row and has "
                            + "suspended itself for this world. Something else owns hotbar selection "
                            + "here; the hotbar now follows Vanilla exactly, one tick later.",
                    MAX_CONSECUTIVE_MISMATCHES);
        }
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

    /** Called when the client enters a different world; see the suspension note. */
    public static void resetSession() {
        PREVIEW.set(null);
        CONSECUTIVE_MISMATCHES.set(0);
        suspended = false;
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
        // A screen or overlay stops Vanilla from processing keybinds, so
        // onVanillaHotbarTick never runs to confirm and drop the preview. Left
        // alone it would stay on the HUD behind the screen for up to the
        // fail-safe deadline and then pop back.
        if (minecraft.player != state.player()
                || minecraft.screen != null
                || minecraft.getOverlay() != null
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
