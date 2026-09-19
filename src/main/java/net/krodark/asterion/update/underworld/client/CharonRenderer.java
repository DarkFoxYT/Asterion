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
import net.krodark.asterion.update.underworld.entity.CharonEntity;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

/** Single model pass, including the hood: no coincident overlay geometry. */
public final class CharonRenderer extends GeoEntityRenderer<CharonEntity, EntityRenderState> {
    private static final Identifier MODEL = Asterion.id("entity/charon");
    private static final Identifier TEXTURE = Asterion.id("textures/entity/charon.png");
    private static final DataTicket<Float> PITCH = DataTickets.create("asterion_charon_pitch", Float.class);
    private static final DataTicket<Float> ROLL = DataTickets.create("asterion_charon_roll", Float.class);

    public CharonRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
            @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
            @Override public Identifier getAnimationResource(CharonEntity entity) { return Asterion.id("entity/charon"); }
        });
        shadowRadius = .55F;
        withScale(.65F);
    }

    @Override public void addRenderData(CharonEntity charon, Void related, EntityRenderState state, float partialTick) {
        if (charon.getVehicle() instanceof CharonsFerryEntity ferry) {
            state.addGeckolibData(PITCH, ferry.rockingPitch(partialTick));
            state.addGeckolibData(ROLL, ferry.rockingRoll(partialTick));
        } else {
            state.addGeckolibData(PITCH, 0F);
            state.addGeckolibData(ROLL, 0F);
        }
    }

    @Override protected void applyRotations(RenderPassInfo<EntityRenderState> pass, PoseStack poses, float rotation) {
        super.applyRotations(pass, poses, rotation);
        poses.mulPose(Axis.XP.rotationDegrees(pass.getOrDefaultGeckolibData(PITCH, 0F)));
        poses.mulPose(Axis.ZP.rotationDegrees(pass.getOrDefaultGeckolibData(ROLL, 0F)));
    }
}
