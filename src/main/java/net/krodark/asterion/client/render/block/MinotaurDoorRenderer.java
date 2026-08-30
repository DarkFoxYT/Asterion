package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.MinotaurDoorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public final class MinotaurDoorRenderer extends GeoBlockRenderer<MinotaurDoorBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Float> ANGLE = DataTickets.create("asterion_door_angle", Float.class);
    public MinotaurDoorRenderer(BlockEntityRendererProvider.Context context) { super(context, new Model()); }
    @Override public void addRenderData(MinotaurDoorBlockEntity door, Void related, BlockEntityRenderState state, float partialTick) {
        state.addGeckolibData(ANGLE, door.angle(partialTick));
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass, BoneSnapshots bones) {
        float angle = pass.getOrDefaultGeckolibData(ANGLE, 0F);
        bones.ifPresent("rightdoor", bone -> bone.setRotation(0, angle, 0));
        bones.ifPresent("leftdoor", bone -> bone.setRotation(0, -angle, 0));
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 128; }
    private static final class Model extends GeoModel<MinotaurDoorBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/minotaur_door"); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/minotaur_door.png"); }
        @Override public Identifier getAnimationResource(MinotaurDoorBlockEntity door) { return Asterion.id("block/minotaur_door"); }
    }
}
