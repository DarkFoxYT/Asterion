package net.krodark.asterion.port.legacy.gecko;
public interface GeoRenderProvider extends software.bernie.geckolib.animatable.client.RenderProvider {
 software.bernie.geckolib.renderer.GeoItemRenderer<?> getGeoItemRenderer();
 @Override default net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer(){return getGeoItemRenderer();}
}
