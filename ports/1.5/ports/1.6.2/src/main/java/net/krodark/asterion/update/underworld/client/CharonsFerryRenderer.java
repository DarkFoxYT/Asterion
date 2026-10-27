package net.krodark.asterion.update.underworld.client;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

public final class CharonsFerryRenderer extends GeoEntityRenderer<CharonsFerryEntity, EntityRenderState> {
    private static final DataTicket<Float> PITCH = DataTickets.create("asterion_ferry_pitch", Float.class);
    private static final DataTicket<Float> ROLL = DataTickets.create("asterion_ferry_roll", Float.class);
    public CharonsFerryRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) {
                return Asterion.id("entity/charons_ferry");
            }
            @Override public Identifier getTextureResource(GeoRenderState state) {
                return Asterion.id("textures/entity/charons_ferry.png");
            }
            @Override public Identifier getAnimationResource(CharonsFerryEntity entity) {
                return Asterion.id("entity/charons_ferry");
            }
        });
        shadowRadius = 1.8F;
    }

    @Override public void addRenderData(CharonsFerryEntity ferry, Void related,
                                        EntityRenderState state, float partialTick) {
        state.addGeckolibData(PITCH, ferry.rockingPitch(partialTick));
        state.addGeckolibData(ROLL, ferry.rockingRoll(partialTick));
    }

    @Override protected void applyRotations(RenderPassInfo<EntityRenderState> pass, PoseStack poses, float rotation) {
        super.applyRotations(pass, poses, rotation);
        poses.translate(0, 17.5 / 16.0, 0);
        poses.mulPose(Axis.XP.rotationDegrees(pass.getOrDefaultGeckolibData(PITCH, 0F)));
        poses.mulPose(Axis.ZP.rotationDegrees(pass.getOrDefaultGeckolibData(ROLL, 0F)));
        poses.translate(0, -17.5 / 16.0, 0);
    }
}
