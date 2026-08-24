package dev.zymekoh.herzium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.zymekoh.herzium.Herzium;
import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
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
    private boolean instantEquip = true;
    private boolean hotbarPreview = true;

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
            CoreDiagnostics.recordConfigReadFailure();
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
                    CoreDiagnostics.recordConfigReadFailure();
                    return new HerziumConfig();
                });
    }

    private static HerziumConfig read() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            CoreDiagnostics.recordConfigHealthy();
            return new HerziumConfig();
        }

        try {
            HerziumConfig loaded = GSON.fromJson(
                    Files.readString(CONFIG_PATH, StandardCharsets.UTF_8),
                    HerziumConfig.class);
            CoreDiagnostics.recordConfigHealthy();
            return loaded != null ? loaded : new HerziumConfig();
        } catch (IOException | JsonParseException exception) {
            Herzium.LOGGER.warn("Could not read {}; defaults will be used.", CONFIG_PATH, exception);
            CoreDiagnostics.recordConfigReadFailure();
            return new HerziumConfig();
        }
    }

    public boolean startupWarningAcknowledged() {
        return this.startupWarningAcknowledged;
    }

    /** Read by {@code ItemInHandRendererMixin} on every rendered frame. */
    public boolean instantEquip() {
        return this.instantEquip;
    }

    /** Read by {@link dev.zymekoh.herzium.input.ImmediateHotbarInput} per input. */
    public boolean hotbarPreview() {
        return this.hotbarPreview;
    }

    public void setInstantEquip(boolean enabled) {
        if (this.instantEquip == enabled) {
            return;
        }

        this.instantEquip = enabled;
        this.save();
    }

    public void setHotbarPreview(boolean enabled) {
        if (this.hotbarPreview == enabled) {
            return;
        }

        this.hotbarPreview = enabled;
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
            CoreDiagnostics.recordConfigHealthy();
        } catch (IOException exception) {
            Herzium.LOGGER.warn("Could not save {}.", CONFIG_PATH, exception);
            CoreDiagnostics.recordConfigWriteFailure();

            try {
                Files.deleteIfExists(temporaryPath);
            } catch (IOException cleanupException) {
                Herzium.LOGGER.debug("Could not remove temporary config {}.", temporaryPath, cleanupException);
            }
        }
    }
}
