package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.GreekRune;
import net.krodark.asterion.block.RuneBlock;
import net.krodark.asterion.block.RuneBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.chunk.LevelChunk;

public final class ZoneRunePlacement {
    private static final java.util.Map<ServerLevel, java.util.ArrayDeque<net.minecraft.world.level.ChunkPos>> PENDING = new java.util.IdentityHashMap<>();
    private ZoneRunePlacement() { }
    private static final java.util.List<String> MAZE_FEATURES = java.util.List.of(
            "ancient_moss_patch", "giant_dead_tree", "ancient_leaves_cluster",
            "overgrowth_bridge", "overgrowth_rest_site", "overgrowth_puddle",
            "overgrowth_bridge_chains", "ancient_ground_vines", "ancient_hanging_vines",
            "tainted_petals");
    public static void enqueue(ServerLevel level, LevelChunk chunk) {
        PENDING.computeIfAbsent(level, ignored -> new java.util.ArrayDeque<>()).add(chunk.getPos());
    }
    public static void tick(ServerLevel level) {
        var queue = PENDING.get(level);
        if (queue == null) return;
        for (int i = 0; i < 2 && !queue.isEmpty(); i++) {
            var pos = queue.removeFirst();
            var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) {
                AuthoredCatacombs.placeArenaChunk(level,chunk);
                placeDeferredWorldgen(level,chunk);
                decorate(level,chunk);
            }
        }
        if (queue.isEmpty()) PENDING.remove(level);
    }
    public static void clear() { PENDING.clear(); }
    private static void placeDeferredWorldgen(ServerLevel level, LevelChunk chunk) {
        var cp = chunk.getPos();
        if (cp.x() >= -4 && cp.x() <= 3 && cp.z() >= -4 && cp.z() <= 3) return;
        BlockPos marker = new BlockPos(cp.getMinBlockX(), 0, cp.getMinBlockZ());
        if (chunk.getBlockState(marker).is(Blocks.STRUCTURE_VOID)) return;
        AuthoredCatacombs.place(level, cp);
        var registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        int index = 0;
        for (String name : MAZE_FEATURES) {
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, Asterion.id(name));
            var feature = registry.get(key);
            if (feature.isEmpty()) {
                Asterion.LOGGER.warn("Missing maze placed feature {}", key.identifier());
                continue;
            }
            long salt = level.getSeed() ^ ChunkPos.pack(cp.x(), cp.z())
                    ^ (long) ++index * 0x9E3779B97F4A7C15L;
            feature.get().value().place(level, level.getChunkSource().getGenerator(),
                    RandomSource.create(salt), new BlockPos(cp.getMinBlockX(), 50, cp.getMinBlockZ()));
        }
        chunk.setBlockState(marker, Blocks.STRUCTURE_VOID.defaultBlockState(), 0);
        chunk.markUnsaved();
    }
    public static void decorate(ServerLevel level, LevelChunk chunk) {
        var cp = chunk.getPos();
        if (Math.floorMod(cp.x() * 31L + cp.z() * 17L + level.getSeed(), 7) != 0
                || Math.abs(cp.getMiddleBlockX()) < 80 && Math.abs(cp.getMiddleBlockZ()) < 80) return;
        for (int x = 3; x < 13; x++) for (int z = 3; z < 13; z++) for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int y = 50; y <= 57; y++) {
                BlockPos root = new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                if (!fits(level, root, facing)) continue;
                var block = Asterion.RUNE_BLOCKS[GreekRune.forRadius(root.getX(), root.getZ()).ordinal()];
                block.place(level, root, facing);
                if (level.getBlockEntity(root) instanceof RuneBlockEntity rune) rune.setWorldGenerated(true);
                return;
            }
        }
    }
    private static boolean fits(ServerLevel level, BlockPos root, Direction facing) {
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) {
            var pos = RuneBlock.part(root, facing, x, y);
            var backing = pos.relative(facing.getOpposite());
            var support = level.getBlockState(backing);
            if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.relative(facing)).isAir()
                    || !(support.is(Asterion.ANCIENT_BRICKS) || support.is(Asterion.ANCIENT_STONE)
                    || support.is(Asterion.ANCIENT_MOSSY_BRICKS) || support.is(Asterion.MOSSY_ANCIENT_STONE))) return false;
        }
        return true;
    }
}
