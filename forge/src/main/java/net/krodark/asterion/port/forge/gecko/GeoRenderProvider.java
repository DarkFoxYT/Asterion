package net.krodark.asterion.port.forge.gecko;

import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public interface GeoRenderProvider extends IClientItemExtensions {
    GeoItemRenderer<?> getGeoItemRenderer();

    @Override
    default net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return getGeoItemRenderer();
    }
}
