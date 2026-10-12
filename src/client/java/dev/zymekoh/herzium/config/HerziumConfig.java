package dev.zymekoh.herzium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.zymekoh.herzium.Herzium;
import dev.zymekoh.herzium.input.HotbarOrder;
import dev.zymekoh.herzium.input.HotbarOrderController;
import dev.zymekoh.herzium.input.HotbarOrderPolicy;
import dev.zymekoh.herzium.input.ImmediateHotbarInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Herzium's persisted settings.
 *
 * <p>Every feature flag here is read <em>at the injection point</em>, never
 * when the mixin is applied, so toggling one takes effect on the next frame
 * without restarting the game. That is deliberate: a mod whose only answer to
 * an incompatibility is "uninstall it" is not a good neighbour, and a flag that
 * needs a restart is barely better when the thing you are debugging is a
 * conflict with another mod.</p>
 *
 * <p>Flags that only affect Herzium's own behaviour default to {@code true}
 * <em>in the field initialiser</em>. Gson leaves absent keys at their Java
 * default, so a {@code herzium.json} written by an older version keeps those
 * features enabled instead of silently turning them off.</p>
 */
public final class HerziumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("herzium.json");
    private static volatile HerziumConfig instance;
    private static volatile CompletableFuture<HerziumConfig> pendingLoad;

    /**
     * One thread, so writes stay in the order they were requested and never
     * happen while the render thread is waiting on them. Toggling an option in
     * the config screen used to block the frame on file creation, a serialise,
     * a write and an atomic move.
     *
     * <p>Daemon, so a pending write cannot hold the game open on quit. That is
     * safe here because {@link #save()} writes to a temporary file and only
     * then moves it into place: a write cut short by the JVM exiting leaves the
     * previous config intact and at worst an orphan {@code .tmp} beside it.</p>
     */
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Herzium config writer");
        thread.setDaemon(true);
        return thread;
    });

    private boolean startupWarningAcknowledged;

    /**
     * The order a fresh install starts on.
     *
     * <p>This is {@link HotbarOrder#HERZIUM}, not Vanilla, which means a new
     * install changes the <em>real</em> selected slot from its first session:
     * among slots with pending clicks the last pressed binding wins rather than
     * the highest slot. That is server-observable, so the advisory shown on
     * first launch is the only notice a player gets before it applies, and the
     * setting is one click away in Mod Menu.</p>
     *
     * <p>Gson leaves absent keys at their Java default, so a {@code
     * herzium.json} written before this field existed also lands here. Every
     * save writes the key, and the first save happens when the advisory is
     * acknowledged, so in practice that only affects configs from versions
     * older than the setting itself.</p>
     */
    private volatile HotbarOrder hotbarOrder = HotbarOrder.HERZIUM;

    /**
     * The core of the Herzium order. None of these is a switch any more, and none is saved: with
     * any of the first three off, a burst sent the wrong item or an extra click, and every one of
     * them only ever makes a click wait one tick. They stay fields so the laboratory can turn one
     * off and measure what it does.
     *
     * <ul>
     *   <li>split bursts: a click waits for the key pressed before it, one key per tick;</li>
     *   <li>offhand sync: the server learns the slot before an offhand swap or a drop;</li>
     *   <li>hold guard: a button still held does not use the old item while its click waits;</li>
     *   <li>repeat filter: a hotbar key the keyboard repeats is not a new press;</li>
     *   <li>pair slips: a click pressed a moment before its key goes with that key.</li>
     * </ul>
     */
    private transient volatile boolean splitBursts = true;
    private transient volatile boolean offhandSync = true;
    private transient volatile boolean holdGuard = true;
    private transient volatile boolean repeatFilter = true;
    private transient volatile boolean pairSlips = true;

    /**
     * Out of the core, off: each cost a tick or an alert. Strict attacks sent "attack, key, use" on
     * two ticks where Vanilla takes one; one click per tick drew AirLiquidPlace with latency
     * (AUDIT-1.12.0); mouse-like keys dropped the repeats a held use key spams with.
     */
    private transient volatile boolean strictActionOrder = false;
    private transient volatile boolean oneClickPerTick = false;
    private transient volatile boolean mouseLikeKeys = false;

    /** 1.12 feel: what is drawn, never what is sent. */
    private volatile boolean hotbarPreview = true;
    private volatile boolean previewRecovery = true;
    private volatile boolean swapPreview = true;
    private volatile boolean combatPreview = true;
    private volatile boolean slotPop = true;
    private volatile boolean noEquipDip = true;
    private volatile boolean smoothIndicator = true;
    private volatile boolean responseMeter = false;
    private volatile boolean shapeMonitor = true;

    /** Every switch of the settings screen: what is drawn and what is measured. */
    public enum Feature {
        PREVIEW, PREVIEW_RECOVERY, SWAP_PREVIEW, COMBAT_PREVIEW, SLOT_POP, NO_EQUIP_DIP, SMOOTH_INDICATOR,
        RESPONSE_METER, SHAPE_MONITOR;

        public String key() {
            return "herzium.feature." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public boolean on(Feature feature) {
        return switch (feature) {
            case PREVIEW -> this.hotbarPreview;
            case PREVIEW_RECOVERY -> this.previewRecovery;
            case SWAP_PREVIEW -> this.swapPreview;
            case COMBAT_PREVIEW -> this.combatPreview;
            case SLOT_POP -> this.slotPop;
            case NO_EQUIP_DIP -> this.noEquipDip;
            case SMOOTH_INDICATOR -> this.smoothIndicator;
            case RESPONSE_METER -> this.responseMeter;
            case SHAPE_MONITOR -> this.shapeMonitor;
        };
    }

    public void toggle(Feature feature) {
        boolean value = !on(feature);
        switch (feature) {
            case PREVIEW -> this.hotbarPreview = value;
            case PREVIEW_RECOVERY -> this.previewRecovery = value;
            case SWAP_PREVIEW -> this.swapPreview = value;
            case COMBAT_PREVIEW -> this.combatPreview = value;
            case SLOT_POP -> this.slotPop = value;
            case NO_EQUIP_DIP -> this.noEquipDip = value;
            case SMOOTH_INDICATOR -> this.smoothIndicator = value;
            case RESPONSE_METER -> this.responseMeter = value;
            case SHAPE_MONITOR -> this.shapeMonitor = value;
        }
        changed();
    }

    public static HerziumConfig get() {
        HerziumConfig current = instance;
        if (current != null) {
            return current;
        }

        CompletableFuture<HerziumConfig> load;
        synchronized (HerziumConfig.class) {
            if (instance != null) {
                return instance;
            }

            load = pendingLoad;
            if (load == null) {
                instance = read();
                return instance;
            }
        }

        HerziumConfig loaded;
        try {
            loaded = load.join();
        } catch (CompletionException exception) {
            Herzium.LOGGER.warn("Could not finish loading {}; defaults will be used.", CONFIG_PATH, exception);
            loaded = new HerziumConfig();
        }
        synchronized (HerziumConfig.class) {
            if (instance == null) {
                instance = loaded;
            }
            if (pendingLoad == load) {
                pendingLoad = null;
            }
            return instance;
        }
    }

    /** Starts the tiny config read without holding up Fabric's client entrypoint. */
    public static synchronized void loadAsync() {
        if (instance != null || pendingLoad != null) {
            return;
        }

        pendingLoad = CompletableFuture.supplyAsync(HerziumConfig::read)
                .exceptionally(exception -> {
                    Herzium.LOGGER.warn("Could not load {}; defaults will be used.", CONFIG_PATH, exception);
                    return new HerziumConfig();
                });
    }

    private static HerziumConfig read() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return new HerziumConfig();
        }

        try {
            HerziumConfig loaded = GSON.fromJson(
                    Files.readString(CONFIG_PATH, StandardCharsets.UTF_8),
                    HerziumConfig.class);
            return loaded != null ? loaded : new HerziumConfig();
        } catch (IOException | JsonParseException exception) {
            Herzium.LOGGER.warn("Could not read {}; defaults will be used.", CONFIG_PATH, exception);
            return new HerziumConfig();
        }
    }

    public boolean startupWarningAcknowledged() {
        return this.startupWarningAcknowledged;
    }

    public HotbarOrder hotbarOrder() {
        // A config holding an unreadable order is a config with no answer, and
        // that is the same question a fresh install asks; it gets the same one.
        return this.hotbarOrder == null ? HotbarOrder.HERZIUM : this.hotbarOrder;
    }

    /** The burst options of the Herzium order, read once per input pass. */
    public HotbarOrderPolicy.Options burstOptions() {
        return new HotbarOrderPolicy.Options(this.splitBursts, this.strictActionOrder, this.offhandSync,
                this.oneClickPerTick, this.repeatFilter, this.mouseLikeKeys);
    }

    public boolean splitBursts() { return this.splitBursts; }

    public boolean pairSlips() { return this.pairSlips; }

    public boolean holdGuard() { return this.holdGuard; }

    public boolean strictActionOrder() { return this.strictActionOrder; }

    public boolean offhandSync() { return this.offhandSync; }

    private void changed() {
        HotbarOrderController.reset();
        ImmediateHotbarInput.clearPreview();
        this.save();
    }

    public void cycleHotbarOrder() {
        setHotbarOrder(hotbarOrder().next());
    }

    public void setHotbarOrder(HotbarOrder order) {
        this.hotbarOrder = order;
        HotbarOrderController.reset();
        ImmediateHotbarInput.clearPreview();
        this.save();
    }

    public void acknowledgeStartupWarning() {
        if (this.startupWarningAcknowledged) {
            return;
        }

        this.startupWarningAcknowledged = true;
        this.save();
    }

    /** Queues a write; returns immediately so the caller's frame is not held. */
    public void save() {
        SAVE_EXECUTOR.execute(this::writeToDisk);
    }

    private synchronized void writeToDisk() {
        Path temporaryPath = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(
                    temporaryPath,
                    GSON.toJson(this) + System.lineSeparator(),
                    StandardCharsets.UTF_8);

            try {
                Files.move(
                        temporaryPath,
                        CONFIG_PATH,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryPath, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Herzium.LOGGER.warn("Could not save {}.", CONFIG_PATH, exception);

            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException cleanupException) {
                Herzium.LOGGER.debug("Could not remove temporary config {}.", temporaryPath, cleanupException);
            }
        }
    }
}
