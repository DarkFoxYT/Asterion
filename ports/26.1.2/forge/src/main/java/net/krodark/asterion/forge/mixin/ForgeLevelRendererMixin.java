package net.krodark.asterion.forge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class ForgeLevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private RenderBuffers renderBuffers;
    @Unique private PoseStack asterion$pose;
    @Unique private LevelRenderContext asterion$context;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void asterion$prepare(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline,
                                  CameraRenderState camera, Matrix4fc modelView, GpuBufferSlice fog,
                                  Vector4f fogColor, boolean sky, ChunkSectionsToRender sections, CallbackInfo ci) {
        asterion$pose = null;
        asterion$context = new LevelRenderContext() {
            @Override public LevelRenderState levelState() { return levelRenderState; }
            @Override public PoseStack poseStack() { return asterion$pose; }
            @Override public MultiBufferSource.BufferSource bufferSource() { return renderBuffers.bufferSource(); }
        };
    }

    @ModifyExpressionValue(method = "lambda$addMainPass$0", at = @At(value = "NEW", target = "Lcom/mojang/blaze3d/vertex/PoseStack;"))
    private PoseStack asterion$recordPose(PoseStack pose) {
        asterion$pose = pose;
        return pose;
    }

    @WrapOperation(method = "lambda$addMainPass$0", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;renderGroup(Lnet/minecraft/client/renderer/chunk/ChunkSectionLayerGroup;Lcom/mojang/blaze3d/textures/GpuSampler;)V", ordinal = 1))
    private void asterion$renderTranslucent(ChunkSectionsToRender sections, ChunkSectionLayerGroup group,
                                            GpuSampler sampler, Operation<Void> original) {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.fire(asterion$context);
        original.call(sections, group, sampler);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.fire(asterion$context);
    }
}
