package net.krodark.asterion.dev.verification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.util.LoadedBlockSearch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Compare the optimized search with the old full-volume scan, including edits and chunk edges. */
public final class BackgroundSearchCheck {
    public static void run(ServerLevel level) {
        BlockPos center = new BlockPos(-8, 224, -8);
        BlockPos min = center.offset(-36, -10, -36), max = center.offset(36, 10, 36);
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++)
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) level.getChunk(x, z);
        var fixtures = java.util.List.of(min, max, center, center.offset(23, 0, 23), center.offset(-9, 7, -9));
        for (BlockPos pos : fixtures) level.setBlock(pos, Asterion.PRESSURE_BUTTON.defaultBlockState(), 18);
        var expected = new ArrayList<BlockPos>();
        int originalReads = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            originalReads++;
            if (level.getBlockState(pos).is(Asterion.PRESSURE_BUTTON)) expected.add(pos.immutable());
        }
        AtomicInteger optimizedReads = new AtomicInteger();
        var actual = LoadedBlockSearch.find(level, min, max, state -> {
            optimizedReads.incrementAndGet();
            return state.is(Asterion.PRESSURE_BUTTON);
        });
        var order = Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX);
        actual.sort(order);
        check(expected.equals(actual), "Optimized scan changed marker positions/order");
        check(optimizedReads.get() < originalReads / 4, "Sparse search did not skip unrelated sections");
        level.setBlock(center, Blocks.AIR.defaultBlockState(), 18);
        BlockPos added = center.offset(1, 0, 0);
        level.setBlock(added, Asterion.PRESSURE_BUTTON.defaultBlockState(), 18);
        actual = LoadedBlockSearch.find(level, min, max, state -> state.is(Asterion.PRESSURE_BUTTON));
        check(!actual.contains(center) && actual.contains(added), "Search retained stale block state");
        int remote = 100_000;
        check(level.getChunk(remote, remote, ChunkStatus.FULL, false) == null, "Remote test chunk already loaded");
        var remotePos = new BlockPos(remote << 4, 224, remote << 4);
        check(LoadedBlockSearch.find(level, remotePos, remotePos.offset(15, 15, 15), state -> true).isEmpty(), "Unloaded chunk was searched");
        check(level.getChunk(remote, remote, ChunkStatus.FULL, false) == null, "Search loaded a remote chunk");
        for (BlockPos pos : fixtures) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
        level.setBlock(added, Blocks.AIR.defaultBlockState(), 18);
        Asterion.LOGGER.info("PASS: identical background scan results; {} -> {} state checks; live edits and unloaded chunks verified",
                originalReads, optimizedReads.get());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
