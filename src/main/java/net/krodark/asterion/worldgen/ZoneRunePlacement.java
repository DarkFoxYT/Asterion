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
    private static final java.util.Map<ServerLevel, java.util.LinkedHashSet<net.minecraft.world.level.ChunkPos>> PENDING = new java.util.IdentityHashMap<>();
    private static final java.util.Map<ServerLevel, java.util.ArrayDeque<ChunkPos>> ARENA_PENDING =
            new java.util.IdentityHashMap<>();
    private static final java.util.Map<ServerLevel, java.util.ArrayDeque<ChunkPos>> BRAZIER_ROOM_PENDING =
            new java.util.IdentityHashMap<>();
    private static final java.util.List<ChunkPos> ARENA_CHUNKS = createArenaChunks();
    private ZoneRunePlacement() { }
    private static final java.util.List<String> MAZE_FEATURES = java.util.List.of(
            "ancient_moss_patch", "giant_dead_tree", "ancient_leaves_cluster",
            "overgrowth_bridge", "overgrowth_rest_site", "overgrowth_puddle",
            "overgrowth_bridge_chains", "ancient_ground_vines", "ancient_hanging_vines",
            "tainted_petals");
    public static void enqueue(ServerLevel level, LevelChunk chunk) {
        PENDING.computeIfAbsent(level, ignored -> new java.util.LinkedHashSet<>()).add(chunk.getPos());
    }
    public static java.util.List<ChunkPos> arenaChunks() { return ARENA_CHUNKS; }
    public static void enqueueArena(ServerLevel level) {
        var queue = ARENA_PENDING.computeIfAbsent(level, ignored -> new java.util.ArrayDeque<>());
        if (queue.isEmpty()) queue.addAll(ARENA_CHUNKS);
    }
    public static void enqueueCursedBrazierRoom(ServerLevel level) {
        for (int roomIndex = 0; roomIndex < AuthoredCatacombs.BRAZIER_ROOM_ORIGINS.size(); roomIndex++)
            enqueueCursedBrazierRoom(level, roomIndex);
    }
    public static void enqueueCursedBrazierRoom(ServerLevel level, int roomIndex) {
        int normalizedRoom = Math.clamp(roomIndex, 0, AuthoredCatacombs.BRAZIER_ROOM_ORIGINS.size() - 1);
        BlockPos origin = AuthoredCatacombs.BRAZIER_ROOM_ORIGINS.get(normalizedRoom);
        var queue = BRAZIER_ROOM_PENDING.computeIfAbsent(level, ignored -> new java.util.ArrayDeque<>());
        var queued = new java.util.HashSet<>(queue);
        int hallZ = CatacombLayout.BRAZIER_ROOM_MIN_ZS.get(normalizedRoom) + 1;
        int spineMinChunkX = (CatacombLayout.ROOT_X * CatacombLayout.TILE) >> 4;
        int spineMaxChunkX = (CatacombLayout.ROOT_X * CatacombLayout.TILE + CatacombLayout.TILE - 1) >> 4;
        int spineMinChunkZ = (CatacombLayout.ROOT_Z * CatacombLayout.TILE) >> 4;
        int spineMaxChunkZ = (hallZ * CatacombLayout.TILE + CatacombLayout.TILE - 1) >> 4;
        for (int x=spineMinChunkX;x<=spineMaxChunkX;x++) for (int z=spineMinChunkZ;z<=spineMaxChunkZ;z++) {
            ChunkPos chunk = new ChunkPos(x,z);
            if (queued.add(chunk)) queue.add(chunk);
        }
        // Include every chunk crossed by this room's axis-aligned crypt-module hall so
        // existing worlds receive the same clean connection as newly generated worlds.
        int hallMinChunkX = (CatacombLayout.ROOT_X * CatacombLayout.TILE) >> 4;
        int hallMaxChunkX = (CatacombLayout.BRAZIER_ROOM_MIN_X * CatacombLayout.TILE - 1) >> 4;
        int hallMinChunkZ = (hallZ * CatacombLayout.TILE) >> 4;
        int hallMaxChunkZ = (hallZ * CatacombLayout.TILE + CatacombLayout.TILE - 1) >> 4;
        for (int x=hallMinChunkX;x<=hallMaxChunkX;x++) for (int z=hallMinChunkZ;z<=hallMaxChunkZ;z++) {
            ChunkPos chunk = new ChunkPos(x,z);
            if (queued.add(chunk)) queue.add(chunk);
        }
        int minX=origin.getX()>>4;
        int maxX=(origin.getX()+49)>>4;
        int minZ=origin.getZ()>>4;
        int maxZ=(origin.getZ()+49)>>4;
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++) {
            ChunkPos chunk = new ChunkPos(x,z);
            if (queued.add(chunk)) queue.add(chunk);
        }
    }
    public static void tick(ServerLevel level) {
        var arena = ARENA_PENDING.get(level);
        // Finish the arena before a newly connected player can reach its keyed door.
        // Eight chunks per tick completes the authored footprint in roughly half a second.
        for (int i = 0; i < 8 && arena != null && !arena.isEmpty(); i++) {
            ChunkPos pos = arena.removeFirst();
            // This runs from the ordinary server tick, after chunk scheduling startup,
            // so requesting FULL here cannot wait on the callback currently executing.
            AuthoredCatacombs.placeArenaChunk(level, level.getChunk(pos.x(), pos.z()));
        }
        if (arena != null && arena.isEmpty()) ARENA_PENDING.remove(level);

        var room=BRAZIER_ROOM_PENDING.get(level);
        for(int i=0;i<2&&room!=null&&!room.isEmpty();i++) {
            ChunkPos pos=room.removeFirst();
            level.getChunk(pos.x(),pos.z());
            if(!AuthoredCatacombs.cursedBrazierRoomChunkReady(level,pos))
                AuthoredCatacombs.placeCursedBrazierRoomChunk(level,pos);
        }
        if(room!=null&&room.isEmpty())BRAZIER_ROOM_PENDING.remove(level);

        var queue = PENDING.get(level);
        if (queue == null) return;
        for (int i = 0; i < 2 && !queue.isEmpty(); i++) {
            var iterator = queue.iterator();
            var pos = iterator.next();
            iterator.remove();
            var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) {
                AuthoredCatacombs.placeArenaChunk(level,chunk);
                boolean newlyGenerated = placeDeferredWorldgen(level,chunk);
                decorate(level,chunk,newlyGenerated);
            }
        }
        if (queue.isEmpty()) PENDING.remove(level);
    }
    public static void clear() { PENDING.clear(); ARENA_PENDING.clear(); BRAZIER_ROOM_PENDING.clear(); }
    private static java.util.List<ChunkPos> createArenaChunks() {
        java.util.LinkedHashSet<ChunkPos> chunks = new java.util.LinkedHashSet<>();
        for (int x = -4; x <= 3; x++) for (int z = -4; z <= 3; z++)
            chunks.add(new ChunkPos(x, z));
        // Include the retired approach footprint once so existing saves have the old
        // hand-carved hall sealed and replaced by ordinary authored modules.
        for (int x = -1; x <= 5; x++) for (int z = 3; z <= 5; z++)
            chunks.add(new ChunkPos(x, z));
        return java.util.List.copyOf(chunks);
    }
    private static boolean placeDeferredWorldgen(ServerLevel level, LevelChunk chunk) {
        var cp = chunk.getPos();
        if (cp.x() >= -4 && cp.x() <= 3 && cp.z() >= -4 && cp.z() <= 3) return false;
        BlockPos marker = new BlockPos(cp.getMinBlockX(), 0, cp.getMinBlockZ());
        BlockPos linkMarker = new BlockPos(cp.getMinBlockX() + 1, 0, cp.getMinBlockZ());
        var linkedRevision = Blocks.LIGHT.defaultBlockState().setValue(
                net.minecraft.world.level.block.LightBlock.LEVEL, 2);
        if (chunk.getBlockState(marker).is(Blocks.STRUCTURE_VOID)) {
            if (!chunk.getBlockState(linkMarker).equals(linkedRevision)) {
                AuthoredCatacombs.retrofitWovenConnections(level, chunk);
                chunk.setBlockState(linkMarker, linkedRevision, 0);
                chunk.markUnsaved();
            }
            return false;
        }
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
        chunk.setBlockState(linkMarker, linkedRevision, 0);
        chunk.markUnsaved();
        return true;
    }
    public static void decorate(ServerLevel level, LevelChunk chunk) { decorate(level, chunk, false); }
    private static void decorate(ServerLevel level, LevelChunk chunk, boolean newlyGenerated) {
        var cp = chunk.getPos();
        BlockPos decorationMarker = new BlockPos(cp.getMinBlockX() + 2, 0, cp.getMinBlockZ());
        var decorated = Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 3);
        if (chunk.getBlockState(decorationMarker).equals(decorated)) return;
        // A pre-fix chunk has already had its decoration pass. Mark it without placing
        // another rune when it is loaded again after a relog.
        if (!newlyGenerated && chunk.getBlockState(new BlockPos(cp.getMinBlockX(), 0, cp.getMinBlockZ()))
                .is(Blocks.STRUCTURE_VOID)) {
            chunk.setBlockState(decorationMarker, decorated, 0);
            chunk.markUnsaved();
            return;
        }
        if (Math.floorMod(cp.x() * 31L + cp.z() * 17L + level.getSeed(), 7) != 0
                || Math.abs(cp.getMiddleBlockX()) < 80 && Math.abs(cp.getMiddleBlockZ()) < 80) {
            chunk.setBlockState(decorationMarker, decorated, 0);
            chunk.markUnsaved();
            return;
        }
        for (int x = 3; x < 13; x++) for (int z = 3; z < 13; z++) for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int y = 50; y <= 57; y++) {
                BlockPos root = new BlockPos(cp.getMinBlockX() + x, y, cp.getMinBlockZ() + z);
                if (!fits(level, root, facing)) continue;
                var block = Asterion.RUNE_BLOCKS[GreekRune.forRadius(root.getX(), root.getZ()).ordinal()];
                block.place(level, root, facing);
                if (level.getBlockEntity(root) instanceof RuneBlockEntity rune) rune.setWorldGenerated(true);
                chunk.setBlockState(decorationMarker, decorated, 0);
                chunk.markUnsaved();
                return;
            }
        }
        chunk.setBlockState(decorationMarker, decorated, 0);
        chunk.markUnsaved();
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
