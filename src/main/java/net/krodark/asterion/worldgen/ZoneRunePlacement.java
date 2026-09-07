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
    private static final java.util.Map<ServerLevel, java.util.Set<ChunkPos>> PREPARING = new java.util.IdentityHashMap<>();
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
        var queued = new java.util.HashSet<>(queue);
        for (ChunkPos pos : ARENA_CHUNKS) if (!queued.contains(pos)) queue.addLast(pos);
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
    private static final java.util.Map<ServerLevel, java.util.Map<ChunkPos, int[]>> GENERATING = new java.util.IdentityHashMap<>();

    private static BlockPos catacombsMarker(ChunkPos pos) {
        return new BlockPos(pos.getMinBlockX() + 3, 0, pos.getMinBlockZ());
    }

    public static void markCatacombsPlaced(net.minecraft.world.level.chunk.ChunkAccess chunk) {
        chunk.setBlockState(catacombsMarker(chunk.getPos()), Blocks.LIGHT.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 0), 0);
        chunk.markUnsaved();
    }

    public static void tick(ServerLevel level) {
        long deadline = System.nanoTime() + 4_000_000L;
        if (!level.players().isEmpty()) prepareStructures(level);
        else {
            if (PREPARING.containsKey(level) && ARENA_PENDING.containsKey(level)) {
                var arena = ARENA_PENDING.get(level);
                arena.clear(); arena.addAll(ARENA_CHUNKS);
            }
            releasePreparationTickets(level);
        }

        var queue = PENDING.get(level);
        if (queue == null) return;
        for (int i = 0; i < 2 && !queue.isEmpty() && System.nanoTime() < deadline; i++) {
            var iterator = queue.iterator();
            var pos = iterator.next();
            iterator.remove();
            var chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) {
                AuthoredCatacombs.placeArenaChunk(level,chunk);
                Boolean newlyGenerated = placeDeferredWorldgen(level, chunk);
                if (newlyGenerated == null) queue.add(pos);
                else decorate(level, chunk, newlyGenerated);
            } else {
                var progress = GENERATING.get(level);
                if (progress != null) {
                    progress.remove(pos);
                    if (progress.isEmpty()) GENERATING.remove(level);
                }
            }
        }
        if (queue.isEmpty()) PENDING.remove(level);
    }

    private static void prepareStructures(ServerLevel level) {
        boolean prepareArena = BossArenaEncounter.isIntroCinematic(level)
                || level.players().stream().anyMatch(player ->
                        Math.abs(player.getX()) <= 192 && Math.abs(player.getZ()) <= 192);
        boolean prepareRooms = level.players().stream().anyMatch(player ->
                AuthoredCatacombs.BRAZIER_ROOM_ORIGINS.stream().anyMatch(origin ->
                        Math.abs(player.getX() - origin.getX()) <= 96
                                && Math.abs(player.getZ() - origin.getZ()) <= 96));
        if (!prepareArena && !prepareRooms) releasePreparationTickets(level);
        var arena = ARENA_PENDING.get(level);
        LevelChunk chunk = nextReadyChunk(level, arena, prepareArena);
        if (chunk != null) {
            AuthoredCatacombs.placeArenaChunk(level, chunk);
            return;
        }
        if (arena != null && arena.isEmpty()) {
            ARENA_PENDING.remove(level);
            if (!prepareRooms) releasePreparationTickets(level);
        }
        var rooms = BRAZIER_ROOM_PENDING.get(level);
        chunk = nextReadyChunk(level, rooms, prepareRooms);
        if (chunk != null) {
            if (!AuthoredCatacombs.cursedBrazierRoomChunkReady(level, chunk.getPos()))
                AuthoredCatacombs.placeCursedBrazierRoomChunk(level, chunk.getPos());
            return;
        }
        if (rooms != null && rooms.isEmpty()) {
            BRAZIER_ROOM_PENDING.remove(level);
            if (arena == null || arena.isEmpty()) releasePreparationTickets(level);
        }
    }

    private static LevelChunk nextReadyChunk(ServerLevel level, java.util.ArrayDeque<ChunkPos> queue, boolean cinematic) {
        if (queue == null) return null;
        int remaining = queue.size();
        while (remaining-- > 0) {
            ChunkPos pos = queue.removeFirst();
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) return chunk;
            queue.addLast(pos);
            if (cinematic && remaining == 0) {
                PREPARING.computeIfAbsent(level, ignored -> new java.util.HashSet<>()).add(pos);
                level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL, pos, 0);
            }
        }
        return null;
    }
    private static void releasePreparationTickets(ServerLevel level) {
        var held = PREPARING.remove(level);
        if (held != null) for (ChunkPos pos : held)
            level.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL, pos, 0);
    }
    public static void clear() {
        for (ServerLevel level : java.util.List.copyOf(PREPARING.keySet())) releasePreparationTickets(level);
        PENDING.clear(); ARENA_PENDING.clear(); BRAZIER_ROOM_PENDING.clear(); GENERATING.clear();
    }
    private static java.util.List<ChunkPos> createArenaChunks() {
        java.util.LinkedHashSet<ChunkPos> chunks = new java.util.LinkedHashSet<>();
        for (int x = -4; x <= 3; x++) for (int z = -4; z <= 3; z++)
            chunks.add(new ChunkPos(x, z));
         
         
        for (int x = -1; x <= 5; x++) for (int z = 3; z <= 5; z++)
            chunks.add(new ChunkPos(x, z));
        return java.util.List.copyOf(chunks);
    }
    private static Boolean placeDeferredWorldgen(ServerLevel level, LevelChunk chunk) {
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
        var progress = GENERATING.computeIfAbsent(level, ignored -> new java.util.HashMap<>());
        int[] stage = progress.get(cp);
        if (stage == null) {
            if (!chunk.getBlockState(catacombsMarker(cp)).equals(Blocks.LIGHT.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 0))) {
                AuthoredCatacombs.place(level, cp);
                markCatacombsPlaced(chunk);
            }
            progress.put(cp, new int[2]);
            return null;
        }
        var registry = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE);
        int end = Math.min(MAZE_FEATURES.size(), stage[0] + 1);
        while (stage[0] < end) {
            String name = MAZE_FEATURES.get(stage[0]++);
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, Asterion.id(name));
            var feature = registry.get(key);
            if (feature.isEmpty()) {
                Asterion.LOGGER.warn("Missing maze placed feature {}", key.identifier());
                continue;
            }
            long salt = level.getSeed() ^ ChunkPos.pack(cp.x(), cp.z())
                    ^ (long) ++stage[1] * 0x9E3779B97F4A7C15L;
            feature.get().value().place(level, level.getChunkSource().getGenerator(),
                    RandomSource.create(salt), new BlockPos(cp.getMinBlockX(), 50, cp.getMinBlockZ()));
        }
        if (stage[0] < MAZE_FEATURES.size()) return null;
        progress.remove(cp);
        if (progress.isEmpty()) GENERATING.remove(level);
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
