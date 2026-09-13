package net.krodark.asterion.block;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

 
public final class PedestalBlockItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PedestalBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<PedestalBlockItem> renderer;

            @Override public GeoItemRenderer<PedestalBlockItem> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new GeoModel<>() {
                    @Override public ResourceLocation getModelResource(PedestalBlockItem item) {
                        return Asterion.id("block/pedestal");
                    }
                    @Override public ResourceLocation getTextureResource(PedestalBlockItem item) {
                        return Asterion.id("textures/block/pedestal.png");
                    }
                    @Override public ResourceLocation getAnimationResource(PedestalBlockItem item) { return null; }
                });
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
