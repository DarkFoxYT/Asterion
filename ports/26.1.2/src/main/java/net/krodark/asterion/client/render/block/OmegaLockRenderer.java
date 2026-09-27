package net.krodark.asterion.client.render.block;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoBlockRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.OmegaLockBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.Identifier;

public final class OmegaLockRenderer extends GeoBlockRenderer<OmegaLockBlockEntity, BlockEntityRenderState> {
    public OmegaLockRenderer(BlockEntityRendererProvider.Context context) { super(context, new Model()); }
    private static final class Model extends GeoModel<OmegaLockBlockEntity> {
        @Override public Identifier getModelResource(GeoRenderState state) { return Asterion.id("block/omega_lock"); }
        @Override public Identifier getTextureResource(GeoRenderState state) { return Asterion.id("textures/block/runes/24.png"); }
        @Override public Identifier getAnimationResource(OmegaLockBlockEntity animatable) { return Asterion.id("block/omega_lock"); }
    }
}
