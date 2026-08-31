package net.krodark.asterion.mixin;

import net.krodark.asterion.WorldGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void asterion$retainHeavyWater(BlockPlaceContext context, CallbackInfoReturnable<BlockState> result) {
        BlockState state = result.getReturnValue();
        if (state == null || !net.krodark.asterion.fluid.HeavyWaterlogging.supports(state)) return;
        var fluid = context.getLevel().getFluidState(context.getClickedPos());
        if (state.getBlock() instanceof net.krodark.asterion.block.WaterloggedDecoration) {
            result.setReturnValue(net.krodark.asterion.block.WaterloggedDecoration.retain(state, fluid));
            return;
        }
        if (net.krodark.asterion.fluid.HeavyWaterlogging.isHeavy(fluid.getType())
                && net.krodark.asterion.fluid.HeavyWaterlogging.canFill(context.getPlayer(), context.getLevel(),
                        context.getClickedPos(), net.krodark.asterion.fluid.HeavyWaterlogging.dry(state)))
            result.setReturnValue(net.krodark.asterion.fluid.HeavyWaterlogging.withFluid(state, fluid));
    }

    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void asterion$trackTemporaryBlock(BlockPlaceContext context, BlockState state,
                                                CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValue() && context.getLevel() instanceof ServerLevel level)
            WorldGenerator.trackPlayerPlacement(level, context.getClickedPos(), state);
    }
}
