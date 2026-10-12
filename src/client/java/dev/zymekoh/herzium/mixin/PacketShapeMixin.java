package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.input.ShapeMonitor;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads the packets this client sends, for the tick shape monitor. It changes none of them. */
@Mixin(value = ClientCommonPacketListenerImpl.class, priority = 100)
abstract class PacketShapeMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), require = 0)
    private void herzium$watchTickShape(Packet<?> packet, CallbackInfo ci) {
        ShapeMonitor.sent(packet);
    }
}
