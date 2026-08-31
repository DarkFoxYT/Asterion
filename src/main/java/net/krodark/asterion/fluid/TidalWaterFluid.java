package net.krodark.asterion.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;

/** Water immersion/extinguishing, but the tide controller alone changes its level. */
public final class TidalWaterFluid extends WaterFluid {
    public TidalWaterFluid() { registerDefaultState(stateDefinition.any().setValue(LEVEL, 8)); }
    @Override protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        // Deliberately omit FALLING: this liquid has exactly eight states, never waterfalls.
        builder.add(LEVEL);
    }
    @Override public Fluid getSource() { return this; }
    @Override public Fluid getFlowing() { return this; }
    @Override public FluidState getSource(boolean falling) { return defaultFluidState(); }
    @Override public FluidState getFlowing(int amount, boolean falling) {
        return defaultFluidState().setValue(LEVEL, Math.clamp(amount, 1, 8));
    }
    @Override public Item getBucket() { return HeavyWater.BUCKET; }
    @Override public int getAmount(FluidState state) { return state.getValue(LEVEL); }
    @Override public boolean isSource(FluidState state) { return getAmount(state) == 8; }
    @Override public float getOwnHeight(FluidState state) { return getAmount(state) / 8.0F; }
    @Override public boolean isSame(Fluid other) { return other == this || other == HeavyWater.STILL || other == HeavyWater.FLOWING; }
    @Override public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) { return Vec3.ZERO; }
    @Override protected boolean canConvertToSource(ServerLevel level) { return false; }
    @Override public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos,
                                             Fluid other, Direction direction) { return false; }
    @Override public BlockState createLegacyBlock(FluidState state) {
        return HeavyWater.BLOCK.defaultBlockState().setValue(TidalWaterBlock.LEVEL, getAmount(state));
    }
    @Override public void tick(ServerLevel level, BlockPos pos, BlockState block, FluidState state) { }
    @Override public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
        if (random.nextInt(64) == 0) level.addParticle(ParticleTypes.UNDERWATER,
                pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble() * getOwnHeight(state),
                pos.getZ() + random.nextDouble(), 0, 0, 0);
    }
}
