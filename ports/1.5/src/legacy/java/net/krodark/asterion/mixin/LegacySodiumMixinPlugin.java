package net.krodark.asterion.mixin;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/** Sodium 0.5 still uses the original Sodium package name. */
public final class LegacySodiumMixinPlugin implements IMixinConfigPlugin {
    private final AsterionMixinPlugin shared = new AsterionMixinPlugin();

    @Override public boolean shouldApplyMixin(String target, String mixin) {
        if (!shared.shouldApplyMixin(target, mixin)) return false;
        if (!mixin.endsWith(".PortSpriteInterpolationMixin")) return true;
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(
                    "me.jellysquid.mods.sodium.mixin.features.textures.animations.upload.SpriteContentsInterpolationMixin");
            return false;
        } catch (ClassNotFoundException | IOException absent) { return true; }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> targets, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
