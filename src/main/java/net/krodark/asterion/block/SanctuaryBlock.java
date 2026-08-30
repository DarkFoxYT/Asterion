package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionWorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SanctuaryBlock extends BaseEntityBlock {
    // Altar: dormant / charged / collected. Obelisk: inactive / active.
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 2);
    public final boolean altar;
    public SanctuaryBlock(boolean altar, Properties properties) {
        super(properties); this.altar = altar;
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(CHARGE); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SanctuaryBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RespawnObelisks.BLOCK_ENTITY, SanctuaryBlockEntity::tick);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return altar ? box(1, 0, 1, 15, 10.5, 15)
                : Shapes.or(box(2, 0, 2, 14, 3, 14), box(4, 3, 4, 12, 13, 12),
                        box(5, 13, 5, 11, 15, 11), box(6, 15, 6, 10, 16, 10));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(ItemStack.EMPTY, state, level, pos, player);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
        return interact(stack, state, level, pos, player);
    }
    private InteractionResult interact(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (altar) {
            if (state.getValue(CHARGE) != 1) {
                serverPlayer.sendOverlayMessage(Component.translatable(state.getValue(CHARGE) == 0
                        ? "message.asterion.altar_dormant" : "message.asterion.altar_empty"));
                return InteractionResult.SUCCESS_SERVER;
            }
            ItemStack reward = new ItemStack(RespawnObelisks.CHARGED_RUNE);
            // Leave the charge in place if the inventory is full; never lose or duplicate it.
            if (!player.getInventory().add(reward)) return InteractionResult.FAIL;
            level.setBlock(pos, state.setValue(CHARGE, 2), 3);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1, 1.25F);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.asterion.obelisk_maze_only"));
            return InteractionResult.FAIL;
        }
        BlockPos spawn = safeSpawn(server, pos);
        if (spawn == null) {
            serverPlayer.sendOverlayMessage(Component.translatable("message.asterion.obelisk_blocked"));
            return InteractionResult.FAIL;
        }
        if (state.getValue(CHARGE) != 1) {
            if (!stack.is(RespawnObelisks.CHARGED_RUNE)) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.asterion.obelisk_needs_rune"));
                return InteractionResult.SUCCESS_SERVER;
            }
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlock(pos, state.setValue(CHARGE, 1), 3);
            if (level.getBlockEntity(pos) instanceof SanctuaryBlockEntity sanctuary) sanctuary.startPulse();
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.8F, .7F);
        }
        AsterionWorldState.get(server).setRuneCheckpoint(player.getUUID(), spawn);
        serverPlayer.sendOverlayMessage(Component.translatable("message.asterion.obelisk_bound"));
        return InteractionResult.SUCCESS_SERVER;
    }
    private static BlockPos safeSpawn(ServerLevel level, BlockPos origin) {
        for (int distance = 1; distance <= 3; distance++) for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = origin.relative(direction, distance);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getFluidState(pos).isEmpty() && level.getFluidState(pos.above()).isEmpty()
                    && !level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)
                    && !level.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK)
                    && !level.getBlockState(pos.below()).is(Blocks.CAMPFIRE)
                    && !level.getBlockState(pos.below()).is(Blocks.SOUL_CAMPFIRE)
                    && level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())) return pos;
        }
        return null;
    }
}
