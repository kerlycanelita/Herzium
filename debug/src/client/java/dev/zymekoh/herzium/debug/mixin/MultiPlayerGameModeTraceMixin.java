package dev.zymekoh.herzium.debug.mixin;

import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Traces Vanilla action entrypoints without invoking, cancelling or repeating them. */
@Mixin(value = MultiPlayerGameMode.class, priority = 3000)
abstract class MultiPlayerGameModeTraceMixin {
    @Inject(method = "startDestroyBlock", at = @At("HEAD"))
    private void herziumDebug$startDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        DebugCollector.onActionCall("startDestroyBlock", "pos=" + pos + "; face=" + direction);
    }

    @Inject(method = "startDestroyBlock", at = @At("RETURN"))
    private void herziumDebug$startDestroyResult(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        DebugCollector.onActionResult("startDestroyBlock", cir.getReturnValueZ());
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
    private void herziumDebug$continueDestroy(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        DebugCollector.onActionCall("continueDestroyBlock", "pos=" + pos + "; face=" + direction);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void herziumDebug$useOn(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        DebugCollector.onActionCall("useItemOn", "hand=" + hand + "; pos=" + hit.getBlockPos() + "; face=" + hit.getDirection());
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void herziumDebug$useOnResult(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        DebugCollector.onActionResult("useItemOn", cir.getReturnValue());
    }

    @Inject(method = "useItem", at = @At("HEAD"))
    private void herziumDebug$use(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        DebugCollector.onActionCall("useItem", "hand=" + hand);
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void herziumDebug$useResult(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        DebugCollector.onActionResult("useItem", cir.getReturnValue());
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void herziumDebug$attack(Player player, Entity target, CallbackInfo ci) {
        DebugCollector.onActionCall("attack", "target=" + target.getType());
    }

    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    private void herziumDebug$container(
            int containerId,
            int slotNum,
            int buttonNum,
            ContainerInput input,
            Player player,
            CallbackInfo ci) {
        DebugCollector.onActionCall("containerInput", "id=" + containerId + "; slot=" + slotNum
                + "; button=" + buttonNum + "; type=" + input);
    }
}
