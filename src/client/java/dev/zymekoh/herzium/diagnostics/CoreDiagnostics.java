package dev.zymekoh.herzium.diagnostics;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small, bootstrap-safe runtime ledger used by the Core configuration page.
 *
 * <p>This class deliberately depends only on the JDK so the Mixin plugin can
 * record successfully applied mixins before Minecraft client classes are
 * available.</p>
 */
public final class CoreDiagnostics {
    public enum ConfigState {
        LOADING,
        HEALTHY,
        READ_FAILED,
        WRITE_FAILED
    }

    public enum InputSource {
        KEYBOARD,
        MOUSE
    }

    public record Snapshot(
            Set<String> appliedMixins,
            boolean coreFrameHookObserved,
            boolean keyboardHookObserved,
            boolean mouseHookObserved,
            boolean wheelHookObserved,
            boolean hotbarVisualHookObserved,
            boolean handRenderHookObserved,
            boolean containerOptimizationHookObserved,
            ConfigState configState,
            long previewRequests,
            long vanillaConfirmations,
            long previewMismatches,
            long ambiguousPreviewsResolved,
            long containerFramesOptimized,
            long instantEquipFrames,
            long combatEquipFramesPreserved,
            int lastPreviewSlot,
            InputSource lastInputSource) {
        public boolean mixinApplied(String simpleName) {
            return this.appliedMixins.contains(simpleName);
        }
    }

    private static final Set<String> APPLIED_MIXINS = ConcurrentHashMap.newKeySet();
    private static final AtomicLong PREVIEW_REQUESTS = new AtomicLong();
    private static final AtomicLong VANILLA_CONFIRMATIONS = new AtomicLong();
    private static final AtomicLong PREVIEW_MISMATCHES = new AtomicLong();
    private static final AtomicLong AMBIGUOUS_PREVIEWS_RESOLVED = new AtomicLong();
    private static final AtomicLong CONTAINER_FRAMES_OPTIMIZED = new AtomicLong();
    private static final AtomicLong INSTANT_EQUIP_FRAMES = new AtomicLong();
    private static final AtomicLong COMBAT_EQUIP_FRAMES_PRESERVED = new AtomicLong();

    private static volatile boolean keyboardHookObserved;
    private static volatile boolean coreFrameHookObserved;
    private static volatile boolean mouseHookObserved;
    private static volatile boolean wheelHookObserved;
    private static volatile boolean hotbarVisualHookObserved;
    private static volatile boolean handRenderHookObserved;
    private static volatile boolean containerOptimizationHookObserved;
    private static volatile ConfigState configState = ConfigState.LOADING;
    private static volatile int lastPreviewSlot = -1;
    private static volatile InputSource lastInputSource;
    private static volatile int currentSessionId;

    private CoreDiagnostics() {
    }

    /**
     * Starts a fresh set of counters when the client enters a different world.
     *
     * <p>The frame and preview counters are cumulative, so without this they
     * kept adding up across worlds and servers for the whole process lifetime:
     * the numbers on the config page said nothing about the session the player
     * was actually looking at. Leaving a world does not reset them -- the
     * counters stay readable after disconnecting, and are cleared by the next
     * join.</p>
     *
     * <p>Only the counters are session-scoped. Which mixins applied, and which
     * hooks have ever fired, are facts about the process and are kept.</p>
     *
     * @param sessionId an identity for the current level, or {@code 0} for none.
     *                  Passed as an int on purpose: this class must not import
     *                  Minecraft classes, so the Mixin plugin can use it before
     *                  the client classes exist.
     * @return {@code true} when this call started a new session, so callers can
     *         drop whatever else was cached for the previous one.
     */
    public static boolean observeSession(int sessionId) {
        if (sessionId == 0 || sessionId == currentSessionId) {
            return false;
        }

        currentSessionId = sessionId;
        PREVIEW_REQUESTS.set(0L);
        VANILLA_CONFIRMATIONS.set(0L);
        PREVIEW_MISMATCHES.set(0L);
        AMBIGUOUS_PREVIEWS_RESOLVED.set(0L);
        CONTAINER_FRAMES_OPTIMIZED.set(0L);
        INSTANT_EQUIP_FRAMES.set(0L);
        COMBAT_EQUIP_FRAMES_PRESERVED.set(0L);
        lastPreviewSlot = -1;
        lastInputSource = null;
        return true;
    }

    public static void recordMixinApplied(String mixinClassName) {
        int separator = mixinClassName.lastIndexOf('.');
        APPLIED_MIXINS.add(separator >= 0 ? mixinClassName.substring(separator + 1) : mixinClassName);
    }

    public static void recordHotbarInput(InputSource source, int slot) {
        if (source == InputSource.KEYBOARD) {
            keyboardHookObserved = true;
        } else {
            mouseHookObserved = true;
        }
        lastInputSource = source;
        lastPreviewSlot = slot;
        PREVIEW_REQUESTS.incrementAndGet();
    }

    public static void recordCoreFrameHook() {
        if (!coreFrameHookObserved) {
            coreFrameHookObserved = true;
        }
    }

    public static void recordHotbarVisualHook() {
        if (!hotbarVisualHookObserved) {
            hotbarVisualHookObserved = true;
        }
    }

    public static void recordWheelHook() {
        if (!wheelHookObserved) {
            wheelHookObserved = true;
        }
    }

    public static void recordHandRenderHook() {
        if (!handRenderHookObserved) {
            handRenderHookObserved = true;
        }
    }

    public static void recordContainerFrameOptimized() {
        containerOptimizationHookObserved = true;
        CONTAINER_FRAMES_OPTIMIZED.incrementAndGet();
    }

    public static void recordInstantEquipFrame() {
        INSTANT_EQUIP_FRAMES.incrementAndGet();
    }

    public static void recordCombatEquipFramePreserved() {
        COMBAT_EQUIP_FRAMES_PRESERVED.incrementAndGet();
    }

    public static void recordVanillaConfirmation(boolean matchedPreview) {
        VANILLA_CONFIRMATIONS.incrementAndGet();
        if (!matchedPreview) {
            PREVIEW_MISMATCHES.incrementAndGet();
        }
    }

    public static void recordAmbiguousPreviewResolved() {
        AMBIGUOUS_PREVIEWS_RESOLVED.incrementAndGet();
    }

    public static void recordConfigHealthy() {
        configState = ConfigState.HEALTHY;
    }

    public static void recordConfigReadFailure() {
        configState = ConfigState.READ_FAILED;
    }

    public static void recordConfigWriteFailure() {
        configState = ConfigState.WRITE_FAILED;
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                Set.copyOf(APPLIED_MIXINS),
                coreFrameHookObserved,
                keyboardHookObserved,
                mouseHookObserved,
                wheelHookObserved,
                hotbarVisualHookObserved,
                handRenderHookObserved,
                containerOptimizationHookObserved,
                configState,
                PREVIEW_REQUESTS.get(),
                VANILLA_CONFIRMATIONS.get(),
                PREVIEW_MISMATCHES.get(),
                AMBIGUOUS_PREVIEWS_RESOLVED.get(),
                CONTAINER_FRAMES_OPTIMIZED.get(),
                INSTANT_EQUIP_FRAMES.get(),
                COMBAT_EQUIP_FRAMES_PRESERVED.get(),
                lastPreviewSlot,
                lastInputSource);
    }
}
