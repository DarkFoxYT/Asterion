package net.krodark.asterion.update.underworld.client;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.function.BiConsumer;

/** The spinneret endpoint uses the fully posed bone, including torso IK and scale. */
public final class SpiderSilkLayer extends GeoRenderLayer<LimboSpiderEntity,Void,EntityRenderState> {
    private record Thread(int spider,Vec3 anchor) { }
    private static final DataTicket<Thread> SILK=DataTickets.create("asterion_spider_live_silk",Thread.class);
    public SpiderSilkLayer(LimboSpiderRenderer renderer) { super(renderer); }
    @Override public void addRenderData(LimboSpiderEntity spider,Void unused,EntityRenderState state,float partial) {
        if(spider.threadAnchor()!=null)state.addGeckolibData(SILK,new Thread(spider.getId(),spider.threadAnchor()));
    }
    @Override public void addPerBoneRender(RenderPassInfo<EntityRenderState> pass,BiConsumer<GeoBone,PerBoneRender<EntityRenderState>> consumer) {
        Thread silk=pass.renderState().getOrDefaultGeckolibData(SILK,(Thread)null);
        if(silk==null || !pass.willRender())return;
        pass.model().getBone("webmaker").ifPresent(bone->consumer.accept(bone,(posed,ignored,tasks)->{
            Vector3f p=posed.poseStack().last().pose().transformPosition(new Vector3f());
            Vec3 from=new Vec3(p.x,p.y,p.z),to=silk.anchor().subtract(posed.cameraState().pos);
            if(from.distanceToSqr(to)>40*40)return;
            LimboWebWorldRenderer.liveThread(silk.spider(),from.add(posed.cameraState().pos),silk.anchor());
            tasks.submitCustomGeometry(new PoseStack(),RenderTypes.entityTranslucent(Asterion.id("textures/entity/limbo_web_white.png"),false),
                    (pose,out)->{
                        Vec3 a=from;
                        double sag=Math.min(.25,from.distanceTo(to)*.02);
                        for(int i=1;i<=16;i++) {
                            double t=i/16.0;
                            Vec3 b=from.lerp(to,t).add(0,-4*sag*t*(1-t),0);
                            LimboWebWorldRenderer.strand(pose,out,a,b,.016,190,posed.packedLight());a=b;
                        }
                    });
        }));
    }
}
