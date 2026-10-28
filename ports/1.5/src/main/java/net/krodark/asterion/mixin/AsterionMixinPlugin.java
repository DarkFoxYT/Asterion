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
        if (targetClassName.startsWith("net.irisshaders.") || targetClassName.startsWith("net.coderbot.iris.")
                || targetClassName.startsWith("net.optifine.") || targetClassName.startsWith("net.caffeinemc.")
                || targetClassName.startsWith("me.jellysquid.mods.sodium.")) {
            // Optional integrations must not ask Mixin to resolve absent mods.
            try (var resource = MixinService.getService().getResourceAsStream(targetClassName.replace('.', '/') + ".class")) {
                if (resource == null) return false;
            } catch (IOException absent) { return false; }
        }
        if (!mixinClassName.equals("net.krodark.asterion.mixin.PortSpriteInterpolationMixin")) return true;
        // These renderers replace vanilla's interpolation/upload method. Let the
        // installed renderer own it; never inject into another mixin's overwrite.
        // Inspect bytecode without initializing renderer or Minecraft classes.
        for (String rendererMixin : List.of(
                "net.caffeinemc.mods.sodium.mixin.features.textures.animations.upload.SpriteContentsInterpolationMixin",
                "org.embeddedt.embeddium.impl.mixin.features.textures.animations.upload.SpriteContentsInterpolationMixin")) {
            try {
                MixinService.getService().getBytecodeProvider().getClassNode(rendererMixin);
                return false;
            } catch (ClassNotFoundException | IOException absent) {
                // Try the next optional renderer.
            }
        }
        return true;
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    private static boolean present(String name) {
        try (var resource = MixinService.getService().getResourceAsStream(name.replace('.', '/') + ".class")) {
            return resource != null;
        } catch (IOException absent) { return false; }
    }
    @Override public List<String> getMixins() {
        var result = new java.util.ArrayList<String>();
        if (present("net.irisshaders.iris.pipeline.PipelineManager") || present("net.coderbot.iris.pipeline.PipelineManager")) result.add(present("net.krodark.asterion.mixin.LegacyIrisLabyrinthPipelineMixin")
                ? "LegacyIrisLabyrinthPipelineMixin" : "IrisLabyrinthPipelineMixin");
        if (present("net.optifine.Config")) result.add("OptifineLabyrinthShadersMixin");
        return result;
    }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) { }
}
