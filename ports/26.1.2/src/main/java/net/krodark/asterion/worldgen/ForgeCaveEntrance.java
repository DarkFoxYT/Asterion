package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

 
final class ForgeCaveEntrance {
    private record Cell(BlockState state, int priority) {}
    private static final List<BlockPos> LOOP = loop();

    private ForgeCaveEntrance() {}

    static void place(ServerLevelAccessor level, ChunkPos chunk, long seed, int cx, int cz, int top) {
        int mouthX = cx - 32;
        int bottom = ShaleCaves.floorY(seed, mouthX, cz) + 1;
        int spiralTop = top - 4;
        int turns = Math.max(1, (int)Math.ceil((spiralTop - bottom) / 14.0));
        int steps = turns * LOOP.size();
        Map<BlockPos, Cell> plan = new HashMap<>();
        for (int i = 0; i <= steps; i++) {
            BlockPos local = LOOP.get(i % LOOP.size());
            BlockPos previous = LOOP.get(Math.floorMod(i - 1, LOOP.size()));
            int x = cx - 39 + local.getX(), z = cz + local.getZ();
            if (x + 4 < chunk.getMinBlockX() || x - 4 > chunk.getMaxBlockX()
                    || z + 4 < chunk.getMinBlockZ() || z - 4 > chunk.getMaxBlockZ()) continue;
            int feet = spiralTop - (int)Math.floor((spiralTop - bottom) * i / (double)steps);
            int previousFeet = spiralTop - (int)Math.floor((spiralTop - bottom) * Math.max(0, i - 1) / (double)steps);
            Direction uphill = Direction.getApproximateNearest(previous.getX() - local.getX(), 0,
                    previous.getZ() - local.getZ());
            boolean dark = ShaleCaves.shaded(seed, x, feet - 1, z);
            BlockState rock = (dark ? Asterion.SHADED_SHALE : Asterion.SHALE).defaultBlockState();
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                int px = x + dx, pz = z + dz;
                if (px < chunk.getMinBlockX() || px > chunk.getMaxBlockX()
                        || pz < chunk.getMinBlockZ() || pz > chunk.getMaxBlockZ()) continue;
                int distance = dx * dx + dz * dz;
                int edge = 12 + (int)Math.floorMod(CatacombLayout.hash(seed, px, pz), 4);
                if (distance > edge) continue;
                int ceiling = feet + (distance <= 2 ? 5 : 4);
                for (int y = feet - 3; y <= ceiling + 1; y++) {
                     
                     
                    boolean stairShell = px >= cx - 28 && px <= cx - 19
                            && Math.abs(pz - cz) <= 9 && y >= 28;
                    if (!stairShell && (y > LabyrinthLevels.CAVE_ROOF_Y || feet > bottom + 5))
                        put(plan, new BlockPos(px, y, pz), rock(seed, px, y, pz), 0);
                }
                if (distance > 2) continue;
                BlockState floor = previousFeet > feet && i > 0 && i < steps
                        ? (dark ? Asterion.SHADED_SHALE_STAIRS : Asterion.SHALE_STAIRS).defaultBlockState()
                                .setValue(StairBlock.FACING, uphill)
                        : rock;
                put(plan, new BlockPos(px, feet - 1, pz), floor, 3);
                for (int y = feet; y <= ceiling; y++) put(plan, new BlockPos(px, y, pz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
         
         
        for (int depth = 0; depth <= 8; depth++) for (int side = -3; side <= 3; side++) {
            int x = mouthX + depth, z = cz + side;
            if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX()
                    || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ()) continue;
            int upperFeet = top - Math.max(0, (8 - depth) / 2);
            for (int feet : new int[]{upperFeet, bottom}) {
                if (feet == bottom && depth > 7) continue;
                if (Math.abs(side) > 1) {
                    if (feet == upperFeet && x < cx - 28)
                        for (int y = feet - 2; y <= feet + 5 - Math.abs(side); y++)
                            put(plan, new BlockPos(x, y, z), rock(seed, x, y, z), 0);
                    continue;
                }
                put(plan, new BlockPos(x, feet - 1, z), rock(seed, x, feet - 1, z), 4);
                for (int y = feet; y < feet + 4; y++) put(plan, new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 4);
                if (feet == upperFeet && x < cx - 28)
                    put(plan, new BlockPos(x, feet + 4, z), rock(seed, x, feet + 4, z), 0);
            }
        }
        plan.forEach((pos, cell) -> level.setBlock(pos, cell.state, 18));
        placeLift(level, chunk, seed, cx - 39, cz, bottom, spiralTop);
    }

    private static void placeLift(ServerLevelAccessor level, ChunkPos chunk, long seed, int cx, int cz, int bottom, int top) {
        for (int x = cx - 3; x <= cx + 7; x++) for (int z = cz - 3; z <= cz + 3; z++) {
            if (x < chunk.getMinBlockX() || x > chunk.getMaxBlockX() || z < chunk.getMinBlockZ() || z > chunk.getMaxBlockZ()) continue;
            int dx = x - cx, dz = z - cz;
            for (int y = bottom - 1; y <= top + 3; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (Math.abs(dx) <= 3) {
                    int radius = 2 + (int)(CatacombLayout.hash(seed + y / 3, x, z) & 1);
                    boolean interior = Math.abs(dx) <= radius && Math.abs(dz) <= radius && y >= bottom && y < top + 3;
                    level.setBlock(pos, interior ? Blocks.AIR.defaultBlockState() : rock(seed, x, y, z), 18);
                }
                 
                if (dx >= 2 && Math.abs(dz) <= 1) {
                    if (y == bottom - 1 || y == top - 1) level.setBlock(pos, rock(seed, x, y, z), 18);
                    else if (y >= bottom && y <= bottom + 2 || y >= top && y <= top + 2)
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                }
            }
        }
        BlockPos anchor = new BlockPos(cx, bottom - 1, cz);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos support = anchor.offset(dx, 0, dz);
            if (chunk.getMinBlockX() <= support.getX() && support.getX() <= chunk.getMaxBlockX()
                    && chunk.getMinBlockZ() <= support.getZ() && support.getZ() <= chunk.getMaxBlockZ())
                level.setBlock(support, Asterion.SHADED_SHALE_SLAB.defaultBlockState(), 18);
        }
        if (chunk.getMinBlockX() <= cx && cx <= chunk.getMaxBlockX() && chunk.getMinBlockZ() <= cz && cz <= chunk.getMaxBlockZ())
            level.setBlock(anchor, net.krodark.asterion.game.ChainLiftContent.ANCHOR.defaultBlockState(), 18);
    }

    private static BlockState rock(long seed, int x, int y, int z) {
        return (ShaleCaves.shaded(seed, x, y, z) ? Asterion.SHADED_SHALE : Asterion.SHALE).defaultBlockState();
    }

    private static void put(Map<BlockPos, Cell> plan, BlockPos pos, BlockState state, int priority) {
        plan.merge(pos, new Cell(state, priority), (old, next) -> old.priority > next.priority ? old : next);
    }

    private static List<BlockPos> loop() {
        List<BlockPos> path = new ArrayList<>();
        int x = 7, z = 0;
        path.add(new BlockPos(x, 0, z));
        for (int[] target : new int[][]{{7,-4},{4,-7},{-4,-7},{-7,-4},{-7,4},{-4,7},{4,7},{7,4},{7,0}}) {
            while (x != target[0] || z != target[1]) {
                if (x != target[0]) { x += Integer.signum(target[0] - x); path.add(new BlockPos(x, 0, z)); }
                if (z != target[1]) { z += Integer.signum(target[1] - z); path.add(new BlockPos(x, 0, z)); }
            }
        }
        path.removeLast();
        return List.copyOf(path);
    }
}
