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

import java.util.function.Consumer;

 
public final class RuneBlockItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final int runeIndex;

    public RuneBlockItem(RuneBlock block, int runeIndex, Properties properties) {
        super(block, properties);
        this.runeIndex = runeIndex;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<RuneBlockItem> renderer;

            @Override
            public GeoItemRenderer<RuneBlockItem> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new GeoModel<>() {
                    @SuppressWarnings("deprecation")
                    @Override public ResourceLocation getModelResource(RuneBlockItem item) {
                        return Asterion.id("block/rune");
                    }
                    @SuppressWarnings("deprecation")
                    @Override public ResourceLocation getTextureResource(RuneBlockItem item) {
                        return Asterion.id("textures/block/runes/" + (runeIndex + 1) + ".png");
                    }
                    @Override public ResourceLocation getAnimationResource(RuneBlockItem item) {
                        return Asterion.id("block/rune");
                    }
                });
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
