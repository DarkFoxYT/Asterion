package net.krodark.asterion.block;

import com.mojang.serialization.MapCodec;
import net.krodark.asterion.game.GameplayContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;

/** Single-use proximity encounter; creative players do not activate it. */
public final class ChallengeSpawnerBlock extends BaseEntityBlock {
    private final boolean explosive;
    public ChallengeSpawnerBlock(boolean explosive, Properties properties) { super(properties); this.explosive = explosive; }
    public boolean explosive() { return explosive; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return MapCodec.unit(this); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ChallengeSpawnerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GameplayContent.CHALLENGE_SPAWNER_ENTITY, ChallengeSpawnerBlockEntity::tick);
    }
}
