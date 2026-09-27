package net.krodark.asterion.block;

import com.geckolib.animatable.GeoItem;
import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.resources.Identifier;
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
                    @Override public Identifier getModelResource(GeoRenderState state) {
                        return Asterion.id("block/pedestal");
                    }
                    @Override public Identifier getTextureResource(GeoRenderState state) {
                        return Asterion.id("textures/block/pedestal.png");
                    }
                    @Override public Identifier getAnimationResource(PedestalBlockItem item) { return null; }
                });
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
