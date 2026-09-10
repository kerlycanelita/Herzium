package dev.zymekoh.herzium.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the logical key currently assigned by Vanilla. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
    @Accessor("key")
    InputConstants.Key herzium$getBoundKey();

    /**
     * Read-only view of Vanilla's pending logical-click counter.
     *
     * <p>Herzium uses this only to predict what the next unmodified
     * {@code handleKeybinds} hotbar pass can resolve. It deliberately exposes
     * no setter and never consumes or clears the queue.</p>
     */
    @Accessor("clickCount")
    int herzium$getPendingClickCount();
}
