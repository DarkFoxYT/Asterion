package net.krodark.asterion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.AsterionEmissiveBuffer;
import net.krodark.asterion.network.PressureButtonHoldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.OverlayTexture;
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
    private static final RenderType MARKER=AsterionEmissiveBuffer.renderType(
            Asterion.id("textures/pin/pin_buttonhold.png"));
    private static final List<BlockPos> BUTTONS=new ArrayList<>();
    private static int scanDelay;
    private static BlockPos heldTarget;
    private static int heldTicks;
    private PressureButtonClient() { }

    public static void initialize() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context->render(context));
        HudElementRegistry.addLast(Asterion.id("pressure_button_progress"),PressureButtonClient::renderProgress);
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
            heldTicks=0;
            if(heldTarget!=null) ClientPlayNetworking.send(new PressureButtonHoldPayload(heldTarget,true));
        }
        if(heldTarget!=null&&client.level.getBlockState(heldTarget).is(Asterion.PRESSURE_BUTTON))
            heldTicks=Math.min(40,heldTicks+1);
        else heldTicks=0;
        if(!BossEntranceCinematic.hasFinished()) { BUTTONS.clear(); return; }
        if(--scanDelay>0) return;
        scanDelay=40;
        scan(client);
    }
    private static void releaseHold() {
        if(heldTarget!=null&&ClientPlayNetworking.canSend(PressureButtonHoldPayload.TYPE))
            ClientPlayNetworking.send(new PressureButtonHoldPayload(heldTarget,false));
        heldTarget=null;
        heldTicks=0;
    }
    private static void renderProgress(GuiGraphicsExtractor graphics,net.minecraft.client.DeltaTracker delta) {
        Minecraft client=Minecraft.getInstance();
        if(heldTarget==null||heldTicks<=0||client.player==null||client.level==null
                ||!client.level.getBlockState(heldTarget).is(Asterion.PRESSURE_BUTTON)) return;
        int width=156,height=9;
        int x=(graphics.guiWidth()-width)/2,y=graphics.guiHeight()-70;
        float partial=Mth.clamp(delta.getGameTimeDeltaPartialTick(false),0F,1F);
        float progress=Mth.clamp((heldTicks+partial)/40F,0F,1F);
        graphics.fill(x-2,y-2,x+width+2,y+height+2,0xC0100B08);
        graphics.fill(x,y,x+width,y+height,0xE02A2119);
        graphics.fill(x,y,x+Mth.floor(width*progress),y+height,
                progress>=1F?0xFFE8B743:0xFFB87827);
        graphics.centeredText(client.font,Component.literal(progress>=1F
                        ?"LAMENTER HELD ACTIVE":"HOLD — AWAKEN LAMENTER"),
                graphics.guiWidth()/2,y-12,0xFFFFE7BE);
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
            int color=(alpha<<24)|0xFFFFFF;
            Vec3 normal=camera.subtract(target).normalize();
            vertex(out,pose,target.add(up),camera,normal,color,0,0);
            vertex(out,pose,target.add(right),camera,normal,color,1,0);
            vertex(out,pose,target.subtract(up),camera,normal,color,1,1);
            vertex(out,pose,target.subtract(right),camera,normal,color,0,1);
        }
    }
    private static boolean occluded(Minecraft client,Vec3 from,Vec3 to,BlockPos button) {
        HitResult hit=client.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
        return hit.getType()!=HitResult.Type.MISS
                &&(!(hit instanceof BlockHitResult blockHit)||!blockHit.getBlockPos().equals(button));
    }
    private static void vertex(VertexConsumer out,PoseStack.Pose pose,Vec3 point,Vec3 camera,Vec3 normal,
                               int color,float u,float v) {
        out.addVertex(pose,(float)(point.x-camera.x),(float)(point.y-camera.y),(float)(point.z-camera.z))
                .setColor(color).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0)
                .setNormal(pose,(float)normal.x,(float)normal.y,(float)normal.z);
    }
}
