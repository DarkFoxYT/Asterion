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

import java.util.function.Consumer;

/** Inventory/hand renderer for the same authored 3D rune plaque used by the block entity. */
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
                    @Override public Identifier getModelResource(GeoRenderState state) {
                        return Asterion.id("block/rune");
                    }
                    @Override public Identifier getTextureResource(GeoRenderState state) {
                        return Asterion.id("textures/block/runes/" + (runeIndex + 1) + ".png");
                    }
                    @Override public Identifier getAnimationResource(RuneBlockItem item) {
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
