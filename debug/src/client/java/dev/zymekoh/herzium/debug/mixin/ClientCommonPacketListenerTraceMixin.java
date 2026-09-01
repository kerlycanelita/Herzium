package dev.zymekoh.herzium.debug.mixin;

import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes only safe metadata from packets Minecraft has already chosen to send. */
@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 3000)
abstract class ClientCommonPacketListenerTraceMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void herziumDebug$packet(Packet<?> packet, CallbackInfo ci) {
        DebugCollector.onPacketSent(packet);
    }
}
