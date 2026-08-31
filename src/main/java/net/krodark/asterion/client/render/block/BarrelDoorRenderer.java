package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.BarrelDoorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public final class BarrelDoorRenderer extends GeoBlockRenderer<BarrelDoorBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Float> ANGLE = DataTickets.create("asterion_barrel_door_angle", Float.class);
    public BarrelDoorRenderer(BlockEntityRendererProvider.Context context) { super(context, new Model()); }
    @Override public void addRenderData(BarrelDoorBlockEntity door, Void related, BlockEntityRenderState state, float partialTick) {
        state.addGeckolibData(ANGLE, door.angle(partialTick));
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass, BoneSnapshots bones) {
        float angle = pass.getOrDefaultGeckolibData(ANGLE, 0F);
        bones.ifPresent("door", bone -> bone.setRotation(0, angle, 0));
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 128; }
    private static final class Model extends GeoModel<BarrelDoorBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/barrel_door"); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/barrel_door.png"); }
        @Override public Identifier getAnimationResource(BarrelDoorBlockEntity door) { return Asterion.id("block/barrel_door"); }
    }
}
