package dev.zymekoh.herzium.debug.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observes Vanilla's logical input queue without consuming or modifying it. */
@Mixin(value = KeyMapping.class, priority = 3000)
abstract class KeyMappingTraceMixin {
    @Inject(method = "click", at = @At("TAIL"))
    private static void herziumDebug$onClick(InputConstants.Key key, CallbackInfo ci) {
        DebugCollector.onKeyClick(key);
    }

    @Inject(method = "set", at = @At("HEAD"))
    private static void herziumDebug$onSet(InputConstants.Key key, boolean state, CallbackInfo ci) {
        DebugCollector.onKeyState(key, state);
    }

    @Inject(method = "consumeClick", at = @At("RETURN"))
    private void herziumDebug$onConsume(CallbackInfoReturnable<Boolean> cir) {
        DebugCollector.onKeyConsumed(
                (KeyMapping) (Object) this,
                cir.getReturnValueZ(),
                ((KeyMappingClickCountAccessor) this).herziumDebug$getPendingClickCount());
    }
}
