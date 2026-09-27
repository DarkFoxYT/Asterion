package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CursedBrazierDoorBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public final class CursedBrazierDoorRenderer extends GeoBlockRenderer<CursedBrazierDoorBlockEntity, BlockEntityRenderState> {
    private static final DataTicket<Float> LIFT = DataTickets.create("asterion_cursed_door_lift", Float.class);
    public CursedBrazierDoorRenderer(BlockEntityRendererProvider.Context context) { super(context, new Model()); }
    @Override public void addRenderData(CursedBrazierDoorBlockEntity door, Void related,
                                        BlockEntityRenderState state, float partialTick) {
        state.addGeckolibData(LIFT, door.progress(partialTick));
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass, BoneSnapshots bones) {
        float lift = pass.getOrDefaultGeckolibData(LIFT, 0F);
        bones.ifPresent("full", bone -> bone.setTranslateY(lift * 72F));
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public boolean shouldRender(CursedBrazierDoorBlockEntity door, Vec3 camera) {
        return net.krodark.asterion.block.CursedBrazierDoorBlock.isRoot(door.getBlockState())
                && super.shouldRender(door, camera);
    }
    @Override public int getViewDistance() { return 128; }
    private static final class Model extends GeoModel<CursedBrazierDoorBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/cursed_brazier_door"); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/cursed_brazier_door.png"); }
        @Override public Identifier getAnimationResource(CursedBrazierDoorBlockEntity door) { return Asterion.id("block/cursed_brazier_door"); }
    }
}
