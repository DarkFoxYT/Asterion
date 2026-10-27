package net.krodark.asterion.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.function.Function;

/** Keep the maze's authored atmosphere while Iris manages normal dimension transitions. */
@Pseudo
@Mixin(targets = {"net.irisshaders.iris.pipeline.PipelineManager",
        "net.coderbot.iris.pipeline.PipelineManager"}, remap = false)
public abstract class IrisLabyrinthPipelineMixin {
    @Unique private Boolean asterion$previousMaze;
    @Shadow public abstract void destroyPipeline();

    @Inject(method = "preparePipeline", at = @At("HEAD"))
    private void asterion$refreshWorldSettings(@Coerce Object dimension, CallbackInfoReturnable<Object> callback) {
        boolean maze = "asterion:labyrinth".equals(dimension.toString());
        // Vanilla and shader pipelines use different Sodium vertex formats. Rebuild
        // across the boundary so Iris reapplies settings and reloads chunk shaders.
        if (asterion$previousMaze != null && asterion$previousMaze != maze) destroyPipeline();
        asterion$previousMaze = maze;
    }

    @WrapOperation(method = "preparePipeline", at = @At(value = "INVOKE", target =
            "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object asterion$authoredDimension(Function<?, ?> factory, Object dimension, Operation<Object> original) {
        if (!"asterion:labyrinth".equals(dimension.toString())) return original.call(factory, dimension);
        // Optional dependency: construct Iris/Oculus's own shader-disabled pipeline
        // once per dimension. Its manager retains responsibility for caches/reloads.
        String prefix = factory.getClass().getName().startsWith("net.coderbot.")
                ? "net.coderbot.iris" : "net.irisshaders.iris";
        try {
            return Class.forName(prefix + ".pipeline.VanillaRenderingPipeline").getConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot isolate Asterion's dimension from the shader pack", failure);
        }
    }
}
