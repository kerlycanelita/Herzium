package dev.zymekoh.herzium.debug.mixin;

import dev.zymekoh.herzium.debug.DebugCollector;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Samples the final hand state after all render-time mixins have run. */
@Mixin(value = ItemInHandRenderer.class, priority = 100)
abstract class ItemInHandRendererTraceMixin {
    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void herziumDebug$afterHandsRendered(CallbackInfo ci) {
        DebugCollector.onHandsRendered(
                this.mainHandItem,
                this.offHandItem,
                this.mainHandHeight,
                this.oMainHandHeight,
                this.offHandHeight,
                this.oOffHandHeight);
    }
}
