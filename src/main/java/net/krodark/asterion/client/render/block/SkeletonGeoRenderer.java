package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.GeoBlockRenderer;
import net.krodark.asterion.block.SkeletonBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public final class SkeletonGeoRenderer extends GeoBlockRenderer<SkeletonBlockEntity, BlockEntityRenderState> {
    public SkeletonGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new SkeletonGeoModel());
    }
}
