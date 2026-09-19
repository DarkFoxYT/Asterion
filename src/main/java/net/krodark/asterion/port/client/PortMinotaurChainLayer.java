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
        if(!bone.getName().equals(arm>=0?"right_player_grip":"left_player_grip"))return;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        poses.pushPose();
        //? if >=1.20.5 {
            software.bernie.geckolib.util.RenderUtil.translateToPivotPoint(poses, bone);
            //?} else {
            /*software.bernie.geckolib.util.RenderUtils.translateToPivotPoint(poses, bone);
            *///?}
        Vector3f hand=poses.last().pose().transformPosition(new Vector3f());
        poses.popPose();
        Vec3 start=new Vec3(hand.x,hand.y,hand.z);
        if(boss.heldPlayerId()>=0) net.krodark.asterion.port.client.ragdoll.MinotaurHandAttachment.capture(boss.heldPlayerId(),start.add(camera));
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
    }
    static void draw(VertexConsumer out, Vec3 start, Vec3 end, double slack, int light) {
        Vec3 axis = end.subtract(start).normalize();
        Vec3 across = axis.cross(Math.abs(axis.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        across = across.add(axis.cross(across)).normalize();
        Vec3 other = axis.cross(across).normalize();
        int links = Math.min(288, Math.max(1, Mth.ceil(start.distanceTo(end) / .45)));
        Vec3 a = start;
        for (int i = 1; i <= links; i++) {
            double t = i / (double)links;
            Vec3 b = start.lerp(end, t).add(0, -4 * slack * t * (1 - t), 0);

            quad(out, a, b, across.scale(.20), other, 0, .25F, light);
            quad(out, a, b, other.scale(.20), across, 4.25F / 16, 8.25F / 16, light);
            a = b;
        }
    }

    private static void quad(VertexConsumer out, Vec3 a, Vec3 b, Vec3 width, Vec3 normal, float u0, float u1, int light) {
        vertex(out, a.subtract(width), u0, 0, normal, light);
        vertex(out, a.add(width), u1, 0, normal, light);
        vertex(out, b.add(width), u1, .25F, normal, light);
        vertex(out, b.subtract(width), u0, .25F, normal, light);
    }

    private static void vertex(VertexConsumer out, Vec3 point, float u, float v, Vec3 normal, int light) {
        //? if >=1.20.5 {
        out.addVertex((float)point.x,(float)point.y,(float)point.z).setColor(-1).setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal((float)normal.x,(float)normal.y,(float)normal.z);
        //?} else {
        /*out.vertex(point.x,point.y,point.z).color(-1).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal((float)normal.x,(float)normal.y,(float)normal.z).endVertex();
        *///?}
    }
}
