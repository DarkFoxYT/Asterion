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
                player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.asterion.chain_lift.space"));
            return net.minecraft.world.InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            Consumer<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("tooltip.asterion.chain_lift")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<ChainLiftBlockItem> renderer;

            @Override public GeoItemRenderer<ChainLiftBlockItem> getGeoItemRenderer() {
                if (renderer == null) renderer = new GeoItemRenderer<>(new GeoModel<>() {
                    @Override public Identifier getModelResource(GeoRenderState state) {
                        return Asterion.id("block/chain_lift");
                    }
                    @Override public Identifier getTextureResource(GeoRenderState state) {
                        return Asterion.id("textures/block/chain_lift.png");
                    }
                    @Override public Identifier getAnimationResource(ChainLiftBlockItem item) { return null; }
                });
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
