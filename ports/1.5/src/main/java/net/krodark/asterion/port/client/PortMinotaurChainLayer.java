package net.krodark.asterion.port.client;
import com.mojang.blaze3d.vertex.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
final class PortMinotaurChainLayer extends GeoRenderLayer<MinotaurEntity> {
    private static final RenderType MATERIAL=RenderType.entityCutout(Asterion.id("textures/block/mazesteel_chain.png"));
    PortMinotaurChainLayer(PortMinotaurRenderer renderer){super(renderer);}
    @Override public void renderForBone(PoseStack poses,MinotaurEntity boss,GeoBone bone,RenderType type,MultiBufferSource buffers,VertexConsumer buffer,float partial,int light,int overlay) {
        int arm=boss.reachArmSide();
        boolean grip=bone.getName().equals(arm>=0?"right_player_grip":"left_player_grip");
        boolean upGrip=bone.getName().equals(arm>=0?"right_player_up":"left_player_up");
        int side=bone.getName().equals("hand_chainR")?1:bone.getName().equals("hand_chainL")?-1:0;
        if(!grip && !upGrip && side==0)return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        poses.pushPose();
        //? if >=1.20.5 {
            software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(poses, bone);
            //?} else {
            /*software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(poses, bone);
            *///?}
        Vector3f hand=poses.last().pose().transformPosition(new Vector3f());
        Vector3f upPoint=poses.last().pose().transformPosition(new Vector3f(0,1,0));
        poses.popPose();
        Vec3 start=new Vec3(hand.x,hand.y,hand.z);
        if(side!=0){PortWeaponChainAttachments.capture(boss.getId(),side,start.add(camera));return;}
        if(upGrip){if(boss.heldPlayerId()>=0)net.krodark.asterion.port.client.ragdoll.MinotaurHandAttachment.captureUp(boss.heldPlayerId(),start.add(camera));return;}
        if(boss.heldPlayerId()>=0){
            net.krodark.asterion.port.client.ragdoll.MinotaurHandAttachment.capture(boss.heldPlayerId(),start.add(camera));
            net.krodark.asterion.port.client.ragdoll.MinotaurHandAttachment.captureUp(boss.heldPlayerId(),new Vec3(upPoint.x,upPoint.y,upPoint.z).add(camera));
        }
        if(!boss.isChainGrappleActive()&&!boss.isPerformingGrab())return;
        var target=boss.level().getEntity(boss.grabTargetEntityId());
        float ticks=boss.bossAttackAnimationTicks()+partial;boolean held=boss.heldPlayerId()>=0;
        if(target==null||!target.isAlive()||!held&&(ticks<8||ticks>=36))return;
        Vec3 targetPos=target.getPosition(partial).add(0,target.getBbHeight()*.55,0).subtract(camera);
        float extension=held?1:Mth.clamp((ticks-12)/7,0,1)*Mth.clamp((36-ticks)/9,0,1);
        Vec3 end=held?start.add(0,-.35,0):start.lerp(targetPos,extension);
        double length=start.distanceTo(end);if(length<.05||length>40)return;
        double slack=Math.min(1.3,length*.09)*Mth.clamp(Math.abs(ticks-25)/7,.06F,1);
        draw(buffers.getBuffer(MATERIAL),start,end,slack,light);
        buffers.getBuffer(type);
    }
    static void draw(VertexConsumer out,Vec3 start,Vec3 end,double slack,int light){
        int links=Math.min(288,Math.max(2,Mth.ceil(start.distanceTo(end)/.25)));
        Vec3[] points=new Vec3[links+1];
        for(int i=0;i<=links;i++){double t=i/(double)links;points[i]=start.lerp(end,t).add(0,-4*slack*t*(1-t),0);}
        PortChainGeometry.drawWeapon(new PoseStack().last(),out,points,Vec3.ZERO,light);
    }
}
