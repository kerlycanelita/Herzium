package dev.zymekoh.herzium.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's own use and attack entry points, for the same-tick laboratory order only. */
@Mixin(Minecraft.class)
public interface MinecraftActionInvoker {
    @Invoker("startUseItem")
    void herzium$startUseItem();

    @Invoker("startAttack")
    boolean herzium$startAttack();
}
