package net.krodark.asterion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.material.Fluids;

/** Vanilla placement, joining, waterlogging, dripping, collision and falling damage. */
public final class ShaleSpikeBlock extends PointedDripstoneBlock {
    public ShaleSpikeBlock(Properties properties) { super(properties); }

    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        maybeTransferFluid(state, level, pos, random.nextFloat());
        if (state.getValue(TIP_DIRECTION) != Direction.DOWN || random.nextFloat() >= .011377778F
                || level.getBlockState(pos.above()).is(this)
                || !level.getFluidState(pos.above(2)).is(Fluids.WATER)) return;
        BlockPos tip = pos;
        for (int i=0;i<7;i++) {
            BlockState current=level.getBlockState(tip);
            if (!current.is(this) || current.getValue(TIP_DIRECTION)!=Direction.DOWN) return;
            if (current.getValue(THICKNESS)==DripstoneThickness.TIP) {
                if (level.getBlockState(tip.below()).isAir())
                    level.setBlockAndUpdate(tip.below(), defaultBlockState().setValue(TIP_DIRECTION, Direction.DOWN));
                return;
            }
            tip=tip.below();
        }
    }
}
