package dev.zymekoh.herzium.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.zymekoh.herzium.Herzium;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.fabricmc.loader.api.FabricLoader;

public final class HerziumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("herzium.json");
    private static volatile HerziumConfig instance;
    private static volatile CompletableFuture<HerziumConfig> pendingLoad;

    private boolean immediateHotbarSelection = true;
    private boolean startupWarningAcknowledged;

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

    public boolean immediateHotbarSelection() {
        return this.immediateHotbarSelection;
    }

    public void setImmediateHotbarSelection(boolean immediateHotbarSelection) {
        this.immediateHotbarSelection = immediateHotbarSelection;
        this.save();
    }

    public boolean startupWarningAcknowledged() {
        return this.startupWarningAcknowledged;
    }

    public void acknowledgeStartupWarning() {
        if (this.startupWarningAcknowledged) {
            return;
        }

        this.startupWarningAcknowledged = true;
        this.save();
    }

    public synchronized void save() {
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
