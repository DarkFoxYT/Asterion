package net.krodark.asterion.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBuffer;
import net.krodark.asterion.network.PressureButtonHoldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Input and depth-tested arena guidance for pressure buttons. */
public final class PressureButtonClient {
    private static final RenderType MARKER=AsterionEmissiveBuffer.customRenderType("pressure_button_marker",
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Asterion.id("pipeline/pressure_button_marker")).withCull(false).build());
    private static final List<BlockPos> BUTTONS=new ArrayList<>();
    private static int scanDelay;
    private static BlockPos heldTarget;
    private PressureButtonClient() { }

    public static void initialize() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context->render(context));
    }
    public static void tick(Minecraft client) {
        if(client.player==null||client.level==null) { releaseHold(); BUTTONS.clear(); return; }
        BlockPos target=null;
        if(client.options.keyUse.isDown()&&client.hitResult instanceof BlockHitResult hit) {
            var targeted=client.level.getBlockState(hit.getBlockPos());
            if(targeted.is(Asterion.PRESSURE_BUTTON)||targeted.is(Asterion.LAMENTER))
                target=hit.getBlockPos().immutable();
        }
        if(!java.util.Objects.equals(target,heldTarget)&&ClientPlayNetworking.canSend(PressureButtonHoldPayload.TYPE)) {
            if(heldTarget!=null) ClientPlayNetworking.send(new PressureButtonHoldPayload(heldTarget,false));
            heldTarget=target;
            if(heldTarget!=null) ClientPlayNetworking.send(new PressureButtonHoldPayload(heldTarget,true));
        }
        if(!BossEntranceCinematic.hasFinished()) { BUTTONS.clear(); return; }
        if(--scanDelay>0) return;
        scanDelay=40;
        scan(client);
    }
    private static void releaseHold() {
        if(heldTarget!=null&&ClientPlayNetworking.canSend(PressureButtonHoldPayload.TYPE))
            ClientPlayNetworking.send(new PressureButtonHoldPayload(heldTarget,false));
        heldTarget=null;
    }
    private static void scan(Minecraft client) {
        BUTTONS.clear();
        BlockPos center=client.player.blockPosition();
        int radius=36,minY=Math.max(client.level.getMinY(),center.getY()-10),
                maxY=Math.min(client.level.getMaxY(),center.getY()+10);
        for(BlockPos pos:BlockPos.betweenClosed(center.getX()-radius,minY,center.getZ()-radius,
                center.getX()+radius,maxY,center.getZ()+radius)) {
            if(client.level.getBlockState(pos).is(Asterion.PRESSURE_BUTTON)) BUTTONS.add(pos.immutable());
            if(BUTTONS.size()>=12) break;
        }
    }
    private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        Minecraft client=Minecraft.getInstance();
        if(client.player==null||client.level==null||!BossEntranceCinematic.hasFinished()||BUTTONS.isEmpty()) return;
        Vec3 camera=context.levelState().cameraRenderState.pos;
        VertexConsumer out=context.bufferSource().getBuffer(MARKER);
        PoseStack.Pose pose=context.poseStack().last();
        double time=client.level.getGameTime()+client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for(BlockPos pos:BUTTONS) {
            if(!client.level.getBlockState(pos).is(Asterion.PRESSURE_BUTTON)) continue;
            Vec3 button=Vec3.atCenterOf(pos),target=button.add(0,.95,0);
            double distance=client.player.position().distanceTo(button);
            if(distance<=4||distance>48||occluded(client,camera,target,pos)) continue;
            double pulse=.22+(.5+.5*Math.sin(time*.16))*0.07;
            Vec3 horizontal=new Vec3(camera.x-target.x,0,camera.z-target.z);
            if(horizontal.lengthSqr()<1.0e-5) horizontal=new Vec3(0,0,1);
            Vec3 right=new Vec3(-horizontal.z,0,horizontal.x).normalize().scale(pulse);
            Vec3 up=new Vec3(0,pulse,0);
            int alpha=Mth.clamp((int)(185+45*Math.sin(time*.16)),120,230);
            int color=(alpha<<24)|0x35D9FF;
            vertex(out,pose,target.add(up),camera,color); vertex(out,pose,target.add(right),camera,color);
            vertex(out,pose,target.subtract(up),camera,color); vertex(out,pose,target.subtract(right),camera,color);
        }
    }
    private static boolean occluded(Minecraft client,Vec3 from,Vec3 to,BlockPos button) {
        HitResult hit=client.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        return hit.getType()!=HitResult.Type.MISS
                &&(!(hit instanceof BlockHitResult blockHit)||!blockHit.getBlockPos().equals(button));
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 point,Vec3 camera,int color) {
        out.addVertex(pose,(float)(point.x-camera.x),(float)(point.y-camera.y),(float)(point.z-camera.z)).setColor(color);
    }
}
