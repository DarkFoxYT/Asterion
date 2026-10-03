package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Renders the simulated weapon entity with its authored Gecko model and texture. */
@SuppressWarnings("deprecation")
public final class MinotaurAxePortRenderer extends EntityRenderer<MinotaurAxeEntity> {
    private static final PortMinotaurRenderer.WeaponObject AXE = new PortMinotaurRenderer.WeaponObject();
    private static final PortMinotaurRenderer.WeaponObject SWORD = new PortMinotaurRenderer.WeaponObject();
    private static final GeoObjectRenderer<PortMinotaurRenderer.WeaponObject> AXE_RENDERER =
            new PortMinotaurRenderer.WeaponRenderer(false);
    private static final GeoObjectRenderer<PortMinotaurRenderer.WeaponObject> SWORD_RENDERER =
            new PortMinotaurRenderer.WeaponRenderer(true);

    public MinotaurAxePortRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = .6F;
    }

    @Override
    public void render(MinotaurAxeEntity entity, float yaw, float partialTick, PoseStack poses,
                       MultiBufferSource buffers, int packedLight) {
        Vec3 origin=entity.getPosition(partialTick);
        Vec3[] chain=entity.chainPoints(partialTick);
        if(chain!=null){
            Vec3 hand=PortWeaponChainAttachments.hand(entity.throwerId(),entity.chainSide());
            if(hand!=null){Vec3 correction=hand.subtract(chain[0]);for(int i=0;i<chain.length;i++)chain[i]=chain[i].add(correction.scale(Math.pow(1-i/(double)(chain.length-1),2)));}
            PortChainGeometry.drawWeapon(poses.last(),buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutout(net.krodark.asterion.Asterion.id("textures/block/mazesteel_chain.png"))),chain,origin.scale(-1),packedLight);
        }
        PortWeaponTrails.draw(poses,buffers,entity.trailPoints(partialTick),origin,entity.isSword());
        poses.pushPose();
        var rotation=entity.renderRotation(partialTick);
        var release=entity.tickCount<4?PortWeaponRelease.get(entity.throwerId(),entity.isSword()?entity.chainSide():0):null;
        if(release!=null){
            float blend=net.minecraft.util.Mth.clamp((entity.tickCount+partialTick)/4F,0,1);
            blend=blend*blend*(3-2*blend);
            Vec3 offset=release.center().subtract(origin).scale(1-blend);
            poses.translate(offset.x,offset.y,offset.z);
            rotation=new org.joml.Quaternionf(release.rotation()).slerp(rotation,blend);
        }
        poses.mulPose(rotation);
        poses.scale(entity.modelScale(), entity.modelScale(), entity.modelScale());
        poses.translate(0, -entity.modelCenterY(), 0);
        if (entity.isSword()) poses.translate(0, 6.0 / 16.0, 0);
        if (entity.isSword())
//? if >=1.20.5 {
SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight, partialTick);
//?} else {
/*SWORD_RENDERER.render(poses, SWORD, buffers, null, null, packedLight);*/
//?}

        else {
            // Matches the original physics visual's submitAligned transform.
            poses.mulPose(Axis.YP.rotationDegrees(-90));

//? if >=1.20.5 {
AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight, partialTick);
//?} else {
/*AXE_RENDERER.render(poses, AXE, buffers, null, null, packedLight);*/
//?}

        }
        poses.popPose();
        super.render(entity, yaw, partialTick, poses, buffers, packedLight);
    }

    @Override public ResourceLocation getTextureLocation(MinotaurAxeEntity entity) {
        return entity.isSword()
                ? net.krodark.asterion.Asterion.id("textures/physics/sword.png")
                : net.krodark.asterion.Asterion.id("textures/physics/axe.png");
    }
}
