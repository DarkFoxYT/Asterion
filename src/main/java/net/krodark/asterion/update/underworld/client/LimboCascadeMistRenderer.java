package net.krodark.asterion.update.underworld.client;

import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.mixin.RenderTypeFactory;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.*;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Depth-tested local mist veils. No fullscreen pass, texture, or simulated mist particles. */
public final class LimboCascadeMistRenderer {
    public static final int FACE_BUDGET = 192;
    private static final RenderType MIST = RenderTypeFactory.create("asterion/limbo_cascade_mist",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET,RenderPipelines.FOG_SNIPPET)
                    .withLocation(Asterion.id("pipeline/limbo_cascade_mist"))
                    .withVertexShader(Asterion.id("core/limbo_cascade_mist"))
                    .withFragmentShader(Asterion.id("core/limbo_cascade_mist"))
                    //? if >=26.2 {
                    /*.withVertexBinding(0,DefaultVertexFormat.ENTITY).withPrimitiveTopology(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                    .withBindGroupLayout(com.mojang.blaze3d.pipeline.BindGroupLayout.builder().withUniform("Fog",com.mojang.blaze3d.shaders.UniformType.UNIFORM_BUFFER).build())
                    *///?} else {
                    .withVertexFormat(DefaultVertexFormat.ENTITY,VertexFormat.Mode.QUADS)
                    //?}
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,false))
                    .withCull(false).build()).createRenderSetup());
    private LimboCascadeMistRenderer() { }
    static int draw(List<LimboCascadeMesh.Face> faces,MultiBufferSource buffers,PoseStack poses,
                    Vec3 camera,double ticks,int remaining) {
        if (faces.isEmpty() || remaining<=0) return remaining;
        var out=buffers.getBuffer(MIST);
        long time=(long)ticks;
        int fraction=(int)((ticks-time)*255);
        int index=0;
        for (var face : faces) {
            if(index++%3!=0)continue;
            double range=camera.distanceToSqr((face.x0()+face.x1())*.5,face.lower()+2,(face.z0()+face.z1())*.5);
            if (range>96*96) continue;
            if (remaining<=0) break;
            int opacity=(int)Math.round(255*Math.clamp((96-Math.sqrt(range))/32,0,1));
            for(int shell=0;shell<2 && remaining>0;shell++,remaining--)
            for(int corner=0;corner<4;corner++) {
                boolean right=corner==1||corner==2,top=corner>=2;
                double tangentX=-face.nz(),tangentZ=face.nx();
                double x=(face.x0()+face.x1())*.5+tangentX*(right?2.25:-2.25)+face.nx()*(2.6+shell*3);
                double z=(face.z0()+face.z1())*.5+tangentZ*(right?2.25:-2.25)+face.nz()*(2.6+shell*3);
                double y=face.lower()+.65+(top?8-shell*2:0);
                out.addVertex(poses.last(),(float)(x-camera.x),(float)(y-camera.y),(float)(z-camera.z))
                        .setUv((float)x,(float)z).setUv1(right?255:0,0)
                        .setUv2((int)(time&65535),(int)((time>>>16)&65535))
                        .setColor(top?255:0,fraction,(int)(opacity*(shell==0?1:.75)),255)
                        .setNormal(poses.last(),face.nx(),0,face.nz());
            }
        }
        return Math.max(0,remaining);
    }
    static void finish(MultiBufferSource.BufferSource buffers) { buffers.endBatch(MIST); }
}
