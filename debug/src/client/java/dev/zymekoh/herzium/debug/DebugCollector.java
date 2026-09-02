package dev.zymekoh.herzium.debug;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded, asynchronous and observational trace store for Herzium QA.
 *
 * <p>No method in this class changes a key state, selected slot, screen, item,
 * packet, cooldown or world value. Chat contents and typed characters are never
 * accepted by this API. Packet tracing is deliberately restricted to packet
 * classes relevant to hotbar, hand and container diagnosis.</p>
 */
public final class DebugCollector {
    public static final String VERSION = "0.2.0";
    private static final Logger LOGGER = LoggerFactory.getLogger("Herzium Debug");
    private static final int MAX_EVENTS = 6_000;
    private static final int FILE_QUEUE_CAPACITY = 16_384;
    private static final long HOTBAR_WINDOW_NANOS = 500_000_000L;
    private static final long CURSOR_TRANSITION_NANOS = 350_000_000L;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final Object EVENT_LOCK = new Object();
    private static final Object HOTBAR_LOCK = new Object();
    private static final ArrayDeque<DebugEvent> EVENTS = new ArrayDeque<>(MAX_EVENTS);
    private static final LinkedBlockingQueue<String> FILE_QUEUE = new LinkedBlockingQueue<>(FILE_QUEUE_CAPACITY);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final AtomicLong ISSUE_COUNT = new AtomicLong();
    private static final AtomicLong DROPPED_FILE_LINES = new AtomicLong();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicBoolean RUNTIME_REPORTED = new AtomicBoolean();
    private static final long SESSION_START_NANOS = System.nanoTime();
    private static final String SESSION_ID = Long.toUnsignedString(SESSION_START_NANOS, 36);

    private static volatile Path logFile;
    private static volatile boolean writerRunning;
    private static volatile long frameStartedNanos;
    private static volatile long tickStartedNanos;
    private static final AtomicLong FRAMES = new AtomicLong();
    private static final AtomicLong FRAME_NANOS = new AtomicLong();
    private static final AtomicLong MAX_FRAME_NANOS = new AtomicLong();
    private static final AtomicLong TICKS = new AtomicLong();
    private static final AtomicLong TICK_NANOS = new AtomicLong();
    private static final AtomicLong MOUSE_MOVES = new AtomicLong();
    private static final AtomicLong MOUSE_DISTANCE_MILLI = new AtomicLong();
    private static final AtomicLong RELEVANT_PACKETS = new AtomicLong();
    private static final AtomicLong HUD_HOOKS = new AtomicLong();
    private static final AtomicLong STALE_CHECKS = new AtomicLong();
    private static volatile long sampleStartedNanos = System.nanoTime();
    private static volatile StatusSnapshot status = StatusSnapshot.empty();

    private static volatile int lastSelectedSlot = -1;
    private static volatile String lastMainHand = "<unset>";
    private static volatile String lastOffhand = "<unset>";
    private static volatile String lastScreen = "<unset>";
    private static volatile String lastOverlay = "<unset>";
    private static volatile boolean lastWindowActive;
    private static volatile double lastMouseX = Double.NaN;
    private static volatile double lastMouseY = Double.NaN;
    private static volatile long lastScreenTransitionNanos;
    private static volatile String screenTransitionTarget = "none";
    private static volatile long expectedVanillaReleaseCenterUntilNanos;
    private static volatile String lastRenderedMain = "<unset>";
    private static volatile String lastRenderedOffhand = "<unset>";
    private static volatile float lastMainHeight = Float.NaN;
    private static volatile float lastOffhandHeight = Float.NaN;
    private static volatile int lastPreviewedSlot = -1;
    private static volatile long lastPreviewNanos;
    private static volatile String lastHerziumHandPreview = "<none>";
    private static volatile int lastPacketSlot = -1;
    private static volatile long lastPacketSlotNanos;
    private static volatile long clientTickNumber;
    private static final Map<String, Boolean> KEY_STATES = new HashMap<>();
    private static HotbarBurst hotbarBurst;
    private static HotbarBurst confirmationBurst;

    private DebugCollector() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        try {
            Path directory = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("herzium-debug");
            Files.createDirectories(directory);
            logFile = directory.resolve("herzium-debug-" + FILE_TIME.format(LocalDateTime.now()) + ".log");
            writerRunning = true;
            Thread.ofPlatform().daemon(true).name("Herzium Debug log writer").start(DebugCollector::writeLoop);
            Runtime.getRuntime().addShutdownHook(Thread.ofPlatform()
                    .name("Herzium Debug shutdown")
                    .unstarted(() -> {
                        writerRunning = false;
                        flushQueueSynchronously();
                    }));
        } catch (IOException exception) {
            LOGGER.warn("Could not create the Herzium Debug log file; the in-memory viewer remains available.", exception);
        }

        info("SESSION", "Herzium Debug " + VERSION + " started; session=" + SESSION_ID);
        info("BOUNDARY", "Observer-only L0 instrumentation: no input, action, cooldown, inventory or packet mutation.");
        info("PRIVACY", "Chat contents and typed characters are not recorded; only bound gameplay actions and safe packet metadata are traced.");
        info("FILE", "Persistent log=" + (logFile == null ? "unavailable" : logFile.toAbsolutePath()));
        LOGGER.info("Herzium Debug {} started. Live trace: {}", VERSION, logFile);
    }

    public static void trace(String category, String message) {
        add(Level.TRACE, category, message);
    }

    public static void info(String category, String message) {
        add(Level.INFO, category, message);
    }

    public static void warn(String category, String message) {
        add(Level.WARN, category, message);
    }

    public static void issue(String category, String message) {
        ISSUE_COUNT.incrementAndGet();
        add(Level.ERROR, category, message);
        LOGGER.warn("[{}] {}", category, message);
    }

    private static void add(Level level, String category, String message) {
        long sequence = SEQUENCE.incrementAndGet();
        long elapsedNanos = Math.max(0L, System.nanoTime() - SESSION_START_NANOS);
        DebugEvent event = new DebugEvent(
                sequence,
                elapsedNanos,
                Thread.currentThread().getName(),
                level,
                sanitize(category),
                sanitize(message));
        synchronized (EVENT_LOCK) {
            if (EVENTS.size() == MAX_EVENTS) {
                EVENTS.removeFirst();
            }
            EVENTS.addLast(event);
        }
        if (logFile != null && !FILE_QUEUE.offer(event.fileLine())) {
            DROPPED_FILE_LINES.incrementAndGet();
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    public static List<DebugEvent> snapshot(int maximum) {
        synchronized (EVENT_LOCK) {
            int skip = Math.max(0, EVENTS.size() - Math.max(1, maximum));
            List<DebugEvent> result = new ArrayList<>(EVENTS.size() - skip);
            int index = 0;
            for (DebugEvent event : EVENTS) {
                if (index++ >= skip) {
                    result.add(event);
                }
            }
            return result;
        }
    }

    public static long latestSequence() {
        return SEQUENCE.get();
    }

    public static StatusSnapshot status() {
        return status;
    }

    public static Path logFile() {
        return logFile;
    }

    public static void addUserMarker() {
        info("MARK", "========== USER MARKER ==========");
    }

    public static String fullReport() {
        StringBuilder report = new StringBuilder(256_000);
        report.append("HERZIUM DEBUG REPORT\n")
                .append("debugVersion=").append(VERSION).append('\n')
                .append("session=").append(SESSION_ID).append('\n')
                .append("generated=").append(LocalDateTime.now()).append('\n')
                .append("minecraft=").append(modVersion("minecraft")).append('\n')
                .append("herzium=").append(modVersion("herzium")).append('\n')
                .append("fabricLoader=").append(loaderVersion()).append('\n')
                .append("java=").append(System.getProperty("java.version")).append('\n')
                .append("os=").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append('\n')
                .append("logFile=").append(logFile == null ? "unavailable" : logFile.toAbsolutePath()).append('\n')
                .append("droppedFileLines=").append(DROPPED_FILE_LINES.get()).append('\n')
                .append("privacy=No chat contents or typed characters are recorded.\n")
                .append("boundary=L0 observer; no input/action/packet mutation.\n")
                .append("status=").append(status.compact()).append("\n\n")
                .append("LOADED MODS\n");

        FabricLoader.getInstance().getAllMods().stream()
                .sorted(Comparator.comparing(mod -> mod.getMetadata().getId()))
                .forEach(mod -> report.append(mod.getMetadata().getId())
                        .append('=')
                        .append(mod.getMetadata().getVersion().getFriendlyString())
                        .append('\n'));

        report.append("\nEVENTS\n");
        synchronized (EVENT_LOCK) {
            for (DebugEvent event : EVENTS) {
                report.append(event.fileLine()).append('\n');
            }
        }
        return report.toString();
    }

    private static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(ModContainer::getMetadata)
                .map(metadata -> metadata.getVersion().getFriendlyString())
                .orElse("not-loaded");
    }

    public static void onFrameStart() {
        frameStartedNanos = System.nanoTime();
    }

    public static void onFrameEnd(Minecraft minecraft) {
        long now = System.nanoTime();
        long elapsed = Math.max(0L, now - frameStartedNanos);
        FRAMES.incrementAndGet();
        FRAME_NANOS.addAndGet(elapsed);
        MAX_FRAME_NANOS.accumulateAndGet(elapsed, Math::max);
        reportRuntimeOnce(minecraft);
        observeWindowState(minecraft, now);
        samplePerformance(minecraft, now);
    }

    public static void onTickStart() {
        tickStartedNanos = System.nanoTime();
        clientTickNumber++;
    }

    public static void onTickEnd(Minecraft minecraft) {
        TICKS.incrementAndGet();
        TICK_NANOS.addAndGet(Math.max(0L, System.nanoTime() - tickStartedNanos));
        LocalPlayer player = minecraft.player;
        if (player == null) {
            if (lastSelectedSlot != -1) {
                info("WORLD", "Player detached; clearing session state.");
            }
            lastSelectedSlot = -1;
            lastMainHand = "<none>";
            lastOffhand = "<none>";
            synchronized (HOTBAR_LOCK) {
                hotbarBurst = null;
                confirmationBurst = null;
            }
            return;
        }

        int selected = player.getInventory().getSelectedSlot();
        if (selected != lastSelectedSlot) {
            int previous = lastSelectedSlot;
            lastSelectedSlot = selected;
            long latencyMicros = hotbarLatencyMicros();
            info("HOTBAR_COMMIT", "selected " + slot(previous) + " -> " + slot(selected)
                    + (latencyMicros < 0 ? "" : "; sinceInput=" + latencyMicros + "us"));
        }

        String main = stack(player.getMainHandItem());
        String off = stack(player.getOffhandItem());
        if (!main.equals(lastMainHand)) {
            info("MAIN_HAND", lastMainHand + " -> " + main + "; selected=" + slot(selected));
            lastMainHand = main;
        }
        if (!off.equals(lastOffhand)) {
            info("OFFHAND", lastOffhand + " -> " + off);
            lastOffhand = off;
        }
    }

    private static void reportRuntimeOnce(Minecraft minecraft) {
        if (!RUNTIME_REPORTED.compareAndSet(false, true)) {
            return;
        }
        info("RUNTIME", "MC=" + modVersion("minecraft") + "; Herzium=" + modVersion("herzium")
                + "; Loader=" + loaderVersion());
        info("VIDEO", "vsync=" + minecraft.options.enableVsync().get()
                + "; fpsLimit=" + minecraft.options.framerateLimit().get()
                + "; guiScale=" + minecraft.options.guiScale().get()
                + "; rawInput=" + minecraft.options.rawMouseInput().get());
        List<String> relevant = List.of(
                "herzium", "herzium_debug", "exordium", "kohs_inventory_tweaks", "rawinputbuffer",
                "ixeris", "sodium", "immediatelyfast", "modmenu");
        StringJoiner loaded = new StringJoiner(", ");
        for (String id : relevant) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                loaded.add(id + "=" + modVersion(id));
            }
        }
        info("COMPAT", "Relevant loaded mods: " + loaded);
        logBindings(minecraft);
    }

    private static void logBindings(Minecraft minecraft) {
        StringJoiner hotbar = new StringJoiner(", ");
        for (int slot = 0; slot < minecraft.options.keyHotbarSlots.length; slot++) {
            hotbar.add((slot + 1) + "=" + minecraft.options.keyHotbarSlots[slot].saveString());
        }
        info("BINDINGS", "hotbar{" + hotbar + "}; use=" + minecraft.options.keyUse.saveString()
                + "; attack=" + minecraft.options.keyAttack.saveString()
                + "; inventory=" + minecraft.options.keyInventory.saveString()
                + "; offhand=" + minecraft.options.keySwapOffhand.saveString());
    }

    private static void observeWindowState(Minecraft minecraft, long now) {
        String screen = className(minecraft.screen);
        String overlay = className(minecraft.getOverlay());
        boolean active = minecraft.isWindowActive();
        if (!screen.equals(lastScreen)) {
            info("SCREEN", lastScreen + " -> " + screen);
            lastScreen = screen;
        }
        if (!overlay.equals(lastOverlay)) {
            info("OVERLAY", lastOverlay + " -> " + overlay);
            lastOverlay = overlay;
        }
        if (active != lastWindowActive) {
            info("FOCUS", "windowActive=" + active);
            lastWindowActive = active;
        }
        synchronized (HOTBAR_LOCK) {
            if (hotbarBurst != null && now - hotbarBurst.startedNanos > HOTBAR_WINDOW_NANOS) {
                warn("HOTBAR_INPUT_TIMEOUT", "No Vanilla hotbar pass within 500ms for " + hotbarBurst.describe());
                hotbarBurst = null;
            }
            if (confirmationBurst != null
                    && now - confirmationBurst.startedNanos > HOTBAR_WINDOW_NANOS) {
                warn("HOTBAR_CONFIRM_TIMEOUT", "No end-of-tick confirmation within 500ms for "
                        + confirmationBurst.describe());
                confirmationBurst = null;
            }
        }
    }

    private static void samplePerformance(Minecraft minecraft, long now) {
        long interval = now - sampleStartedNanos;
        if (interval < 1_000_000_000L) {
            return;
        }
        sampleStartedNanos = now;
        long frames = FRAMES.getAndSet(0L);
        long frameNanos = FRAME_NANOS.getAndSet(0L);
        long maxFrame = MAX_FRAME_NANOS.getAndSet(0L);
        long ticks = TICKS.getAndSet(0L);
        long tickNanos = TICK_NANOS.getAndSet(0L);
        long mouseMoves = MOUSE_MOVES.getAndSet(0L);
        long mouseDistanceMilli = MOUSE_DISTANCE_MILLI.getAndSet(0L);
        long packets = RELEVANT_PACKETS.getAndSet(0L);
        long hudHooks = HUD_HOOKS.getAndSet(0L);
        long staleChecks = STALE_CHECKS.getAndSet(0L);

        double seconds = interval / 1_000_000_000.0;
        double fps = frames / seconds;
        double tps = ticks / seconds;
        double avgFrameMs = frames == 0 ? 0.0 : frameNanos / 1_000_000.0 / frames;
        double maxFrameMs = maxFrame / 1_000_000.0;
        double avgTickMs = ticks == 0 ? 0.0 : tickNanos / 1_000_000.0 / ticks;
        double mouseDistance = mouseDistanceMilli / 1000.0;
        status = new StatusSnapshot(
                fps,
                tps,
                avgFrameMs,
                maxFrameMs,
                avgTickMs,
                mouseMoves,
                mouseDistance,
                packets,
                hudHooks,
                staleChecks,
                ISSUE_COUNT.get(),
                lastSelectedSlot,
                className(minecraft.screen),
                minecraft.isWindowActive());
        trace("PERF", status.compact());
    }

    public static void onSetScreen(Minecraft minecraft, Screen requested, boolean after) {
        long now = System.nanoTime();
        if (!after) {
            lastScreenTransitionNanos = now;
            screenTransitionTarget = className(requested);
            info("SCREEN_REQUEST", className(minecraft.screen) + " -> " + screenTransitionTarget
                    + "; cursor=" + mousePosition(minecraft) + "; caller=" + callerSummary());
        } else {
            info("SCREEN_APPLIED", "current=" + className(minecraft.screen)
                    + "; cursor=" + mousePosition(minecraft)
                    + "; grabbed=" + minecraft.mouseHandler.isMouseGrabbed());
        }
    }

    public static void onKeyClick(InputConstants.Key key) {
        Minecraft minecraft = Minecraft.getInstance();
        List<KeyMapping> mappings = mappingsFor(minecraft, key);
        String names = mappingNames(mappings);
        trace("KEY_CLICK", "physical=" + key.getName() + "; mappings=" + names
                + "; screen=" + className(minecraft.screen) + "; tick=" + clientTickNumber);

        List<Integer> slots = hotbarSlots(minecraft, key);
        if (slots.isEmpty() || minecraft.player == null || minecraft.screen != null || minecraft.getOverlay() != null) {
            return;
        }
        long now = System.nanoTime();
        synchronized (HOTBAR_LOCK) {
            int before = minecraft.player.getInventory().getSelectedSlot();
            if (hotbarBurst == null || hotbarBurst.tick != clientTickNumber || now - hotbarBurst.startedNanos > HOTBAR_WINDOW_NANOS) {
                hotbarBurst = new HotbarBurst(SEQUENCE.get(), clientTickNumber, now, before);
                if (confirmationBurst != null) {
                    info("HOTBAR_NEXT_GENERATION", "New input arrived after Vanilla pass for "
                            + confirmationBurst.describe() + "; started " + hotbarBurst.describe());
                }
            }
            for (int slot : slots) {
                hotbarBurst.slots.add(slot);
                hotbarBurst.expectedVanillaSlot = Math.max(hotbarBurst.expectedVanillaSlot, slot);
            }
            hotbarBurst.lastInputNanos = now;
            hotbarBurst.distinct = hotbarBurst.slots.stream().distinct().count() > 1;
            info("HOTBAR_INPUT", "key=" + key.getName() + "; matched=" + slots.stream().map(DebugCollector::slot).toList()
                    + "; " + hotbarBurst.describe());
            if (hotbarBurst.distinct) {
                info("HOTBAR_BURST", "Distinct same-tick inputs detected; visual candidate should follow Vanilla's highest pending slot. "
                        + hotbarBurst.describe());
            }
        }
    }

    public static void onKeyState(InputConstants.Key key, boolean state) {
        Minecraft minecraft = Minecraft.getInstance();
        List<KeyMapping> mappings = mappingsFor(minecraft, key);
        if (mappings.isEmpty()) {
            return;
        }
        String id = key.getName();
        synchronized (KEY_STATES) {
            Boolean previous = KEY_STATES.put(id, state);
            if (previous != null && previous == state) {
                return;
            }
        }
        trace("KEY_STATE", "physical=" + id + "; down=" + state + "; mappings=" + mappingNames(mappings));
    }

    public static void onKeyConsumed(KeyMapping mapping, boolean consumed) {
        if (!consumed) {
            return;
        }
        trace("KEY_CONSUME", "mapping=" + mapping.getName() + "; bound=" + mapping.saveString()
                + "; tick=" + clientTickNumber);
        if (mapping.getName().startsWith("key.hotbar.")) {
            synchronized (HOTBAR_LOCK) {
                if (hotbarBurst != null) {
                    hotbarBurst.consumedMappings.add(mapping.getName());
                }
            }
        }
    }

    private static List<KeyMapping> mappingsFor(Minecraft minecraft, InputConstants.Key key) {
        if (minecraft == null || minecraft.options == null) {
            return List.of();
        }
        String physical = key.getName();
        List<KeyMapping> mappings = new ArrayList<>();
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.saveString().equals(physical)) {
                mappings.add(mapping);
            }
        }
        return mappings;
    }

    private static String mappingNames(List<KeyMapping> mappings) {
        if (mappings.isEmpty()) {
            return "[]";
        }
        return mappings.stream().map(KeyMapping::getName).sorted().toList().toString();
    }

    private static List<Integer> hotbarSlots(Minecraft minecraft, InputConstants.Key key) {
        if (minecraft == null || minecraft.options == null) {
            return List.of();
        }
        String physical = key.getName();
        List<Integer> slots = new ArrayList<>();
        for (int index = 0; index < minecraft.options.keyHotbarSlots.length; index++) {
            if (minecraft.options.keyHotbarSlots[index].saveString().equals(physical)) {
                slots.add(index);
            }
        }
        return slots;
    }

    public static void onHerziumPreviewRequested(InputConstants.Key key) {
        trace("HERZIUM_PREVIEW_REQUEST", "logicalKey=" + key.getName());
    }

    public static void onHerziumVisualSlot(int vanillaSlot, int returnedSlot) {
        if (returnedSlot == vanillaSlot) {
            return;
        }
        int previousPreview;
        long previewNanos = System.nanoTime();
        synchronized (HOTBAR_LOCK) {
            HotbarBurst associated = hotbarBurst != null ? hotbarBurst : confirmationBurst;
            previousPreview = associated == null ? lastPreviewedSlot : associated.lastPreviewedSlot;
            lastPreviewedSlot = returnedSlot;
            lastPreviewNanos = previewNanos;
            if (associated != null) {
                associated.lastPreviewedSlot = returnedSlot;
                associated.lastPreviewNanos = previewNanos;
            }
        }
        if (previousPreview != returnedSlot) {
            info("HERZIUM_PREVIEW_VISIBLE", "HUD " + slot(vanillaSlot) + " -> " + slot(returnedSlot)
                    + "; sinceInput=" + hotbarLatencyMicros() + "us");
        }
    }

    public static void onHerziumVisualMainHand(ItemStack authoritative, ItemStack returned) {
        if (ItemStack.matches(authoritative, returned)) {
            lastHerziumHandPreview = "<none>";
            return;
        }
        String transition = stack(authoritative) + " -> " + stack(returned);
        if (!transition.equals(lastHerziumHandPreview)) {
            lastHerziumHandPreview = transition;
            trace("HERZIUM_HAND_PREVIEW", transition + "; sinceInput=" + hotbarLatencyMicros() + "us");
        }
    }

    public static void onHerziumHotbarPass() {
        synchronized (HOTBAR_LOCK) {
            if (hotbarBurst != null) {
                if (confirmationBurst != null) {
                    issue("HOTBAR_CONFIRM_OVERLAP", "A second Vanilla pass completed before the previous one was confirmed: old="
                            + confirmationBurst.describe() + "; new=" + hotbarBurst.describe());
                }
                confirmationBurst = hotbarBurst;
                hotbarBurst = null;
                trace("HERZIUM_HOTBAR_PASS", "Vanilla keybind pass completed; "
                        + confirmationBurst.describe());
            }
        }
    }

    public static void onHerziumConfirm(boolean after) {
        Minecraft minecraft = Minecraft.getInstance();
        int selected = minecraft.player == null ? -1 : minecraft.player.getInventory().getSelectedSlot();
        if (!after) {
            synchronized (HOTBAR_LOCK) {
                if (confirmationBurst != null) {
                    trace("HERZIUM_CONFIRM_BEGIN", "selected=" + slot(selected) + "; "
                            + confirmationBurst.describe());
                }
            }
            return;
        }
        boolean hadPendingBurst;
        synchronized (HOTBAR_LOCK) {
            HotbarBurst confirmed = confirmationBurst;
            hadPendingBurst = confirmed != null;
            if (confirmed != null) {
                int expected = confirmed.expectedVanillaSlot;
                if (selected != expected) {
                    issue("HOTBAR_MISMATCH", "Vanilla committed " + slot(selected) + " but queued bindings predicted "
                            + slot(expected) + "; " + confirmed.describe());
                } else {
                    info("HOTBAR_CONFIRMED", "Vanilla committed " + slot(selected)
                            + "; latency=" + ((System.nanoTime() - confirmed.startedNanos) / 1_000L) + "us; "
                            + confirmed.describe());
                }
                if (confirmed.lastPreviewedSlot >= 0
                        && System.nanoTime() - confirmed.lastPreviewNanos < HOTBAR_WINDOW_NANOS
                        && confirmed.lastPreviewedSlot != selected) {
                    if (confirmed.lastPreviewNanos >= confirmed.lastInputNanos) {
                        issue("VISIBLE_GHOST", "Last visible Herzium preview="
                                + slot(confirmed.lastPreviewedSlot)
                                + " but Vanilla committed=" + slot(selected));
                    } else {
                        info("PREVIEW_SUPERSEDED_BEFORE_FRAME", "Last visible preview="
                                + slot(confirmed.lastPreviewedSlot) + "; a newer input selected " + slot(selected)
                                + " only " + ((System.nanoTime() - confirmed.lastInputNanos) / 1_000L)
                                + "us before Vanilla committed it, with no intervening HUD frame.");
                    }
                }
                confirmationBurst = null;
                lastPreviewedSlot = -1;
                lastHerziumHandPreview = "<none>";
            }
        }
        if (hadPendingBurst) {
            trace("HERZIUM_CONFIRM_END", "selected=" + slot(selected));
        }
    }

    public static void onHerziumHudHook() {
        HUD_HOOKS.incrementAndGet();
    }

    public static void onHerziumScroll(int before, int after) {
        info("HERZIUM_SCROLL", "Vanilla wheel " + slot(before) + " -> " + slot(after)
                + (before == after ? "; no selection change" : "; preview should be cleared"));
    }

    public static void onHerziumReset() {
        info("HERZIUM_RESET", "Preview session reset.");
        synchronized (HOTBAR_LOCK) {
            hotbarBurst = null;
            confirmationBurst = null;
        }
        lastPreviewedSlot = -1;
    }

    public static void onHerziumStaleCheck() {
        STALE_CHECKS.incrementAndGet();
    }

    public static void onMouseButton(MouseButtonInfo button, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        trace("MOUSE_BUTTON", "button=" + button.button() + "; modifiers=" + button.modifiers()
                + "; action=" + action + "; screen=" + className(minecraft.screen)
                + "; mapped=" + mappingsFor(minecraft, InputConstants.Type.MOUSE.getOrCreate(button.button())).stream()
                        .map(KeyMapping::getName).toList());
    }

    public static void onMouseScroll(double xOffset, double yOffset, int slotBefore, int slotAfter, boolean after) {
        if (!after) {
            trace("MOUSE_SCROLL_RAW", "x=" + format(xOffset) + "; y=" + format(yOffset)
                    + "; selectedBefore=" + slot(slotBefore));
        } else {
            info("MOUSE_SCROLL_COMMIT", "selected " + slot(slotBefore) + " -> " + slot(slotAfter)
                    + "; x=" + format(xOffset) + "; y=" + format(yOffset));
        }
    }

    public static void onMouseMove(double x, double y) {
        MOUSE_MOVES.incrementAndGet();
        if (!Double.isNaN(lastMouseX)) {
            double dx = x - lastMouseX;
            double dy = y - lastMouseY;
            double distance = Math.hypot(dx, dy);
            MOUSE_DISTANCE_MILLI.addAndGet(Math.round(distance * 1_000.0));

            Minecraft minecraft = Minecraft.getInstance();
            long sinceScreen = System.nanoTime() - lastScreenTransitionNanos;
            if (sinceScreen >= 0L && sinceScreen <= CURSOR_TRANSITION_NANOS) {
                double centerX = minecraft.getWindow().getScreenWidth() / 2.0;
                double centerY = minecraft.getWindow().getScreenHeight() / 2.0;
                boolean centered = Math.abs(x - centerX) <= 2.0 && Math.abs(y - centerY) <= 2.0;
                if (centered && distance >= 16.0) {
                    String message = "Cursor jump to center after screen target=" + screenTransitionTarget
                            + "; from=(" + format(lastMouseX) + ',' + format(lastMouseY) + ")"
                            + "; to=(" + format(x) + ',' + format(y) + ")"
                            + "; delta=" + format(distance) + "; grabbed=" + minecraft.mouseHandler.isMouseGrabbed();
                    if (System.nanoTime() <= expectedVanillaReleaseCenterUntilNanos) {
                        info("CURSOR_CENTER_VANILLA_RELEASE", message
                                + "; expected from MouseHandler.releaseMouse when a GUI opens");
                    } else if (!"none".equals(screenTransitionTarget)) {
                        issue("CURSOR_CENTER_ON_OPEN", message);
                    } else {
                        info("CURSOR_CENTER_ON_CLOSE", message + "; expected when Vanilla grabs the mouse");
                    }
                }
            }
        }
        lastMouseX = x;
        lastMouseY = y;
    }

    public static void onMouseGrab(boolean grabbed) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean wasGrabbed = minecraft.mouseHandler.isMouseGrabbed();
        if (!grabbed && wasGrabbed) {
            expectedVanillaReleaseCenterUntilNanos = System.nanoTime() + CURSOR_TRANSITION_NANOS;
        }
        info("MOUSE_GRAB", "requested=" + grabbed + "; wasGrabbed=" + wasGrabbed
                + "; screen=" + className(minecraft.screen)
                + "; cursor=" + mousePosition(minecraft) + "; caller=" + callerSummary());
    }

    public static void onHandsRendered(
            ItemStack renderedMain,
            ItemStack renderedOff,
            float mainHeight,
            float oldMainHeight,
            float offHeight,
            float oldOffHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        String main = stack(renderedMain);
        String off = stack(renderedOff);
        boolean changed = !main.equals(lastRenderedMain) || !off.equals(lastRenderedOffhand)
                || Math.abs(mainHeight - lastMainHeight) >= 0.10F
                || Math.abs(offHeight - lastOffhandHeight) >= 0.10F;
        if (!changed) {
            return;
        }
        trace("HAND_RENDER", "renderedMain=" + main + "; authoritativeMain=" + stack(player.getMainHandItem())
                + "; mainHeight=" + format(mainHeight) + "/" + format(oldMainHeight)
                + "; renderedOff=" + off + "; authoritativeOff=" + stack(player.getOffhandItem())
                + "; offHeight=" + format(offHeight) + "/" + format(oldOffHeight));
        lastRenderedMain = main;
        lastRenderedOffhand = off;
        lastMainHeight = mainHeight;
        lastOffhandHeight = offHeight;
    }

    public static void onActionCall(String action, String details) {
        trace("VANILLA_ACTION", action + (details == null || details.isBlank() ? "" : "; " + details));
        reportActionOnPreviewedSlot(action);
    }

    /**
     * Reports an action that ran while the HUD was previewing a different slot.
     *
     * <p>Herzium never moves the action to the previewed slot: Vanilla acts with
     * the slot it has committed. So between the preview becoming visible and
     * Vanilla committing it, a press acts with the previous item while the next
     * one is already on screen. Whether a player can actually land a press
     * inside that window is a question worth measuring rather than assuming,
     * which is what this records: the slot shown, the slot used, and both
     * items.</p>
     *
     * <p>A live preview is the only condition checked, because confirmation
     * clears {@code lastPreviewedSlot}. The elapsed-time guard only discards
     * stale state left behind by a preview that was never confirmed.</p>
     */
    private static void reportActionOnPreviewedSlot(String action) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        int previewed = lastPreviewedSlot;
        if (previewed < 0 || System.nanoTime() - lastPreviewNanos >= HOTBAR_WINDOW_NANOS) {
            return;
        }

        int committed = player.getInventory().getSelectedSlot();
        if (previewed == committed) {
            return;
        }

        issue("ACTION_ON_PREVIEWED_SLOT", action + " ran with " + slot(committed)
                + " while the HUD was already showing " + slot(previewed)
                + "; used " + stack(player.getInventory().getItem(committed))
                + " but displayed " + stack(player.getInventory().getItem(previewed))
                + "; sinceInput=" + hotbarLatencyMicros() + "us");
    }

    public static void onActionResult(String action, Object result) {
        trace("VANILLA_RESULT", action + " -> " + result);
    }

    public static void onPacketSent(Packet<?> packet) {
        String detail;
        if (packet instanceof ServerboundSetCarriedItemPacket carried) {
            int slot = carried.getSlot();
            long now = System.nanoTime();
            detail = "SetCarriedItem slot=" + slot(slot) + "; sinceInput=" + hotbarLatencyMicros() + "us";
            if (lastPacketSlot == slot && now - lastPacketSlotNanos < 100_000_000L) {
                warn("PACKET_REPEAT", "Repeated carried-slot packet for " + slot(slot) + " within "
                        + ((now - lastPacketSlotNanos) / 1_000L) + "us");
            }
            lastPacketSlot = slot;
            lastPacketSlotNanos = now;
        } else if (packet instanceof ServerboundUseItemPacket use) {
            detail = "UseItem hand=" + use.getHand() + "; sequence=" + use.getSequence()
                    + "; rot=" + format(use.getYRot()) + '/' + format(use.getXRot());
        } else if (packet instanceof ServerboundUseItemOnPacket useOn) {
            detail = "UseItemOn hand=" + useOn.getHand() + "; sequence=" + useOn.getSequence()
                    + "; pos=" + useOn.getHitResult().getBlockPos() + "; face=" + useOn.getHitResult().getDirection();
        } else if (packet instanceof ServerboundPlayerActionPacket action) {
            detail = "PlayerAction action=" + action.getAction() + "; sequence=" + action.getSequence()
                    + "; pos=" + action.getPos() + "; face=" + action.getDirection();
        } else if (packet instanceof ServerboundContainerClickPacket click) {
            detail = "ContainerClick id=" + click.containerId() + "; state=" + click.stateId()
                    + "; slot=" + click.slotNum() + "; button=" + click.buttonNum()
                    + "; type=" + click.containerInput() + "; changed=" + click.changedSlots().size();
        } else if (packet instanceof ServerboundSetCreativeModeSlotPacket creative) {
            detail = "CreativeSlot slot=" + creative.slotNum() + "; item=" + stack(creative.itemStack());
        } else if (packet instanceof ServerboundInteractPacket) {
            detail = "InteractEntity";
        } else if (packet instanceof ServerboundSwingPacket swing) {
            // Traced so that a swing at empty air is visible as one arm swing
            // and nothing else. Without it the log merely omits the miss, which
            // proves less than showing exactly what Vanilla did send.
            detail = "Swing hand=" + swing.getHand();
        } else {
            return;
        }
        RELEVANT_PACKETS.incrementAndGet();
        info("PACKET_OUT", detail);
    }

    public static void onContainerSlot(ClientboundContainerSetSlotPacket packet) {
        info("PACKET_IN", "ContainerSetSlot id=" + packet.getContainerId() + "; state=" + packet.getStateId()
                + "; slot=" + packet.getSlot() + "; item=" + stack(packet.getItem()));
    }

    public static void onContainerContent(ClientboundContainerSetContentPacket packet) {
        info("PACKET_IN", "ContainerContent id=" + packet.containerId() + "; state=" + packet.stateId()
                + "; slots=" + packet.items().size() + "; carried=" + stack(packet.carriedItem()));
    }

    public static String stack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        StringBuilder value = new StringBuilder(id).append('x').append(stack.getCount());
        if (stack.isDamageableItem()) {
            value.append("@").append(stack.getDamageValue()).append('/').append(stack.getMaxDamage());
        }
        return value.toString();
    }

    private static String loaderVersion() {
        return FabricLoader.getInstance().getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static String className(Object value) {
        return value == null ? "none" : value.getClass().getSimpleName();
    }

    private static String slot(int slot) {
        return slot < 0 ? "none" : Integer.toString(slot + 1);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static long hotbarLatencyMicros() {
        HotbarBurst current = hotbarBurst != null ? hotbarBurst : confirmationBurst;
        return current == null ? -1L : Math.max(0L, System.nanoTime() - current.startedNanos) / 1_000L;
    }

    private static String burstDescription() {
        HotbarBurst current = hotbarBurst != null ? hotbarBurst : confirmationBurst;
        return current == null ? "no pending burst" : current.describe();
    }

    private static String mousePosition(Minecraft minecraft) {
        return '(' + format(minecraft.mouseHandler.xpos()) + ',' + format(minecraft.mouseHandler.ypos()) + ')';
    }

    private static String callerSummary() {
        StringJoiner joiner = new StringJoiner(" <- ");
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String owner = frame.getClassName();
            if (owner.startsWith("java.")
                    || owner.startsWith("jdk.")
                    || owner.startsWith(DebugCollector.class.getPackageName())
                    || owner.equals("net.minecraft.client.MouseHandler")
                    || owner.equals("net.minecraft.client.Minecraft")) {
                continue;
            }
            joiner.add(owner + '#' + frame.getMethodName() + ':' + frame.getLineNumber());
            if (joiner.length() > 220) {
                break;
            }
        }
        String value = joiner.toString();
        return value.isBlank() ? "vanilla/unknown" : value;
    }

    private static void writeLoop() {
        Path destination = logFile;
        if (destination == null) {
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                destination,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            while (writerRunning || !FILE_QUEUE.isEmpty()) {
                String line = FILE_QUEUE.poll(250L, TimeUnit.MILLISECONDS);
                if (line != null) {
                    writer.write(line);
                    writer.newLine();
                    int drained = 0;
                    while (drained++ < 512 && (line = FILE_QUEUE.poll()) != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                writer.flush();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException exception) {
            LOGGER.warn("Herzium Debug file writer stopped; the in-memory viewer remains available.", exception);
        }
    }

    private static void flushQueueSynchronously() {
        Path destination = logFile;
        if (destination == null || FILE_QUEUE.isEmpty()) {
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                destination,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            String line;
            while ((line = FILE_QUEUE.poll()) != null) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException ignored) {
            // The JVM is shutting down; there is no safe recovery path here.
        }
    }

    public enum Level {
        TRACE,
        INFO,
        WARN,
        ERROR
    }

    public record DebugEvent(
            long sequence,
            long elapsedNanos,
            String thread,
            Level level,
            String category,
            String message) {
        public String displayLine() {
            return String.format(
                    Locale.ROOT,
                    "%06d +%9.3fms %-5s %-22s %s",
                    this.sequence,
                    this.elapsedNanos / 1_000_000.0,
                    this.level,
                    '[' + this.category + ']',
                    this.message);
        }

        public String fileLine() {
            return displayLine() + " [" + this.thread + ']';
        }
    }

    public record StatusSnapshot(
            double fps,
            double ticksPerSecond,
            double averageFrameMs,
            double maximumFrameMs,
            double averageTickMs,
            long mouseMoves,
            double mouseDistance,
            long relevantPackets,
            long hudHooks,
            long staleChecks,
            long issues,
            int selectedSlot,
            String screen,
            boolean focused) {
        private static StatusSnapshot empty() {
            return new StatusSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, "starting", false);
        }

        public String compact() {
            return String.format(
                    Locale.ROOT,
                    "fps=%.1f avg=%.2fms max=%.2fms ticks=%.1f/%.2fms mouse=%d/%.1fpx packets=%d hudHooks=%d staleChecks=%d slot=%s screen=%s focus=%s issues=%d",
                    this.fps,
                    this.averageFrameMs,
                    this.maximumFrameMs,
                    this.ticksPerSecond,
                    this.averageTickMs,
                    this.mouseMoves,
                    this.mouseDistance,
                    this.relevantPackets,
                    this.hudHooks,
                    this.staleChecks,
                    slot(this.selectedSlot),
                    this.screen,
                    this.focused,
                    this.issues);
        }
    }

    private static final class HotbarBurst {
        private final long eventSequence;
        private final long tick;
        private final long startedNanos;
        private final int selectedBefore;
        private final List<Integer> slots = new ArrayList<>();
        private final List<String> consumedMappings = new ArrayList<>();
        private int expectedVanillaSlot = -1;
        private boolean distinct;
        private long lastInputNanos;
        private int lastPreviewedSlot = -1;
        private long lastPreviewNanos;

        private HotbarBurst(long eventSequence, long tick, long startedNanos, int selectedBefore) {
            this.eventSequence = eventSequence;
            this.tick = tick;
            this.startedNanos = startedNanos;
            this.lastInputNanos = startedNanos;
            this.selectedBefore = selectedBefore;
        }

        private String describe() {
            return "burst#" + this.eventSequence
                    + " tick=" + this.tick
                    + " before=" + slot(this.selectedBefore)
                    + " inputs=" + this.slots.stream().map(DebugCollector::slot).toList()
                    + " expected=" + slot(this.expectedVanillaSlot)
                    + " distinct=" + this.distinct
                    + " consumed=" + this.consumedMappings;
        }
    }
}
