package net.krodark.asterion.fluid;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** No scheduled block ticks, block entities or vanilla fluid neighbor updates. */
public final class TidalWaterBlock extends Block implements BucketPickup {
    public static final MapCodec<TidalWaterBlock> CODEC = simpleCodec(TidalWaterBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 8);
    public TidalWaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 8));
    }
    @Override public MapCodec<TidalWaterBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LEVEL); }
    @Override protected FluidState getFluidState(BlockState state) {
        return HeavyWater.FLUID.getFlowing(state.getValue(LEVEL), false);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
    @Override protected boolean isPathfindable(BlockState state, PathComputationType type) { return true; }
    @Override public ItemStack pickupBlock(@Nullable LivingEntity user, LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(LEVEL) != 8) return ItemStack.EMPTY;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
        return new ItemStack(HeavyWater.BUCKET);
    }
    @Override public Optional<SoundEvent> getPickupSound() { return HeavyWater.FLUID.getPickupSound(); }
}
