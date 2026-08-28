package dev.zymekoh.herzium.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.mixin.KeyMappingAccessor;
import dev.zymekoh.herzium.render.CombatItemClassifier;
import dev.zymekoh.herzium.Herzium;
import java.util.concurrent.atomic.AtomicLong;
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
 * share that window, Herzium withholds the preview: Vanilla resolves that burst
 * in ascending slot order rather than physical arrival order, so showing either
 * interpretation early would make a rapid remap appear to disobey the player.
 * The untouched click queue remains the only authority.</p>
 */
public final class ImmediateHotbarInput {
    private static final long FAIL_SAFE_PREVIEW_NANOS = 2_000_000_000L;
    private static final long HUD_HOOK_TIMEOUT_NANOS = 1_000_000_000L;

    private static final AtomicReference<PreviewState> PREVIEW = new AtomicReference<>();
    private static final AtomicReference<PreviewState> PENDING_CONFIRMATION =
            new AtomicReference<>();
    private static final AtomicLong LAST_HUD_HOOK_NANOS = new AtomicLong();
    private static volatile boolean suspended;

    private ImmediateHotbarInput() {
    }

    /** Called after Vanilla has registered exactly one logical KeyMapping click. */
    public static void previewLogicalKey(InputConstants.Key logicalKey) {
        if (suspended || !hudHookIsHealthy()) {
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

        if (creativeHotbarAction) {
            // The modifier may arrive after another hotbar key in the same
            // platform event batch. Retaining that earlier candidate would
            // predict only the prefix of a batch Vanilla treats as a creative
            // toolbar action. Drop the whole visual candidate immediately.
            clearPreview();
            return;
        }

        int matchedSlot = -1;
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            KeyMapping mapping = minecraft.options.keyHotbarSlots[slot];
            if (((KeyMappingAccessor) mapping).herzium$getBoundKey().equals(logicalKey)) {
                // When duplicate bindings exist, Vanilla resolves hotbar slots
                // in ascending order, so the highest matching slot is final.
                matchedSlot = slot;
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
        if (suspended || !hudHookIsHealthy() || !previewIsValid(state, inventory)) {
            clearPreview(state);
            return vanillaSlot;
        }
        if (vanillaSlot != state.selectedSlotAtInput()) {
            return vanillaSlot;
        }

        // A burst containing distinct slots has two reasonable meanings: the
        // last physical key, and Vanilla's highest-numbered pending slot. Do not
        // guess on screen. Vanilla commits the real value later in this tick.
        if (state.ambiguous()) {
            return vanillaSlot;
        }
        return state.slot();
    }

    public static ItemStack visualMainHandItem(LocalPlayer player) {
        int vanillaSlot = player.getInventory().getSelectedSlot();
        int visualSlot = visualSelectedSlot(player.getInventory(), vanillaSlot);
        if (visualSlot == vanillaSlot) {
            return player.getMainHandItem();
        }

        ItemStack previewedItem = player.getInventory().getItem(visualSlot);
        // The hotbar highlight may respond for every unambiguous slot, but a
        // combat item keeps its complete Vanilla hand/equip transition. This
        // separates input feedback from the animation policy instead of making
        // combat slots look as if their hotbar binding was ignored.
        return CombatItemClassifier.preservesVanillaEquipTransition(previewedItem)
                ? player.getMainHandItem()
                : previewedItem;
    }

    /** Records that Vanilla has had a chance to consume its ordinary key queue. */
    public static void markVanillaHotbarPassCompleted(Minecraft minecraft) {
        PreviewState state = PREVIEW.get();
        LocalPlayer player = minecraft.player;
        if (state == null || player == null || state.player() != player) {
            return;
        }

        // Creative save/load modifiers are sampled by Vanilla when the whole
        // hotbar pass runs, not when the first key event arrives. A modifier
        // can therefore arrive later in the same very fast event batch. Such a
        // batch is a toolbar action, not a slot selection, so it supersedes the
        // render preview without counting as a disagreement.
        if (player.hasInfiniteMaterials()
                && (minecraft.options.keyLoadHotbarActivator.isDown()
                        || minecraft.options.keySaveHotbarActivator.isDown())) {
            clearPreview(state);
            return;
        }
        PENDING_CONFIRMATION.set(state);
    }

    /**
     * Confirms against the final slot at the end of the complete client tick.
     *
     * <p>The captured state is important for extremely rapid input. If another
     * click arrives after the keybind pass, it belongs to the next Vanilla pass
     * and replaces {@link #PREVIEW}; confirming the captured instance clears
     * only the input Vanilla actually had a chance to resolve.</p>
     */
    public static void confirmAfterClientTick(Minecraft minecraft) {
        PreviewState state = PENDING_CONFIRMATION.getAndSet(null);
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
            return;
        }

        // One mismatch is enough: displaying a slot Vanilla did not commit is
        // precisely the ghost state this feature exists to avoid. From here on
        // the current world follows Vanilla with no speculative rendering.
        suspended = true;
        PREVIEW.set(null);
        Herzium.LOGGER.warn(
                "Priority Hotbar expected slot {} from Vanilla's queued bindings but the final "
                        + "selection was slot {} (started on {}). It has suspended itself for this "
                        + "world; Herzium will now render Vanilla's committed slot only.",
                state.slot() + 1,
                vanillaSlot + 1,
                state.selectedSlotAtInput() + 1);
    }

    /** Marks the optional HUD expression hook as alive for coordinated rendering. */
    public static void observeHudHook() {
        LAST_HUD_HOOK_NANOS.set(System.nanoTime());
    }

    /** A real Vanilla wheel change supersedes any provisional key preview. */
    public static void onVanillaScrollFinished(Minecraft minecraft, int selectedSlotBeforeScroll) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (player.getInventory().getSelectedSlot() != selectedSlotBeforeScroll) {
            // A wheel selection is already the final Vanilla value. It wins
            // over both a visible candidate and a candidate captured for
            // end-of-tick confirmation, even if those references differ.
            clearPreview();
        }
    }

    public static void clearPreview() {
        PREVIEW.set(null);
        PENDING_CONFIRMATION.set(null);
    }

    /** Called when the client enters a different world; see the suspension note. */
    public static void resetSession() {
        PREVIEW.set(null);
        PENDING_CONFIRMATION.set(null);
        LAST_HUD_HOOK_NANOS.set(0L);
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
            boolean ambiguous = previousIsCurrent
                    && (previous.ambiguous() || previous.slot() != slot);
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
                return;
            }
        }
    }

    private static void clearPreview(PreviewState expected) {
        if (expected != null) {
            PREVIEW.compareAndSet(expected, null);
            PENDING_CONFIRMATION.compareAndSet(expected, null);
        }
    }

    private static boolean hudHookIsHealthy() {
        long observedNanos = LAST_HUD_HOOK_NANOS.get();
        return observedNanos != 0L
                && System.nanoTime() - observedNanos <= HUD_HOOK_TIMEOUT_NANOS;
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
