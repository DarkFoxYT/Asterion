package net.krodark.asterion.client.render.block;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.MinotaurTrophyBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public final class MinotaurTrophyRenderer extends GeoBlockRenderer<MinotaurTrophyBlockEntity, BlockEntityRenderState> {
    public MinotaurTrophyRenderer(BlockEntityRendererProvider.Context context) { super(context, new Model()); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    private static final class Model extends GeoModel<MinotaurTrophyBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/minotaur_trophy"); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/entity/minotaur.png"); }
        @Override public Identifier getAnimationResource(MinotaurTrophyBlockEntity entity) { return null; }
    }
}
