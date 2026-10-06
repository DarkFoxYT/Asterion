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
            double range=camera.distanceToSqr((face.x0()+face.x1())*.5,(face.upper()+face.lower())*.5,(face.z0()+face.z1())*.5);
            if(range>544*544)continue;
            int bands=range<96*96?12:range<256*256?6:3,shells=range<64*64?2:1;
            // Denser at the rolled lip; lower water stretches into a curved, thick sheet.
            for(int shell=0;shell<shells;shell++)
            for (int band = 0; band < bands; band++) for (int corner = 0; corner < 4; corner++) {
                boolean right = corner == 1 || corner == 2;
                double u=(band+(corner>=2?1:0))/(double)bands;
                double t=u*u;
                // Join the actual block surface at the top, then ease into the smooth ring.
                double lip=Math.clamp(t/.15,0,1);lip=1-lip*lip*(3-2*lip);
                double x = (right ? face.x1()+face.lipX1()*lip : face.x0()+face.lipX0()*lip) + face.nx() * .025;
                double z = (right ? face.z1()+face.lipZ1()*lip : face.z0()+face.lipZ0()*lip) + face.nz() * .025;
                double y = face.upper() + 8.0 / 9.0 - (face.upper() - face.lower()) * t;
                out.addVertex(pose, (float)(x - camera.x), (float)(y - camera.y), (float)(z - camera.z))
                        .setColor((int)Math.round(t * 255), fraction, shell*255, 255)
                        .setUv((float)x, (float)z).setUv1(face.upper(), face.lower())
                        .setUv2((int)(time & 65535), (int)((time >>> 16) & 65535))
                        .setNormal(pose, face.nx(), 0, face.nz());
            }
        }
    }
}
