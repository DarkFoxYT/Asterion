package net.krodark.asterion.update.underworld.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.WebCutPayload;
import net.krodark.asterion.update.underworld.WebPatch;
import net.krodark.asterion.update.underworld.WebPatchGenerator;
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

/** World renderer and local high-frequency PBD simulation; there is deliberately no web entity. */
public final class LimboWebWorldRenderer {
    private static final Identifier SILK=Identifier.withDefaultNamespace("textures/block/cobweb.png");
    private static final Map<Long,WebPhysicsGraph> GRAPHS=new HashMap<>(); private static final Map<Long,java.util.BitSet> CUT=new HashMap<>(); private static boolean attack;
    private LimboWebWorldRenderer(){}
    public static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(LimboWebWorldRenderer::tick);
        ClientPlayNetworking.registerGlobalReceiver(WebCutPayload.TYPE,(payload,context)->context.client().execute(()->CUT.computeIfAbsent(payload.key(),ignored->new java.util.BitSet()).set(payload.link())));
    }
    private static void tick(Minecraft client){
        if(client.level==null||client.player==null||!client.level.dimension().equals(Asterion.LIMBO_LEVEL)){GRAPHS.clear();CUT.clear();return;}
        Vec3 body=client.player.position().add(0,client.player.getBbHeight()*.48,0);
        var patches=WebPatchGenerator.around(client.level,body,9); java.util.HashSet<Long> live=new java.util.HashSet<>();
        for(WebPatch patch:patches){live.add(patch.key());GRAPHS.computeIfAbsent(patch.key(),ignored->new WebPhysicsGraph(patch)).step(body,.94,CUT.computeIfAbsent(patch.key(),ignored->new java.util.BitSet()));}
        GRAPHS.keySet().removeIf(key->!live.contains(key));
        boolean down=client.options.keyAttack.isDown(); if(down&&!attack)cutLookedAt(client); attack=down;
    }
    private static void cutLookedAt(Minecraft client){
        Vec3 eye=client.player.getEyePosition(),end=eye.add(client.player.getLookAngle().scale(client.player.blockInteractionRange()+.75));double best=.16*.16;long key=0;int edge=-1;
        for(WebPhysicsGraph graph:GRAPHS.values())for(WebPhysicsGraph.Link link:graph.links){if(CUT.computeIfAbsent(graph.patch.key(),ignored->new java.util.BitSet()).get(link.index()))continue;double d=distance(eye,end,graph.p.get(link.a()),graph.p.get(link.b()));if(d<best){best=d;key=graph.patch.key();edge=link.index();}}
        if(edge>=0&&ClientPlayNetworking.canSend(WebCutPayload.TYPE)){CUT.computeIfAbsent(key,ignored->new java.util.BitSet()).set(edge);ClientPlayNetworking.send(new WebCutPayload(key,edge));}
    }
    public static void submit(PoseStack poses,LevelRenderState state,SubmitNodeCollector output){
        Minecraft client=Minecraft.getInstance();if(client.level==null||!client.level.dimension().equals(Asterion.LIMBO_LEVEL)||GRAPHS.isEmpty())return;Vec3 camera=state.cameraRenderState.pos;
        poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        output.submitCustomGeometry(poses,RenderTypes.entityTranslucent(SILK,false),(pose,out)->{for(WebPhysicsGraph graph:GRAPHS.values()){java.util.BitSet cut=CUT.computeIfAbsent(graph.patch.key(),ignored->new java.util.BitSet());for(WebPhysicsGraph.Link link:graph.links)if(!cut.get(link.index())){double weight=.016+((mix(graph.patch.key()+link.index())>>>58)&7)*.004;strand(pose,out,graph.p.get(link.a()),graph.p.get(link.b()),weight,LevelRenderer.getLightCoords(client.level,BlockPos.containing(graph.p.get(link.a()))));}anchorFans(pose,out,graph.patch,client.level);}});
        poses.popPose();
    }
    private static void anchorFans(PoseStack.Pose pose,VertexConsumer out,WebPatch patch,net.minecraft.client.multiplayer.ClientLevel level){for(int i=0;i<patch.anchors().size();i++){Vec3 anchor=patch.anchors().get(i),normal=patch.normals().get(i);Vec3 u=normal.cross(Math.abs(normal.y)>.8?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=normal.cross(u).normalize();int light=LevelRenderer.getLightCoords(level,BlockPos.containing(anchor));long random=mix(patch.key()+i*31L);for(int ray=0;ray<7;ray++){double angle=Math.PI*2*(ray/7.0)+((random>>>ray)&7)*.035;double length=.24+((random>>>(ray+12))&15)/15.0*.58;Vec3 tip=anchor.add(u.scale(Math.cos(angle)*length)).add(v.scale(Math.sin(angle)*length)).subtract(normal.scale(.008));Vec3 elbow=anchor.lerp(tip,.52).add(normal.scale(.018+ray*.002));strand(pose,out,anchor,elbow,.010+(ray%3)*.003,light);strand(pose,out,elbow,tip,.007+(ray%2)*.003,light);}}}
    private static void strand(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,double width,int light){Vec3 delta=b.subtract(a);if(delta.lengthSqr()<1e-8)return;Vec3 axis=delta.normalize(),side=axis.cross(Math.abs(axis.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize().scale(width);quad(pose,out,a,b,side,light);quad(pose,out,a,b,axis.cross(side).normalize().scale(width*.78),light);}
    private static void quad(PoseStack.Pose pose,VertexConsumer out,Vec3 a,Vec3 b,Vec3 w,int light){vertex(pose,out,a.subtract(w),0,0,light);vertex(pose,out,a.add(w),1,0,light);vertex(pose,out,b.add(w),1,1,light);vertex(pose,out,b.subtract(w),0,1,light);}
    private static void vertex(PoseStack.Pose pose,VertexConsumer out,Vec3 p,float u,float v,int light){org.joml.Vector3f q=pose.pose().transformPosition((float)p.x,(float)p.y,(float)p.z,new org.joml.Vector3f());out.addVertex(q.x,q.y,q.z,0xA8D4D8D4,u,v,OverlayTexture.NO_OVERLAY,light,0,1,0);}
    private static double distance(Vec3 p1,Vec3 q1,Vec3 p2,Vec3 q2){Vec3 d1=q1.subtract(p1),d2=q2.subtract(p2),r=p1.subtract(p2);double a=d1.dot(d1),e=d2.dot(d2),f=d2.dot(r),s,t;if(a<=1e-8&&e<=1e-8)return p1.distanceToSqr(p2);if(a<=1e-8){s=0;t=Math.clamp(f/e,0,1);}else{double c=d1.dot(r);if(e<=1e-8){t=0;s=Math.clamp(-c/a,0,1);}else{double b=d1.dot(d2),den=a*e-b*b;s=den==0?0:Math.clamp((b*f-c*e)/den,0,1);t=(b*s+f)/e;if(t<0){t=0;s=Math.clamp(-c/a,0,1);}else if(t>1){t=1;s=Math.clamp((b-c)/a,0,1);}}}return p1.add(d1.scale(s)).distanceToSqr(p2.add(d2.scale(t)));}
    private static long mix(long z){z=(z^z>>>30)*0xbf58476d1ce4e5b9L;z=(z^z>>>27)*0x94d049bb133111ebL;return z^z>>>31;}
}
