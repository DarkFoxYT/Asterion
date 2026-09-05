package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.phys.AABB;

final class ShaleCavesCheck {
    static void run(ServerLevel level) {
        check(level.getMinY() == -64 && level.getMaxY() == 303, "Caves moved the upper world boundary");
        long seed = MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
        var ores = new java.util.HashSet<net.minecraft.world.level.block.Block>();
        int water = 0, stairs = 0, slabs = 0, spikes = 0, air = 0;
        BlockPos spawn = null;
        for (int cx = 0; cx < 4; cx++) for (int cz = 0; cz < 4; cz++) {
            var chunk = level.getChunk(cx, cz);
            for (BlockPos pos : BlockPos.betweenClosed(cx * 16, -61, cz * 16, cx * 16 + 15, 13, cz * 16 + 15)) {
                var state = chunk.getBlockState(pos);
                if (CatacombProtection.isOre(state)) ores.add(state.getBlock());
                if (state.is(Blocks.WATER)) water++;
                if (state.getBlock() instanceof StairBlock) stairs++;
                if (state.getBlock() instanceof SlabBlock) slabs++;
                if (state.getBlock() instanceof WallBlock) spikes++;
                if (state.isAir()) air++;
                if (spawn == null && state.isAir() && level.getBlockState(pos.above()).isAir()
                        && level.getBlockState(pos.below()).is(Asterion.SHADED_SHALE)) spawn = pos.immutable();
            }
        }
        check(ores.containsAll(java.util.List.of(Asterion.CELESTIAL_BRONZE_ORE, Asterion.TARNISHED_GOLD_ORE,
                Asterion.CELESTIAL_GOLD_ORE, Asterion.SHALE_CELESTIAL_GOLD_ORE, Asterion.SHALE_TARNISHED_GOLD_ORE,
                Asterion.SHADED_SHALE_CELESTIAL_GOLD_ORE, Asterion.SHADED_SHALE_TARNISHED_GOLD_ORE)), "Missing cave ores: " + ores);
        check(air > 5000 && water > 0 && stairs > 50 && slabs > 50 && spikes > 0,
                "Missing cave detail: " + air + "/" + water + "/" + stairs + "/" + slabs + "/" + spikes);
        check(spawn != null, "No supported cave floor");
        var centipede = Asterion.SCARLET_CENTIPEDE.create(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL);
        centipede.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
        check(centipede.checkSpawnRules(level, net.minecraft.world.entity.EntitySpawnReason.NATURAL), "Cave centipede spawn rejected");
        level.addFreshEntity(centipede);
        check(!centipede.isRemoved(), "Underground centipede was deleted on load");
        centipede.discard();

        // A remote Forge must actually be placed, with a usable west socket and stair route.
        int center = CatacombLayout.ROOT_CENTER + 4 * 570;
        for (int cx = (center - 90) >> 4; cx <= (center + 20) >> 4; cx++)
            for (int cz = (center - 14) >> 4; cz <= (center + 14) >> 4; cz++) level.getChunk(cx, cz);
        int crucibles = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center - 18, 16, center - 12, center + 18, 45, center + 12))
            if (level.getBlockState(pos).is(Asterion.CRUCIBLE)) crucibles++;
        check(crucibles > 0, "Remote Forge did not generate");
        BlockPos door = new BlockPos(center - 18, 29, center);
        check(clear(level, door), "West Forge socket is blocked");
        check(route(level, new BlockPos(center - 57, 72, center), door, center), "Catacomb stairs do not reach the Forge socket");
        int shaftX = Math.floorDiv(center - 24, 64) * 64;
        int bottom = ShaleCaves.floorY(seed, shaftX, center) + 1;
        check(route(level, door, new BlockPos(shaftX, bottom, center), center), "Forge does not reach its cave landing");
        Asterion.LOGGER.info("PASS: shale caves, all seven ores, water, slabs/stairs/spikes, centipedes and remote Forge-to-catacomb-to-cave routes");
    }

    private static boolean route(ServerLevel level, BlockPos start, BlockPos target, int center) {
        var queue = new java.util.ArrayDeque<BlockPos>();
        var seen = new java.util.HashSet<BlockPos>();
        queue.add(start); seen.add(start);
        while (!queue.isEmpty()) {
            var pos = queue.removeFirst();
            if (pos.equals(target)) return true;
            for (Direction side : Direction.Plane.HORIZONTAL) for (int dy = -1; dy <= 1; dy++) {
                BlockPos next = pos.relative(side).above(dy);
                if (Math.abs(next.getZ() - center) > 12 || next.getX() < center - 100 || next.getX() > center - 16
                        || next.getY() < -60 || next.getY() > 74 || seen.contains(next) || !clear(level, next)) continue;
                if (level.getBlockState(next.below()).getCollisionShape(level, next.below()).isEmpty()
                        && !level.getBlockState(next).is(Blocks.LADDER)) continue;
                seen.add(next); queue.addLast(next);
            }
            if (level.getBlockState(pos).is(Blocks.LADDER)) for (int dy : new int[]{-1, 1}) {
                BlockPos next = pos.above(dy);
                if (clear(level, next) && seen.add(next)) queue.addLast(next);
            }
        }
        return false;
    }

    private static boolean clear(ServerLevel level, BlockPos pos) {
        return level.noCollision(new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2,
                pos.getX() + .8, pos.getY() + 1.8, pos.getZ() + .8));
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
