package dev.zymekoh.herzium.debug.mixin;

import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server confirmations relevant to inventory and offhand ghost reports. */
@Mixin(value = ClientPacketListener.class, priority = 3000)
abstract class ClientPacketListenerTraceMixin {
    @Inject(method = "handleBlockChangedAck", at = @At("HEAD"))
    private void herziumDebug$blockAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
        DebugCollector.onBlockChangedAck(packet);
    }

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void herziumDebug$blockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        DebugCollector.onBlockUpdate(packet);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("HEAD"))
    private void herziumDebug$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        DebugCollector.onContainerSlot(packet);
    }

    @Inject(method = "handleContainerContent", at = @At("HEAD"))
    private void herziumDebug$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        DebugCollector.onContainerContent(packet);
    }
}
