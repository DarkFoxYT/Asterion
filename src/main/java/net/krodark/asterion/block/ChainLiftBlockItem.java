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

import java.util.List;
import java.util.function.Consumer;

 
public final class ChainLiftBlockItem extends BlockItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ChainLiftBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override public net.minecraft.world.InteractionResult place(net.minecraft.world.item.context.BlockPlaceContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        boolean supported = level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
        int ceiling = ChainLiftBlockEntity.findCeiling(level, pos);
        var footprint = new net.minecraft.world.phys.AABB(pos.getX()-1, pos.getY()+.5, pos.getZ()-1,
                pos.getX()+2, pos.getY()+3.5, pos.getZ()+2);
        boolean clear = !level.getBlockCollisions(null, footprint).iterator().hasNext();
        boolean overlaps = ceiling != ChainLiftBlockEntity.NO_CEILING && !level.getEntitiesOfClass(net.krodark.asterion.entity.ChainLiftEntity.class,
                footprint.expandTowards(0, ceiling-pos.getY(), 0)).isEmpty();
        if (!supported || ceiling == ChainLiftBlockEntity.NO_CEILING || !clear || overlaps) {
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.asterion.chain_lift.space"), true);
            return net.minecraft.world.InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("tooltip.asterion.chain_lift")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<ChainLiftBlockItem> renderer;

            @Override public GeoItemRenderer<ChainLiftBlockItem> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new GeoModel<>() {
                    @SuppressWarnings("deprecation")
                    @Override public ResourceLocation getModelResource(ChainLiftBlockItem item) {
                        return Asterion.id("block/chain_lift");
                    }
                    @SuppressWarnings("deprecation")
                    @Override public ResourceLocation getTextureResource(ChainLiftBlockItem item) {
                        return Asterion.id("textures/block/chain_lift.png");
                    }
                    @Override public ResourceLocation getAnimationResource(ChainLiftBlockItem item) { return null; }
                });
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
