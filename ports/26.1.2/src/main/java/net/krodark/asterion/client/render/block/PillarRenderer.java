package net.krodark.asterion.client.render.block;

import com.geckolib.constant.DataTickets;
import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public final class PillarRenderer extends GeoBlockRenderer<PillarBlockEntity,BlockEntityRenderState> {
    private static final DataTicket<Float> SCALE=DataTickets.create("asterion_pillar_height",Float.class);
    public PillarRenderer(BlockEntityRendererProvider.Context context) { super(context,new Model()); }
    @Override public void addRenderData(PillarBlockEntity pillar,Void related,BlockEntityRenderState state,float partial) {
        state.addGeckolibData(SCALE,pillar.getBlockState().getValue(PillarBlock.HEIGHT)/27F);
    }
    @Override public void adjustModelBonesForRender(RenderPassInfo<BlockEntityRenderState> pass,BoneSnapshots bones) {
        bones.ifPresent("full",bone->bone.setScale(1,pass.getOrDefaultGeckolibData(SCALE,1F),1));
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 128; }
    private static final class Model extends GeoModel<PillarBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/pillar"); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/pillar.png"); }
        @Override public Identifier getAnimationResource(PillarBlockEntity pillar) { return Asterion.id("block/pillar"); }
    }
}
