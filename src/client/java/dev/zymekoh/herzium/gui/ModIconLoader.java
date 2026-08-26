package dev.zymekoh.herzium.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zymekoh.herzium.Herzium;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Draws another mod's own icon, and only its own.
 *
 * <p>Nothing is fetched from anywhere. The icon comes out of the jar the player
 * already installed, at the path that mod declares in its own
 * {@code fabric.mod.json}. If the mod is not installed, or ships no icon, or
 * ships one that cannot be decoded, there is simply no icon and the caller
 * draws its row without one.</p>
 *
 * <p>Textures are registered once and kept for the process. This screen is
 * shown once at start-up and lists at most a handful of mods, so there is
 * nothing here worth reclaiming later.</p>
 */
public final class ModIconLoader {
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static final int PREFERRED_SIZE = 64;

    private ModIconLoader() {
    }

    /**
     * Returns a texture for the installed mod's icon, or {@code null}.
     *
     * <p>Must be called on the render thread: it registers a texture.</p>
     */
    public static Identifier iconFor(String modId) {
        if (CACHE.containsKey(modId)) {
            return CACHE.get(modId);
        }

        Identifier texture = load(modId);
        CACHE.put(modId, texture);
        return texture;
    }

    private static Identifier load(String modId) {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty()) {
            return null;
        }

        Optional<String> iconPath = container.get().getMetadata().getIconPath(PREFERRED_SIZE);
        if (iconPath.isEmpty()) {
            return null;
        }

        Optional<Path> file = container.get().findPath(iconPath.get());
        if (file.isEmpty()) {
            return null;
        }

        try (InputStream stream = Files.newInputStream(file.get())) {
            NativeImage image = NativeImage.read(stream);
            Identifier id = Identifier.fromNamespaceAndPath(
                    "herzium",
                    "mod_icon/" + modId.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_"));
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(id, new DynamicTexture(() -> "Herzium mod icon: " + modId, image));
            return id;
        } catch (IOException | RuntimeException failure) {
            // A mod shipping an icon Minecraft cannot decode is that mod's
            // business, not a reason to fail the advisory it appears on.
            Herzium.LOGGER.debug("Could not read the installed icon for {}.", modId, failure);
            return null;
        }
    }
}
