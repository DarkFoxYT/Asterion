package net.krodark.asterion.update.underworld.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.mixin.RenderTypeFactory;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;

/** One batched, opaque water curtain pass; motion and spray wisps stay on the GPU. */
public final class LimboCascadeRenderer {
    private static final RenderType CURTAIN = RenderTypeFactory.create("asterion/limbo_cascade",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET, RenderPipelines.FOG_SNIPPET)
                    .withLocation(Asterion.id("pipeline/limbo_cascade"))
                    .withVertexShader(Asterion.id("core/limbo_cascade"))
                    .withFragmentShader(Asterion.id("core/limbo_cascade"))
                    //? if >=26.2 {
                    /*.withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                    .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("Fog", com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER).withSampler("Sampler0").build())
                    *///?} else {
                    .withVertexFormat(DefaultVertexFormat.ENTITY, VertexFormat.Mode.QUADS).withSampler("Sampler0")
                    //?}
                    .withCull(false).withDepthStencilState(DepthStencilState.DEFAULT).build())
                    .withTexture("Sampler0", net.minecraft.resources.Identifier.withDefaultNamespace("textures/block/water_still.png"))
                    .createRenderSetup());
    private LimboCascadeRenderer() { }
    static void draw(List<LimboCascadeMesh.Face> faces, MultiBufferSource buffers,
                     PoseStack poses, Vec3 camera, double ticks) {
        if (faces.isEmpty()) return;
        var out = buffers.getBuffer(CURTAIN);
        var pose = poses.last();
        long time = (long)ticks;
        int fraction = (int)((ticks - time) * 255);
        for (var face : faces) {
            // Fixed subdivisions preserve silhouette; no wave/terrain lookup on the render thread.
            for (int band = 0; band < 6; band++) for (int corner = 0; corner < 4; corner++) {
                boolean right = corner == 1 || corner == 2;
                double t = (band + (corner >= 2 ? 1 : 0)) / 6.0;
                double x = (right ? face.x1() : face.x0()) + face.nx() * .025;
                double z = (right ? face.z1() : face.z0()) + face.nz() * .025;
                double y = face.upper() + 8.0 / 9.0 - (face.upper() - face.lower()) * t;
                out.addVertex(pose, (float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
                        .setColor((int)Math.round(t * 255), fraction, 255, 255)
                        .setUv((float)x, (float)z).setUv1(face.upper(), face.lower())
                        .setUv2((int)(time & 65535), (int)((time >>> 16) & 65535))
                        .setNormal(pose, face.nx(), 0, face.nz());
            }
        }
    }
}
