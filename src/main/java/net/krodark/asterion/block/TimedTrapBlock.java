package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.game.GameplayContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public final class TimedTrapBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty ACTIVE = BlockStateProperties.TRIGGERED;
    private final boolean gas;
    public boolean gas() { return gas; }
    public TimedTrapBlock(boolean gas, Properties properties) {
        super(properties); this.gas = gas;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ACTIVE, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, ACTIVE); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getClickedFace()); }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.setValue(FACING, mirror.mirror(state.getValue(FACING))); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TimedTrapBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GameplayContent.TRAP_ENTITY, TimedTrapBlockEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TimedTrapBlockEntity trap) {
            trap.setPeriodSeconds(Math.floorMod(trap.periodSeconds() - 1 + (player.isShiftKeyDown() ? -1 : 1), 60) + 1);
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.asterion.trap_period", trap.periodSeconds()));
        }
        return InteractionResult.SUCCESS;
    }
}
