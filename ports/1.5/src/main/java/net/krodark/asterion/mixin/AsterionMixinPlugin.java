package net.krodark.asterion.mixin;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class AsterionMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.equals("net.krodark.asterion.mixin.PortSpriteInterpolationMixin")) return true;
        try {
            // Inspect bytecode without loading Sodium or a Minecraft target during
            // mixin preparation. Sodium replaces the vanilla interpolation/upload
            // method, so its implementation must own this optimization entirely.
            MixinService.getService().getBytecodeProvider().getClassNode(
                    "net.caffeinemc.mods.sodium.mixin.features.textures.animations.upload.SpriteContentsInterpolationMixin");
            return false;
        } catch (ClassNotFoundException | IOException absent) {
            return true;
        }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
