package net.krodark.asterion.update.underworld.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.WebCutPayload;
import net.krodark.asterion.update.underworld.WebPatch;
import net.krodark.asterion.update.underworld.WebPatchGenerator;
import net.krodark.asterion.update.underworld.WebPlayerShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

/** World renderer and local high-frequency PBD simulation; there is deliberately no web entity. */
public final class LimboWebWorldRenderer {
    // A neutral white sheet keeps the strands independent of Minecraft's chunky cobweb sprite.
    private static final Identifier SILK=Asterion.id("textures/entity/limbo_web_white.png");
    private static final Map<Long,WebPhysicsGraph> GRAPHS=new HashMap<>(); private static final Map<Long,java.util.BitSet> CUT=new HashMap<>(); private static boolean attack;
    private LimboWebWorldRenderer(){}
    /** Foot targets use the same deformed links that are drawn, excluding cuts. */
    public static Vec3 spiderContact(Vec3 foot, double reach) {
        Vec3 result = null;
        double best = reach * reach;
        double partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (WebPhysicsGraph graph : GRAPHS.values()) {
            if (!graph.bounds.inflate(reach).contains(foot)) continue;
            var cuts = CUT.get(graph.patch.key());
            for (WebPhysicsGraph.Link link : graph.links) {
                if (cuts != null && cuts.get(link.index())) continue;
                Vec3 point = net.krodark.asterion.update.underworld.LimboWebSystem.nearest(
                        graph.rendered(link.a(),partial),graph.rendered(link.b(),partial),foot);
                double distance = point.distanceToSqr(foot);
                if (distance < best) { best = distance; result = point; }
            }
        }
        return result;
    }
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(LimboWebWorldRenderer::tick);
        ClientPlayNetworking.registerGlobalReceiver(WebCutPayload.TYPE,(payload,context)->context.client().execute(()->CUT.computeIfAbsent(payload.key(),ignored->new java.util.BitSet()).set(payload.link())));
    }
    private static void tick(Minecraft client){
        if(client.level==null||client.player==null||!client.level.dimension().equals(Asterion.LIMBO_LEVEL)){GRAPHS.clear();CUT.clear();return;}
        Vec3 body=client.player.position().add(0,client.player.getBbHeight()*.48,0);
        var patches=WebPatchGenerator.around(client.level,body,16); java.util.HashSet<Long> live=new java.util.HashSet<>();
        var influences=new ArrayList<WebPhysicsGraph.Influence>();
        for(var part:WebPlayerShape.parts(client.player))
            influences.add(new WebPhysicsGraph.Influence(part.middle(),client.player.getDeltaMovement(),part.radius()+.22));
        for(var entity:client.level.entitiesForRendering()) {
            if(entity==client.player || !(entity instanceof net.minecraft.world.entity.LivingEntity) || entity.distanceToSqr(client.player)>32*32)continue;
            influences.add(new WebPhysicsGraph.Influence(entity.position().add(0,entity.getBbHeight()*.48,0),
                    entity.getDeltaMovement(),Math.max(.55,entity.getBbWidth()*.65)));
        }
        for(WebPatch patch:patches){live.add(patch.key());GRAPHS.computeIfAbsent(patch.key(),ignored->new WebPhysicsGraph(patch)).step(client.level,influences,CUT.computeIfAbsent(patch.key(),ignored->new java.util.BitSet()));}
        GRAPHS.entrySet().removeIf(entry->!live.contains(entry.getKey())
                && !entry.getValue().bounds.inflate(20).contains(body));
        boolean down=client.options.keyAttack.isDown(); if(down&&!attack)cutLookedAt(client); attack=down;
    }
    private static void cutLookedAt(Minecraft client){
        Vec3 eye=client.player.getEyePosition(),end=eye.add(client.player.getLookAngle().scale(client.player.blockInteractionRange()+.75));double best=.16*.16;long key=0;int edge=-1;
        for(WebPhysicsGraph graph:GRAPHS.values())for(WebPhysicsGraph.Link link:graph.links){if(CUT.computeIfAbsent(graph.patch.key(),ignored->new java.util.BitSet()).get(link.index()))continue;double d=distance(eye,end,graph.p.get(link.a()),graph.p.get(link.b()));if(d<best){best=d;key=graph.patch.key();edge=link.index();}}
        if(edge>=0&&ClientPlayNetworking.canSend(WebCutPayload.TYPE)){CUT.computeIfAbsent(key,ignored->new java.util.BitSet()).set(edge);ClientPlayNetworking.send(new WebCutPayload(key,edge));}
    }
    public static void submit(PoseStack poses,LevelRenderState state,SubmitNodeCollector output){
        Minecraft client=Minecraft.getInstance();if(client.level==null||!client.level.dimension().equals(Asterion.LIMBO_LEVEL)||GRAPHS.isEmpty())return;Vec3 camera=state.cameraRenderState.pos;
        double partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        var frustum=state.cameraRenderState.cullFrustum;
        output.submitCustomGeometry(poses,RenderTypes.entityTranslucent(SILK,false),(pose,out)->{for(WebPhysicsGraph graph:GRAPHS.values()){if(frustum!=null&&!frustum.isVisible(graph.bounds))continue;java.util.BitSet cut=CUT.computeIfAbsent(graph.patch.key(),ignored->new java.util.BitSet());int alpha=0x48+(int)((mix(graph.patch.key())>>>56)&0x5f);for(WebPhysicsGraph.Link link:graph.links)if(!cut.get(link.index())){double weight=.012+((mix(graph.patch.key()+link.index())>>>58)&7)*.003;Vec3 a=graph.rendered(link.a(),partial),b=graph.rendered(link.b(),partial);strand(pose,out,a,b,weight,alpha,LevelRenderer.getLightCoords(client.level,BlockPos.containing(a)));}}});
        poses.popPose();
    }
    private static void strand(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,double width,int alpha,int light){
        Vec3 delta=b.subtract(a);
        if(delta.lengthSqr()<1e-8)return;
        Vec3 axis=delta.normalize();
        // A single vertical-facing ribbon, not the two perpendicular quads that
        // made every silk link look like an X or a plus from different angles.
        Vec3 up=new Vec3(0,1,0);
        Vec3 side=up.subtract(axis.scale(axis.dot(up)));
        if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);
        quad(pose,out,a,b,side.normalize().scale(width),alpha,light);
    }
    private static void quad(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 w,int alpha,int light){vertex(pose,out,a.subtract(w),0,0,alpha,light);vertex(pose,out,a.add(w),1,0,alpha,light);vertex(pose,out,b.add(w),1,1,alpha,light);vertex(pose,out,b.subtract(w),0,1,alpha,light);}
    private static void vertex(PoseStack.Pose pose,VertexConsumer out,Vec3 p,float u,float v,int alpha,int light){org.joml.Vector3f q=pose.pose().transformPosition((float)p.x,(float)p.y,(float)p.z,new org.joml.Vector3f());out.addVertex(q.x,q.y,q.z,(alpha<<24)|0x00D4D8D4,u,v,OverlayTexture.NO_OVERLAY,light,0,1,0);}
    private static double distance(Vec3 p1,Vec3 q1,Vec3 p2,Vec3 q2){Vec3 d1=q1.subtract(p1),d2=q2.subtract(p2),r=p1.subtract(p2);double a=d1.dot(d1),e=d2.dot(d2),f=d2.dot(r),s,t;if(a<=1e-8&&e<=1e-8)return p1.distanceToSqr(p2);if(a<=1e-8){s=0;t=Math.clamp(f/e,0,1);}else{double c=d1.dot(r);if(e<=1e-8){t=0;s=Math.clamp(-c/a,0,1);}else{double b=d1.dot(d2),den=a*e-b*b;s=den==0?0:Math.clamp((b*f-c*e)/den,0,1);t=(b*s+f)/e;if(t<0){t=0;s=Math.clamp(-c/a,0,1);}else if(t>1){t=1;s=Math.clamp((b-c)/a,0,1);}}}return p1.add(d1.scale(s)).distanceToSqr(p2.add(d2.scale(t)));}
    private static long mix(long z){z=(z^z>>>30)*0xbf58476d1ce4e5b9L;z=(z^z>>>27)*0x94d049bb133111ebL;return z^z>>>31;}
}
