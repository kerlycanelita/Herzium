package dev.zymekoh.herzium.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Vanilla's "tell the server which slot is held" step. It sends a packet only
 * when the slot changed since the last one, exactly as before a use or attack.
 */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeInvoker {
    @Invoker("ensureHasSentCarriedItem")
    void herzium$ensureHasSentCarriedItem();
}
