package dev.zymekoh.herzium.mixin;

import dev.zymekoh.herzium.diagnostics.CoreDiagnostics;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Avoids resolving optional compatibility targets when their mod is absent.
 */
public final class HerziumMixinPlugin implements IMixinConfigPlugin {
    private static final String EXORDIUM_MIXIN =
            "dev.zymekoh.herzium.mixin.compat.ExordiumBufferInstanceMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !EXORDIUM_MIXIN.equals(mixinClassName)
                || FabricLoader.getInstance().isModLoaded("exordium");
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo) {
        CoreDiagnostics.recordMixinApplied(mixinClassName);
    }
}
