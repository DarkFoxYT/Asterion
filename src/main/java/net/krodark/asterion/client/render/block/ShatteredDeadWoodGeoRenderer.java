package net.krodark.asterion.client.render.block;

import com.geckolib.renderer.GeoBlockRenderer;
import net.krodark.asterion.block.ShatteredDeadWoodBlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public final class ShatteredDeadWoodGeoRenderer
        extends GeoBlockRenderer<ShatteredDeadWoodBlockEntity, BlockEntityRenderState> {
    public ShatteredDeadWoodGeoRenderer(BlockEntityRendererProvider.Context context) {
        super(context, new ShatteredDeadWoodGeoModel());
    }
}
