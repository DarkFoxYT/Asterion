package net.krodark.asterion.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Searches loaded sections, rejecting unrelated palettes before visiting individual blocks. */
public final class LoadedBlockSearch {
    private LoadedBlockSearch() { }

    public static List<BlockPos> find(Level level, BlockPos min, BlockPos max, Predicate<BlockState> matches) {
        List<BlockPos> found = new ArrayList<>();
        int minY = Math.max(min.getY(), level.getMinY());
        int maxY = Math.min(max.getY(), level.getMaxY() - 1);
        if (minY > maxY) return found;

        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                var chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                int fromX = Math.max(min.getX(), cx << 4);
                int toX = Math.min(max.getX(), (cx << 4) + 15);
                int fromZ = Math.max(min.getZ(), cz << 4);
                int toZ = Math.min(max.getZ(), (cz << 4) + 15);

                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    var section = chunk.getSection(chunk.getSectionIndex(sy << 4));
                    if (!section.maybeHas(matches)) continue;
                    int fromY = Math.max(minY, sy << 4);
                    int toY = Math.min(maxY, (sy << 4) + 15);
                    for (int y = fromY; y <= toY; y++) {
                        for (int x = fromX; x <= toX; x++) {
                            for (int z = fromZ; z <= toZ; z++) {
                                if (matches.test(section.getBlockState(x & 15, y & 15, z & 15)))
                                    found.add(new BlockPos(x, y, z));
                            }
                        }
                    }
                }
            }
        }
        return found;
    }
}
