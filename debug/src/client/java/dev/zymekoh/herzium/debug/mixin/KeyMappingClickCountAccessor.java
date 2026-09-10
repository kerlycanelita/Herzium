package dev.zymekoh.herzium.debug.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access used to prove what remains in Vanilla's click queue. */
@Mixin(KeyMapping.class)
public interface KeyMappingClickCountAccessor {
    @Accessor("clickCount")
    int herziumDebug$getPendingClickCount();
}
