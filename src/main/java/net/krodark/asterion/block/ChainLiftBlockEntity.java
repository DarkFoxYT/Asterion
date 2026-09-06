package net.krodark.asterion.block;

import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class ChainLiftBlockEntity extends BlockEntity {
    public static final int NO_CEILING = Integer.MIN_VALUE;
    private boolean spawned;
    public ChainLiftBlockEntity(BlockPos pos, BlockState state) { super(ChainLiftContent.BLOCK_ENTITY, pos, state); }
     
    public static int findCeiling(Level level, BlockPos base) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int limit = Math.min(level.getMaxY(), base.getY() + 128);
        for (int y = base.getY() + 1; y <= limit; y++) {
            cursor.set(base.getX(), y, base.getZ());
            if (level.getBlockState(cursor).isFaceSturdy(level, cursor, Direction.DOWN)) return y >= base.getY()+6 ? y : NO_CEILING;
            for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
                cursor.set(base.getX()+dx, y, base.getZ()+dz);
                if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) return NO_CEILING;
            }
        }
        return NO_CEILING;
    }
    public static void tick(Level world, BlockPos pos, BlockState state, ChainLiftBlockEntity block) {
        if (block.spawned || !(world instanceof ServerLevel level) || level.getGameTime()%20 != 0) return;
        int ceiling = findCeiling(level, pos);
        if (ceiling == NO_CEILING) return;
        var id = java.util.UUID.nameUUIDFromBytes((level.dimension().identifier() + ":chain_lift:" + pos.asLong())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (level.getEntity(id) != null) { block.spawned = true; block.setChanged(); return; }
        var lift = ChainLiftContent.LIFT.create(level, EntitySpawnReason.EVENT);
        if (lift == null) return;
        lift.setUUID(id);
        lift.configure(pos, ceiling);
        if (level.addFreshEntity(lift)) { block.spawned = true; block.setChanged(); }
    }
    @Override protected void saveAdditional(ValueOutput out) { super.saveAdditional(out); out.putBoolean("Spawned", spawned); }
    @Override protected void loadAdditional(ValueInput in) { super.loadAdditional(in); spawned = in.getBooleanOr("Spawned", false); }
}
