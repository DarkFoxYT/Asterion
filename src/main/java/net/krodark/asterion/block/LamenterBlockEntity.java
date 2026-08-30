package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.CatacombFloodState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public final class LamenterBlockEntity extends BlockEntity {
    public static final int EXTINGUISH_TICKS = 160;
    public static final int TEAR_REACH = 6;
    // Pixel centers on the supplied 16px face: two separate tracks beneath each eye.
    private static final double[] TEAR_U = {3.5 / 16, 4.5 / 16, 11.5 / 16, 12.5 / 16};
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private BlockPos wetBrazier;
    private int soakingTicks;
    private long lastTick = Long.MIN_VALUE;

    public LamenterBlockEntity(BlockPos pos, BlockState state) {
        super(Asterion.LAMENTER_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LamenterBlockEntity lamenter) {
        if (level.isClientSide()) {
            if (state.getValue(LamenterBlock.CRYING)) {
                int phase = Math.floorMod(level.getGameTime() + pos.asLong(), 8);
                if (phase != 0 && phase != 4) return;
                // Client-local particles: no per-tear packets or custom renderer/buffer allocations.
                for (int track = phase == 0 ? 0 : 1; track < TEAR_U.length; track += 2) {
                    Direction facing = state.getValue(LamenterBlock.FACING);
                    Vec3 source = tearOrigin(pos, facing, track);
                    // A slight outward arc carries drops over the brazier's inset rim.
                    level.addParticle(Asterion.LAMENTER_TEAR, source.x, source.y, source.z,
                            facing.getStepX() * .014, 0, facing.getStepZ() * .014);
                }
            }
            return;
        }
        ServerLevel server = (ServerLevel) level;
        boolean crying = state.getValue(LamenterBlock.ACTIVE) || level.hasNeighborSignal(pos)
                || CatacombFloodState.isFlooding(server, pos);
        if (crying != state.getValue(LamenterBlock.CRYING)) {
            level.setBlock(pos, state.setValue(LamenterBlock.CRYING, crying), Block.UPDATE_CLIENTS);
            if (crying) level.playSound(null, pos, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                    SoundSource.BLOCKS, .65F, .8F);
        }
        // Paused/unloaded chunks never earn crying time. Progress is deliberately not persisted.
        if (lamenter.lastTick != level.getGameTime() - 1) lamenter.resetSoaking();
        lamenter.lastTick = level.getGameTime();
        if (!crying) { lamenter.resetSoaking(); return; }
        BlockPos target = lamenter.findBrazier(server, pos, state.getValue(LamenterBlock.FACING));
        if (target == null) { lamenter.resetSoaking(); return; }
        if (!target.equals(lamenter.wetBrazier)) {
            lamenter.wetBrazier = target.immutable();
            lamenter.soakingTicks = 0;
        }
        if (++lamenter.soakingTicks >= EXTINGUISH_TICKS) {
            GreekBrazierBlock.extinguish(server, target);
            lamenter.resetSoaking();
        }
    }

    private BlockPos findBrazier(ServerLevel level, BlockPos pos, Direction facing) {
        cursor.set(pos).move(facing);
        for (int down = 0; down <= TEAR_REACH; down++, cursor.move(Direction.DOWN)) {
            if (!level.getChunkSource().hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) return null;
            BlockState candidate = level.getBlockState(cursor);
            if (candidate.is(Asterion.GREEK_BRAZIER))
                return candidate.getValue(BlockStateProperties.LIT) ? cursor : null;
            // Solid blocks, slabs and fluids intercept tears; never extinguish through walls.
            if (!candidate.getCollisionShape(level, cursor).isEmpty() || !candidate.getFluidState().isEmpty()) return null;
        }
        return null;
    }

    private void resetSoaking() { wetBrazier = null; soakingTicks = 0; }

    public static Vec3 tearOrigin(BlockPos pos, Direction facing, int track) {
        double across = TEAR_U[track] - .5;
        Direction right = facing.getCounterClockWise();
        return new Vec3(pos.getX() + .5 + facing.getStepX() * .515 + right.getStepX() * across,
                pos.getY() + 8.5 / 16,
                pos.getZ() + .5 + facing.getStepZ() * .515 + right.getStepZ() * across);
    }
}
