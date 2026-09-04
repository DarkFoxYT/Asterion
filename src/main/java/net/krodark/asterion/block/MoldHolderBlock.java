package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** One-slot, block-state-backed rack for casting molds. */
public final class MoldHolderBlock extends Block {
    public static final IntegerProperty MOLD = IntegerProperty.create("mold", 0, 5);
    public MoldHolderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOLD, 0));
    }
    @Override protected MapCodec<? extends Block> codec() { return MapCodec.unit(this); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOLD);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                     Player player, InteractionHand hand, BlockHitResult hit) {
        int mold = CrucibleBlockEntity.moldIndex(stack.getItem());
        if (mold < 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            giveStored(level, pos, player, state.getValue(MOLD));
            level.setBlock(pos, state.setValue(MOLD, mold + 1), Block.UPDATE_ALL);
            if (!player.isCreative()) stack.shrink(1);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        int stored = state.getValue(MOLD);
        if (stored == 0) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            giveStored(level, pos, player, stored);
            level.setBlock(pos, state.setValue(MOLD, 0), Block.UPDATE_ALL);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
    private static void giveStored(Level level, BlockPos pos, Player player, int stored) {
        if (stored <= 0) return;
        Item item = switch (stored - 1) {
            case 0 -> Asterion.INGOT_CAST; case 1 -> Asterion.SWORD_GUARD_CAST;
            case 2 -> Asterion.SWORD_POMMEL_CAST; case 3 -> Asterion.SWORD_BLADE_CAST;
            default -> Asterion.AXE_HEAD_CAST;
        };
        ItemStack returned = new ItemStack(item);
        if (!player.getInventory().add(returned)) player.drop(returned, false);
    }
}
