package net.krodark.asterion;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.network.DimensionTransitionPayload;
import net.krodark.asterion.network.EntryOmenPayload;
import net.krodark.asterion.network.BossFinalePayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.GatewayPortalPayload;
import net.krodark.asterion.network.MazeZapPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.BiomeAtmospherePayload;
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.krodark.asterion.network.ragdoll.RagdollExplosionPayload;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.entity.BombadierBeetleEntity;
import net.krodark.asterion.worldgen.MazeNbtStructures;
import net.krodark.asterion.worldgen.MazeBiomes;
import net.krodark.asterion.worldgen.MazeChunkData;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.krodark.asterion.block.MinotaurDoorBlockEntity;
import net.krodark.asterion.block.RuneDoorBlock;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGenerator {
    private static final boolean ENABLE_MAZE_NBT_STRUCTURES = true;
    private static final int FLOOR_Y = 48;
    private static final int BOSS_FLOOR_Y = net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_FLOOR_Y;
    // The dimension stores 304 blocks (whole chunk sections); chains end at Y=300.
    private static final int DIMENSION_CEILING_Y = 300;
    private static final int PIT_HALF_WIDTH = 42;
    private static final int PIT_WALL_THICKNESS = 6;
    private static final int SKYFALL_CLEARANCE = 42;
    private static final int PORTAL_FADE_IN_TICKS = 16;
    private static final int PORTAL_BLACK_HOLD_TICKS = 4;
    private static final int GATEWAY_PORTAL_DEPTH = 9;
    private static final int SUMMONED_PORTAL_DEPTH = 8;
    private static final ResourceKey<LootTable> MAZE_BARREL_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Asterion.id("chests/maze_supply_barrel"));
    private static final ResourceKey<LootTable> SAFE_RUNE_NEAR_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Asterion.id("chests/safe_rune_near"));
    private static final ResourceKey<LootTable> SAFE_RUNE_MID_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Asterion.id("chests/safe_rune_mid"));
    private static final ResourceKey<LootTable> SAFE_RUNE_FAR_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Asterion.id("chests/safe_rune_far"));
    private static final int[][] PRELOAD_OFFSETS = {{0, 0}};
    private static final int[][] PREWARM_OFFSETS = {{0, 0}};
    private static final Map<Long, Integer> GATEWAY_SURFACE_Y = new ConcurrentHashMap<>();
    private static final Map<MazeKey, MazeTopology> MAZE_TOPOLOGIES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingTransition> PENDING_TRANSITIONS = new HashMap<>();
    private static final Map<UUID, Optional<ServerPlayer.RespawnConfig>> PRE_MAZE_RESPAWNS = new HashMap<>();
    private static final Map<UUID, Long> LAST_PORTAL_SYNC = new HashMap<>();
    private static final Map<UUID, Integer> LAST_BIOME_ATMOSPHERE = new HashMap<>();
    private static final Map<UUID, PhasingEntity> PHASING_ENTITIES = new HashMap<>();
    private static SummonedPortal summonedPortal;
    private static final Map<UUID, Integer> ABOVE_WALL_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> WARD_FALL_PROTECTION = new HashMap<>();
    private static final Map<UUID, ElectrifiedState> ELECTRIFIED = new HashMap<>();
    private static final Map<UUID, Long> ROAMER_REVEAL_TICKS = new HashMap<>();
    private static final Set<UUID> BOSS_ENTRANTS = new HashSet<>();
    private static final Map<UUID, Long> BOSS_START_REQUESTS = new HashMap<>();
    private static final Map<UUID, Vec3> ARENA_PREVIOUS_POSITIONS = new HashMap<>();
    private static final java.util.Set<ServerPlayer> RESET_DEATHS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static boolean bossArenaPrepared;
    private static BossArenaBuild bossArenaBuild;
    private static BossFinale bossFinale;
    private static final BlockPos ARENA_EXIT_PORTAL = new BlockPos(0, 8, -60);
    private static long activeMazeTerrainSeed;
    private static final PriorityQueue<DecayingBlock> DECAYING_BLOCKS = new PriorityQueue<>(
            Comparator.comparingLong(DecayingBlock::dueTick));
    private static final PriorityQueue<RestoringBlock> RESTORING_BLOCKS = new PriorityQueue<>(
            Comparator.comparingLong(RestoringBlock::dueTick));
    private static final Map<BlockKey, Block> PLAYER_PLACED_BLOCKS = new HashMap<>();
    private static long prewarmSeed = Long.MIN_VALUE;
    private static int prewarmIndex;

    private WorldGenerator() {
    }

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean newlyGenerated) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            return;
        }
        initializeMazeTerrain(level);
        MazeChunkData.prepare(level, chunk);

        if (newlyGenerated) {
            MazeNbtStructures.markCopperClean(chunk);
            // CHUNK_LOAD runs before the chunk's FULL future completes. World reads here
            // can wait on that same future; decorate from the following server tick instead.
            net.krodark.asterion.worldgen.ZoneRunePlacement.enqueue(level, chunk);
        } else {
            MazeNbtStructures.cleanLegacyCopper(chunk,
                    BOSS_FLOOR_Y - AsterionConfig.INSTANCE.floorThickness,
                    FLOOR_Y + AsterionConfig.INSTANCE.wallHeight);
            net.krodark.asterion.worldgen.ZoneRunePlacement.enqueue(level,chunk);
        }

        if (ENABLE_MAZE_NBT_STRUCTURES) {
            MazeNbtStructures.Layout layout = mazeStructureLayout(level);
            layout.onChunkBuilt(chunk);
        }
        installMazeWallCores(level, chunk, !newlyGenerated);
        net.krodark.asterion.worldgen.GiantDeadTreeFeature.repairLegacyTrunkGaps(
                level, chunk, !newlyGenerated);
    }

    public static void onChunkGenerate(ServerLevel level, LevelChunk chunk) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        ChunkPos pos = chunk.getPos();
        BlockPos marker = new BlockPos(pos.getMinBlockX(), 1, pos.getMinBlockZ());
        if (!chunk.getBlockState(marker).is(Blocks.BEDROCK)) buildMazeChunk(level, chunk, marker);
        MazeNbtStructures.markCopperClean(chunk);
    }

    public static void tickServer(MinecraftServer server) {
        restoreSavedPortal(server);
        DeadSunEventSystem.tick(server);
        tickDecayingBlocks(server);
        tickRestoringBlocks(server);
        ServerLevel maze = server.getLevel(Asterion.ASTERION_LEVEL);
        if (maze != null) {
            tickMaze(maze);
        }
        server.getPlayerList().getPlayers().forEach(WorldGenerator::tickPlayer);
        if ((server.overworld().getGameTime() % 10L) == 0L && maze != null) {
            maze.players().forEach(WorldGenerator::tickRuneCheckpoint);
        }
        tickPhasingEntities(server);
        if ((server.overworld().getGameTime() % 20L) == 0L) {
            cleanupStaleState(server, maze);
        }
    }

    private static void restoreSavedPortal(MinecraftServer server) {
        if (summonedPortal != null) return;
        AsterionWorldState.SavedPortal saved = AsterionWorldState.get(server.overworld()).summonedPortal();
        if (saved != null)
            summonedPortal = new SummonedPortal(saved.dimension(), saved.center(),
                    saved.surfaceY(), saved.visualSeed());
    }

    private static void tickMaze(ServerLevel maze) {
        net.krodark.asterion.worldgen.ZoneRunePlacement.tick(maze);
        net.krodark.asterion.worldgen.AuthoredCatacombs.tickCursedBrazierRoom(maze);
        finishBossArenaBuildIfReady(maze);
        net.krodark.asterion.worldgen.MazeWildlife.tick(maze);
        net.krodark.asterion.worldgen.CatacombArena.tick(maze);
        if (ENABLE_MAZE_NBT_STRUCTURES) mazeStructureLayout(maze);
        tickBossFinale(maze);
        tickBossArenaDebris(maze);
        tickMinotaurDirector(maze);
        BossArenaEncounter.tick(maze);
        tickMazeEntities(maze);
        if (ENABLE_MAZE_NBT_STRUCTURES && PENDING_TRANSITIONS.isEmpty()) {
            MazeNbtStructures.tick(maze);
        }
    }

    private static MazeNbtStructures.Layout mazeStructureLayout(ServerLevel level) {
        MazeBiomes.load(level);
        AsterionConfig config = AsterionConfig.INSTANCE;
        return MazeNbtStructures.layout(level, config.mazeRadiusCells, config.cellSize,
                (minX, minZ, maxX, maxZ) -> {
                    int center = config.mazeRadiusCells;
                    return maxX < center - 6 || minX > center + 6
                            || maxZ < center - 6 || minZ > center + 6;
                });
    }

    private static void cleanupStaleState(MinecraftServer server, ServerLevel maze) {
        ABOVE_WALL_TICKS.keySet().removeIf(id -> maze == null || maze.getEntityInAnyDimension(id) == null);
        WARD_FALL_PROTECTION.keySet().removeIf(id -> maze == null || maze.getEntityInAnyDimension(id) == null);
        ELECTRIFIED.keySet().removeIf(id -> maze == null || maze.getEntityInAnyDimension(id) == null);
        PENDING_TRANSITIONS.entrySet().removeIf(entry -> {
            if (server.getPlayerList().getPlayer(entry.getKey()) != null) {
                return false;
            }
            PendingTransition pending = entry.getValue();
            pending.maze.getChunkSource().removeTicketWithRadius(
                    TicketType.PORTAL, pending.destinationChunk, 1);
            return true;
        });
        LAST_PORTAL_SYNC.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        LAST_BIOME_ATMOSPHERE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void tickRuneCheckpoint(ServerPlayer player) {
        ServerLevel level = (ServerLevel)player.level();
        BlockPos checkpoint = MazeNbtStructures.safeCheckpointNear(level, player.blockPosition(), 7.0D);
        if (checkpoint == null) checkpoint = net.krodark.asterion.worldgen.CatacombEntrances.checkpoint(level, player.blockPosition());
        if (checkpoint == null) return;
        BlockPos previous = AsterionWorldState.get(level).runeCheckpoint(player.getUUID());
        AsterionWorldState.get(level).setRuneCheckpoint(player.getUUID(), checkpoint);
        if (!checkpoint.equals(previous)) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, checkpoint.getX() + 0.5D,
                    checkpoint.getY() + 0.4D, checkpoint.getZ() + 0.5D,
                    28, 0.8D, 0.7D, 0.8D, 0.025D);
            level.playSound(null, checkpoint, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.2F, 0.72F);
        }
    }

    public static void respawnAtRune(ServerPlayer player) {
        respawnAtRune(player, player.blockPosition());
    }

    public static void respawnAtRune(ServerPlayer player, BlockPos deathPosition) {
        ServerLevel maze = player.level().getServer().getLevel(Asterion.ASTERION_LEVEL);
        if (maze == null) return;
        BlockPos checkpoint = findRespawnCheckpoint(maze, player.getUUID(), deathPosition);
        maze.getChunkAt(checkpoint);
        // Even a nearby respawn needs an acknowledged teleport packet. A server-only
        // setPos lets stale client movement push the replacement player into the floor.
        player.teleportTo(maze, checkpoint.getX() + 0.5D, checkpoint.getY() + 0.1D,
                checkpoint.getZ() + 0.5D, Set.of(), player.getYRot(), 0.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        WARD_FALL_PROTECTION.put(player.getUUID(), 100);
    }

    private static BlockPos findRespawnCheckpoint(ServerLevel maze, UUID playerId, BlockPos deathPosition) {
        BlockPos checkpoint = AsterionWorldState.get(maze).runeCheckpoint(playerId);
        if (checkpoint != null) {
            maze.getChunkAt(checkpoint);
            // The checkpoint is persisted independently from the runtime NBT-room cache.  After a
            // restart that cache may not have inspected the room yet, so rejecting the saved point
            // here silently sent players to a random cell instead of their activated rune.
            if (!isSafeRespawnPosition(maze, checkpoint)) checkpoint = null;
        }
        if (checkpoint == null)
            checkpoint = MazeNbtStructures.nearestSafeHouse(maze, deathPosition);
        if (checkpoint != null && !isSafeRespawnPosition(maze, checkpoint)) checkpoint = null;
        if (checkpoint == null) {
            checkpoint = randomMazeArrival(maze, playerId, 0L);
            prepareMazeArrival(maze, checkpoint);
        }
        return checkpoint;
    }

    /** Installs a one-use forced rune spawn after a real labyrinth death. This lets vanilla
     * create the replacement player in Asterion directly instead of loading the Overworld first. */
    public static void prepareRapidRespawn(ServerPlayer player) {
        if (!player.level().dimension().equals(Asterion.ASTERION_LEVEL)) return;
        ServerLevel maze = player.level();
        BlockPos checkpoint = findRespawnCheckpoint(maze, player.getUUID(), player.blockPosition());
        maze.getChunkAt(checkpoint);
        PRE_MAZE_RESPAWNS.put(player.getUUID(), Optional.ofNullable(player.getRespawnConfig()));
        LevelData.RespawnData data = LevelData.RespawnData.of(
                Asterion.ASTERION_LEVEL, checkpoint, player.getYRot(), 0.0F);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(data, true), false);
    }

    /** Restores the bed/world spawn that was temporarily replaced for the direct rune respawn. */
    public static void finishRapidRespawn(ServerPlayer player) {
        Optional<ServerPlayer.RespawnConfig> previous = PRE_MAZE_RESPAWNS.remove(player.getUUID());
        if (previous != null) player.setRespawnPosition(previous.orElse(null), false);
    }

    private static boolean isSafeRespawnPosition(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isCollisionShapeFullBlock(level, floor)
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && !level.getBlockState(feet).is(net.minecraft.tags.BlockTags.FIRE)
                && !level.getBlockState(floor).is(Blocks.MAGMA_BLOCK)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    /** Forces transient client portal state to be sent again after joining or reconnecting. */
    public static void playerConnected(ServerPlayer player) {
        LAST_PORTAL_SYNC.remove(player.getUUID());
    }

    public static boolean resetBossEncounterAfterDeath(ServerPlayer deadPlayer) {
        if (RESET_DEATHS.contains(deadPlayer)) return true;
        if (!(deadPlayer.level() instanceof ServerLevel maze)
                || !maze.dimension().equals(Asterion.ASTERION_LEVEL)
                || AsterionWorldState.get(maze).minotaurDefeated()) return false;
        boolean pitDeath = isInsideBossArena(deadPlayer.position())
                || (BOSS_ENTRANTS.contains(deadPlayer.getUUID()) && isBossEncounterActive(maze));
        if (!pitDeath) return false;
        if (!deadPlayer.isAlive()) RESET_DEATHS.add(deadPlayer);

        for (Entity entity : maze.getAllEntities()) {
            if (entity instanceof MinotaurEntity minotaur
                    && minotaur.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS)
                minotaur.discard();
        }
        BOSS_ENTRANTS.clear();
        bossFinale = null;
        BossArenaEncounter.finish(maze);
        // A party wipe rebuild must not restore pillars through surviving players.
        for (ServerPlayer survivor : java.util.List.copyOf(maze.players())) if (survivor != deadPlayer && survivor.isAlive()
                && isInsideBossArena(survivor.position())) {
            survivor.teleportTo(maze, .5, net.krodark.asterion.worldgen.AuthoredCatacombs.CONNECTOR_Y, 63.5, Set.of(), 180, 0, true);
            survivor.setDeltaMovement(Vec3.ZERO);
            survivor.resetFallDistance();
        }
        clearBossArenaTransientState(maze);
        rebuildBossArena(maze);
        ELECTRIFIED.remove(deadPlayer.getUUID());
        WARD_FALL_PROTECTION.remove(deadPlayer.getUUID());
        return true;
    }

    private static void clearBossArenaTransientState(ServerLevel level) {
        int outer = PIT_HALF_WIDTH + PIT_WALL_THICKNESS + 2;
        AABB arena = new AABB(-outer, BOSS_FLOOR_Y - 20, -outer,
                outer + 1, DIMENSION_CEILING_Y + 1, outer + 1);
        for (Entity entity : level.getAllEntities()) {
            if ((entity instanceof FallingBlockEntity
                    || entity instanceof net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball fireball
                    && (fireball.getOwner() instanceof MinotaurEntity
                    || fireball.entityTags().contains("asterion_catacomb_fireball"))) && arena.intersects(entity.getBoundingBox()))
                entity.discard();
        }
        DECAYING_BLOCKS.removeIf(entry -> entry.dimension.equals(level.dimension())
                && arena.contains(Vec3.atCenterOf(entry.pos)));
        RESTORING_BLOCKS.removeIf(entry -> entry.dimension.equals(level.dimension())
                && arena.contains(Vec3.atCenterOf(entry.pos)));
    }

    public static boolean isNearSafeRune(ServerLevel level, BlockPos center) {
        return MazeNbtStructures.safeCheckpointNear(level, center, 9.0D) != null
                || net.krodark.asterion.worldgen.CatacombEntrances.checkpoint(level, center) != null;
    }

    private static void generateNextPrewarmChunk(ServerLevel maze, BlockPos destination) {
        if (prewarmIndex >= PREWARM_OFFSETS.length) return;
        ChunkPos center = ChunkPos.containing(destination);
        int[] offset = PREWARM_OFFSETS[prewarmIndex++];
        maze.getChunk(center.x() + offset[0], center.z() + offset[1]);
    }

    private static int[][] createSpiralOffsets(int radius) {
        int diameter = radius * 2 + 1;
        int[][] offsets = new int[diameter * diameter][2];
        int index = 0;
        offsets[index++] = new int[]{0, 0};
        for (int ring = 1; ring <= radius; ring++) {
            for (int x = -ring; x <= ring; x++) offsets[index++] = new int[]{x, -ring};
            for (int z = -ring + 1; z <= ring; z++) offsets[index++] = new int[]{ring, z};
            for (int x = ring - 1; x >= -ring; x--) offsets[index++] = new int[]{x, ring};
            for (int z = ring - 1; z > -ring; z--) offsets[index++] = new int[]{-ring, z};
        }
        return offsets;
    }

    public static void trackPlayerPlacement(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        PLAYER_PLACED_BLOCKS.put(new BlockKey(level.dimension(), pos.immutable()), state.getBlock());
        // Surface-maze construction is permanent. Keep provenance so maze attacks can
        // distinguish it from generated masonry, but never enroll it in decay.
        if (isPermanentUpperMazeBuild(pos)) return;
        int decayTicks = net.krodark.asterion.worldgen.CatacombProtection.contains(level, pos)
                ? 20 : AsterionConfig.INSTANCE.playerBlockDecayTicks;
        DECAYING_BLOCKS.add(new DecayingBlock(level.dimension(), pos.immutable(), state.getBlock(),
                level.getGameTime() + decayTicks));
    }

    public static void trackMazeBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL) || state.isAir()) return;
        BlockKey key = new BlockKey(level.dimension(), pos.immutable());
        Block placed = PLAYER_PLACED_BLOCKS.get(key);
        if (placed == state.getBlock()) {
            PLAYER_PLACED_BLOCKS.remove(key);
            return;
        }
        RESTORING_BLOCKS.removeIf(entry -> entry.dimension.equals(level.dimension()) && entry.pos.equals(pos));
        if (!shouldRestoreMazeBreak(level, pos)) return;
        long restoreDelay = net.krodark.asterion.worldgen.CatacombProtection.contains(level, pos)
                ? 20L : 100L;
        RESTORING_BLOCKS.add(new RestoringBlock(level.dimension(), pos.immutable(), state,
                level.getGameTime() + restoreDelay));
    }

    private static boolean shouldRestoreMazeBreak(ServerLevel level, BlockPos pos) {
        long seed = net.krodark.asterion.worldgen.MazeChunkGenerator
                .terrainSeed(level.getChunkSource().randomState());
        AsterionConfig config = AsterionConfig.INSTANCE;
        int floorY;
        if (isPitOpening(pos.getX(), pos.getZ())) {
            floorY = BOSS_FLOOR_Y;
        } else {
            MazeNbtStructures.Layout structures = ENABLE_MAZE_NBT_STRUCTURES
                    ? mazeStructureLayout(level) : MazeNbtStructures.emptyLayout();
            floorY = structures.floorY(pos.getX(), pos.getZ(),
                    mazeFloorY(seed, pos.getX(), pos.getZ(), config.cellSize));
        }
        int foundationBottom = floorY - config.floorThickness + 1;
        if (pos.getY() >= foundationBottom) return pos.getY() <= floorY;
        // Preserve the established fast repair behavior in the separate undercroft.
        return net.krodark.asterion.worldgen.CatacombProtection.contains(level, pos);
    }

    private static void installMazeWallCores(ServerLevel level, LevelChunk chunk, boolean legacyChunk) {
        long seed = net.krodark.asterion.worldgen.MazeChunkGenerator
                .terrainSeed(level.getChunkSource().randomState());
        AsterionConfig config = AsterionConfig.INSTANCE;
        int cell = config.cellSize;
        int thickness = config.wallThickness;
        int radius = config.mazeRadiusCells;
        MazeTopology topology = topology(seed, radius, config.mazeLoopChance, config.mazeLandmarkChance);
        MazeNbtStructures.Layout structures = ENABLE_MAZE_NBT_STRUCTURES
                ? mazeStructureLayout(level) : MazeNbtStructures.emptyLayout();
        BlockPos migrationMarker = new BlockPos(chunk.getPos().getMinBlockX() + 1, 1,
                chunk.getPos().getMinBlockZ());
        boolean expandLegacyShell = legacyChunk && !chunk.getBlockState(migrationMarker).is(Blocks.BEDROCK);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                if (isPitOpening(x, z) || isPitShaftWall(x, z)) continue;
                int floorY = structures.floorY(x, z, mazeFloorY(seed, x, z, cell));
                MazeBiomes.Biome biome = mazeBiomeAt(seed, x, z, cell);
                boolean wall = isWall(topology, structures, seed, biome,
                        x, z, cell, thickness, radius);
                if (!wall) continue;
                boolean core = isMazeWallCore(topology, structures, seed, biome,
                        x, z, cell, thickness, radius);
                // Version 24 widened old two-block walls by exactly their newly claimed
                // shell cells. Existing mined openings in the original footprint remain open.
                boolean newShell = expandLegacyShell && !isWall(topology, structures, seed, biome,
                        x, z, cell, 2, radius);
                int wallHeight = biome.kind() == MazeBiomes.Kind.CRIMSON_MARSHLANDS
                        ? Math.min(DIMENSION_CEILING_Y - floorY - 2,
                                Math.max(config.wallHeight + 28, 56))
                        : config.wallHeight;
                for (int rise = 1; rise <= wallHeight; rise++) {
                    cursor.set(x, floorY + rise, z);
                    BlockState current = chunk.getBlockState(cursor);
                    if (core) {
                        if (!current.is(Asterion.MAZE_WALL_CORE) && isMazeWallMaterial(current))
                            chunk.setBlockState(cursor, Asterion.MAZE_WALL_CORE.defaultBlockState(), 0);
                    } else if (newShell && current.isAir()) {
                        chunk.setBlockState(cursor, patternedWall(seed, x, rise, z, biome, cell, radius), 0);
                    }
                }
            }
        }
        if (!chunk.getBlockState(migrationMarker).is(Blocks.BEDROCK))
            chunk.setBlockState(migrationMarker, Blocks.BEDROCK.defaultBlockState(), 0);
    }

    private static boolean isMazeWallMaterial(BlockState state) {
        return state.is(Asterion.ANCIENT_BRICKS)
                || state.is(Asterion.ANCIENT_MOSSY_BRICKS)
                || state.is(Asterion.MOSSY_ANCIENT_STONE)
                || state.is(Asterion.ANCIENT_STONE)
                || state.is(Asterion.ANCIENT_LEAVES)
                || state.is(Asterion.TAINTED_LEAVES);
    }

    public static boolean isActivePortalProtected(ServerLevel level, BlockPos pos) {
        SummonedPortal portal = summonedPortal;
        if (portal == null || !portal.dimension.equals(level.dimension())) return false;
        int dx = Math.abs(pos.getX() - portal.center.getX());
        int dz = Math.abs(pos.getZ() - portal.center.getZ());
        return dx <= 5 && dz <= 5
                && pos.getY() >= portal.surfaceY - 3
                && pos.getY() <= portal.surfaceY + SUMMONED_PORTAL_DEPTH + 3;
    }

    public static int breakPlayerBlocksAround(ServerLevel level, AABB bounds) {
        int broken = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bounds.minX); x <= Mth.floor(bounds.maxX); x++)
            for (int y = Mth.floor(bounds.minY); y <= Mth.floor(bounds.maxY); y++)
                for (int z = Mth.floor(bounds.minZ); z <= Mth.floor(bounds.maxZ); z++) {
                    cursor.set(x, y, z);
                    BlockKey key = new BlockKey(level.dimension(), cursor.immutable());
                    Block expected = PLAYER_PLACED_BLOCKS.get(key);
                    if (expected == null || !level.getBlockState(cursor).is(expected)) continue;
                    if (isPermanentUpperMazeBuild(cursor)) continue;
                    PLAYER_PLACED_BLOCKS.remove(key);
                    level.destroyBlock(cursor, false, null, 512);
                    broken++;
                }
        return broken;
    }

    /** Removes isolated knee-high snags that a large predator should step through. Gates,
     * containers, runes, portals, and anything taller than one block remain protected. */
    public static int breakLowMazeSnags(ServerLevel level, AABB bounds, Entity breaker) {
        int broken = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bounds.minX); x <= Mth.floor(bounds.maxX); x++)
            for (int y = Mth.floor(bounds.minY); y <= Mth.floor(bounds.maxY); y++)
                for (int z = Mth.floor(bounds.minZ); z <= Mth.floor(bounds.maxZ); z++) {
                    if (broken >= 8) return broken;
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir() || state.is(Asterion.RUNE_ZONE_DOOR)
                            || state.is(Blocks.IRON_DOOR) || state.is(Blocks.IRON_BARS)
                            || state.is(Blocks.LODESTONE) || state.hasBlockEntity()
                            || isActivePortalProtected(level, cursor)
                            || isPermanentUpperPlayerBlock(level, cursor, state)
                            || state.getDestroySpeed(level, cursor) < 0.0F
                            || !level.getBlockState(cursor.above()).isAir()) continue;
                    var shape = state.getCollisionShape(level, cursor);
                    if (shape.isEmpty() || shape.max(Direction.Axis.Y) > 1.01D) continue;
                    trackMazeBreak(level, cursor, state);
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    broken++;
                }
        return broken;
    }

    public static int breakMazeWallAround(ServerLevel level, AABB bounds, Entity breaker) {
        return breakTemporaryMasonry(level, bounds, breaker, FLOOR_Y + 1, 96);
    }

    public static int breakBossArenaWallAround(ServerLevel level, AABB bounds, Entity breaker) {
        return breakTemporaryMasonry(level, bounds, breaker, BOSS_FLOOR_Y + 1, 144);
    }

    /** Whether every collision in a prospective boss lane can be destroyed during the run. */
    public static boolean isBreakableBossPath(ServerLevel level, AABB bounds) {
        boolean foundCollision = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bounds.minX); x <= Mth.floor(bounds.maxX); x++)
            for (int y = Math.max(BOSS_FLOOR_Y + 1, Mth.floor(bounds.minY));
                 y <= Mth.floor(bounds.maxY); y++)
                for (int z = Mth.floor(bounds.minZ); z <= Mth.floor(bounds.maxZ); z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.getCollisionShape(level, cursor).isEmpty()) continue;
                    foundCollision = true;
                    if (!canBossBreakPathBlock(level, cursor, state)) return false;
                }
        return foundCollision;
    }

    /** Clears player construction, rubble, and incidental structure from the boss's body volume. */
    public static int breakBossPathObstacle(ServerLevel level, AABB bounds,
                                            Entity breaker, int budget) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return 0;
        int broken = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bounds.minX); x <= Mth.floor(bounds.maxX); x++)
            for (int y = Math.max(BOSS_FLOOR_Y + 1, Mth.floor(bounds.minY));
                 y <= Mth.floor(bounds.maxY); y++)
                for (int z = Mth.floor(bounds.minZ); z <= Mth.floor(bounds.maxZ); z++) {
                    if (broken >= budget) return broken;
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.getCollisionShape(level, cursor).isEmpty()
                            || !canBossBreakPathBlock(level, cursor, state)) continue;
                    trackMazeBreak(level, cursor, state);
                    level.destroyBlock(cursor, false, breaker, 512);
                    broken++;
                }
        return broken;
    }

    private static boolean canBossBreakPathBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.is(Asterion.MAZESTEEL_GATE) && MinotaurArenaEntrances.isGate(pos)) return false;
        if (net.krodark.asterion.worldgen.CatacombArena.protectedBlock(pos, state)) return false;
        if (state.isAir() || state.hasBlockEntity() || isActivePortalProtected(level, pos)
                || state.getDestroySpeed(level, pos) < 0.0F) return false;

        // Preserve the arena shell and active phase-one pillars. Everything incidental inside the
        // playable ring may be smashed, including player blocks and generated rubble.
        double radiusSquared = (pos.getX() + 0.5D) * (pos.getX() + 0.5D)
                + (pos.getZ() + 0.5D) * (pos.getZ() + 0.5D);
        double protectedRadius = PIT_HALF_WIDTH - 1.5D;
        if (radiusSquared >= protectedRadius * protectedRadius) return false;
        BossArenaBuild build = bossArenaBuild;
        if (build != null) for (BossPillar pillar : build.pillars) {
            if (!pillar.broken && Math.abs(pos.getX() - pillar.x) <= 2
                    && Math.abs(pos.getZ() - pillar.z) <= 2
                    && pos.getY() <= BOSS_FLOOR_Y + 18) return false;
        }
        return true;
    }

    private static int breakTemporaryMasonry(ServerLevel level, AABB bounds, Entity breaker,
                                               int minimumY, int budget) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return 0;
        int broken = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = Mth.floor(bounds.minX); x <= Mth.floor(bounds.maxX); x++)
            for (int y = Math.max(minimumY, Mth.floor(bounds.minY)); y <= Mth.floor(bounds.maxY); y++)
                for (int z = Mth.floor(bounds.minZ); z <= Mth.floor(bounds.maxZ); z++) {
                    if (broken >= budget) return broken;
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    boolean mazeMasonry = state.is(Asterion.ANCIENT_BRICKS)
                            || state.is(Asterion.ANCIENT_MOSSY_BRICKS)
                            || state.is(Asterion.MOSSY_ANCIENT_STONE)
                            || state.is(Asterion.MAZESTEEL_BLOCK)
                            || state.is(Asterion.ANCIENT_STONE)
                            || state.is(Asterion.ANCIENT_BRICK_WALL)
                            || state.is(Asterion.ANCIENT_STONE_WALL)
                            || state.is(Blocks.MOSSY_STONE_BRICKS)
                            || state.is(Blocks.CRACKED_STONE_BRICKS)
                            || state.is(Blocks.TUFF_BRICKS)
                            || state.is(Blocks.BONE_BLOCK)
                            || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                            || state.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
                            || state.is(Blocks.CRACKED_DEEPSLATE_BRICKS)
                            || state.is(Blocks.COBBLED_DEEPSLATE)
                            || state.is(Blocks.POLISHED_BASALT);
                    if (!mazeMasonry || isActivePortalProtected(level, cursor)
                            || isPermanentUpperPlayerBlock(level, cursor, state)) continue;
                    trackMazeBreak(level, cursor, state);
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    broken++;
                }
        return broken;
    }

    private static void tickDecayingBlocks(MinecraftServer server) {
        while (!DECAYING_BLOCKS.isEmpty() && DECAYING_BLOCKS.peek().dueTick <= server.overworld().getGameTime()) {
            DecayingBlock entry = DECAYING_BLOCKS.poll();
            ServerLevel level = server.getLevel(entry.dimension);
            if (level == null || !level.getChunkSource().hasChunk(entry.pos.getX() >> 4, entry.pos.getZ() >> 4)) {
                continue;
            }
            BlockState current = level.getBlockState(entry.pos);
            if (isPermanentUpperMazeBuild(entry.pos)) continue;
            if (current.is(entry.expectedBlock)) {
                level.destroyBlock(entry.pos, false, null, 512);
                PLAYER_PLACED_BLOCKS.remove(new BlockKey(entry.dimension, entry.pos));
            }
        }
    }

    private static void tickRestoringBlocks(MinecraftServer server) {
        while (!RESTORING_BLOCKS.isEmpty()) {
            RestoringBlock entry = RESTORING_BLOCKS.peek();
            ServerLevel level = server.getLevel(entry.dimension);
            if (level == null || entry.dueTick > level.getGameTime()) return;
            RESTORING_BLOCKS.poll();
            if (!level.getChunkSource().hasChunk(entry.pos.getX() >> 4, entry.pos.getZ() >> 4)) {
                RESTORING_BLOCKS.add(new RestoringBlock(entry.dimension, entry.pos, entry.state,
                        level.getGameTime() + 20L));
                continue;
            }
            BlockState current = level.getBlockState(entry.pos);
            if (current.isAir() || current.is(Blocks.LIGHT) || current.canBeReplaced()) {
                AABB occupiedSpace = new AABB(entry.pos).deflate(0.02D);
                if (!level.getEntities((Entity)null, occupiedSpace, Entity::isAlive).isEmpty()) {
                    RESTORING_BLOCKS.add(new RestoringBlock(entry.dimension, entry.pos, entry.state,
                            level.getGameTime() + 10L));
                    continue;
                }
                if (!level.setBlock(entry.pos, entry.state, 3)) {
                    RESTORING_BLOCKS.add(new RestoringBlock(entry.dimension, entry.pos, entry.state,
                            level.getGameTime() + 10L));
                    continue;
                }
                var sound = entry.state.getSoundType();
                level.playSound(null, entry.pos, sound.getPlaceSound(), SoundSource.BLOCKS,
                        (sound.getVolume() + 1.0F) * 0.5F, sound.getPitch() * 0.8F);
            }
        }
    }

    public static BlockPos gatewayPosition(long seed) {
        double angle = unitFloat(mix(seed ^ 0x6A09E667F3BCC909L)) * Mth.TWO_PI;
        int distance = Math.max(128, Math.min(999, AsterionConfig.INSTANCE.gatewayDistance));
        return new BlockPos((int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
    }

    public static void summonPortal(ServerLevel level, BlockPos center, int surfaceY) {
        int riftY = surfaceY - SUMMONED_PORTAL_DEPTH;
        buildSummonedWell(level, center.getX(), surfaceY, center.getZ(), riftY);
        long visualSeed = mix(level.getSeed() ^ center.asLong() ^ level.getGameTime()
                ^ 0xA0761D6478BD642FL);
        summonedPortal = new SummonedPortal(level.dimension(), center.immutable(), riftY, visualSeed);
        AsterionWorldState.get(level).setSummonedPortal(
                level.dimension(), center.immutable(), riftY, visualSeed);
        GatewayPortalPayload payload = portalPayload(level.getServer(), center, riftY, visualSeed);
        level.players().forEach(player -> {
            if (player.distanceToSqr(Vec3.atCenterOf(center)) <= 144.0D * 144.0D
                    && ServerPlayNetworking.canSend(player, GatewayPortalPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
                LAST_PORTAL_SYNC.put(player.getUUID(), level.getGameTime());
            }
        });
    }

    private static void buildSummonedWell(ServerLevel level, int centerX, int surfaceY, int centerZ, int portalY) {
        clearAboveGateway(level, centerX, surfaceY, centerZ, 5);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            if (edge > 5 || Math.abs(dx) + Math.abs(dz) > 8) continue;
            int x = centerX + dx;
            int z = centerZ + dz;
            if (edge <= 1) {
                for (int y = portalY - 2; y <= surfaceY + 2; y++)
                    level.setBlock(cursor.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(cursor.set(x, portalY - 3, z),
                        gatewayRimState(dx, dz, Math.max(3, edge)), 2);
            } else if (edge == 2) {
                for (int y = portalY - 3; y < surfaceY; y++) {
                    int depth = surfaceY - y;
                    BlockState lining = depth % 3 == 0
                            ? Blocks.CHISELED_DEEPSLATE.defaultBlockState()
                            : gatewayRimState(dx, dz, edge);
                    level.setBlock(cursor.set(x, y, z), lining, 2);
                }
                level.setBlock(cursor.set(x, surfaceY, z),
                        ((Math.abs(dx) == 2 && dz == 0) || (Math.abs(dz) == 2 && dx == 0))
                                ? Asterion.ANCIENT_BRICK_SLAB.defaultBlockState()
                                : Asterion.ANCIENT_BRICK_WALL.defaultBlockState(), 2);
            } else {
                level.setBlock(cursor.set(x, surfaceY - 1, z), gatewayRimState(dx, dz, edge), 2);
                if (Math.abs(dx) == 4 && Math.abs(dz) == 4) {
                    level.setBlock(cursor.set(x, surfaceY, z), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
                    level.setBlock(cursor.set(x, surfaceY + 1, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
                }
            }
        }
    }

    public static void buildGateway(ServerLevel level, BlockPos horizontalTarget) {
        int x = horizontalTarget.getX();
        int z = horizontalTarget.getZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        int portalY = y - GATEWAY_PORTAL_DEPTH;
        GATEWAY_SURFACE_Y.put(level.getSeed(), portalY);
        clearAboveGateway(level, x, y, z, 6);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            if (Math.abs(dx) + Math.abs(dz) > 10) continue;
            if (edge >= 3 && edge <= 6)
                level.setBlock(p.set(x + dx, y - 1, z + dz), gatewayRimState(dx, dz, edge), 2);
            if (edge == 3) {
                level.setBlock(p.set(x + dx, y, z + dz),
                        ((Math.abs(dx) == 3 && dz == 0) || (Math.abs(dz) == 3 && dx == 0))
                                ? Asterion.ANCIENT_BRICK_SLAB.defaultBlockState()
                                : Asterion.ANCIENT_STONE_WALL.defaultBlockState(), 2);
            } else if (Math.abs(dx) == 5 && Math.abs(dz) == 5) {
                level.setBlock(p.set(x + dx, y, z + dz), Blocks.CHISELED_DEEPSLATE.defaultBlockState(), 2);
                level.setBlock(p.set(x + dx, y + 1, z + dz), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
            }
        }
        int shaftBottom = level.getMinY() + 5;
        for (int shaftY = y - 1; shaftY >= shaftBottom; shaftY--) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            if (edge <= 2)
                level.setBlock(p.set(x + dx, shaftY, z + dz), Blocks.AIR.defaultBlockState(), 2);
            else if (edge == 3) {
                int depth = y - shaftY;
                Block lining = depth % 9 == 0 || ((dx + dz + depth) & 15) == 0
                        ? Asterion.ANCIENT_STONE : Asterion.ANCIENT_BRICKS;
                level.setBlock(p.set(x + dx, shaftY, z + dz), lining.defaultBlockState(), 2);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            level.setBlock(p.set(x + dx, shaftBottom - 1, z + dz), Asterion.ANCIENT_STONE.defaultBlockState(), 2);
        level.setBlock(p.set(x, shaftBottom, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
    }

    /** Clears trees, leaves, terrain overhangs, and player blocks from the complete well footprint. */
    private static void clearAboveGateway(ServerLevel level, int centerX, int surfaceY, int centerZ, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            int top = Math.max(surfaceY, level.getHeight(Heightmap.Types.WORLD_SURFACE,
                    centerX + dx, centerZ + dz));
            for (int y = surfaceY; y <= top; y++) {
                cursor.set(centerX + dx, y, centerZ + dz);
                if (!level.getBlockState(cursor).isAir())
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static BlockState gatewayRimState(int dx, int dz, int edgeDistance) {
        if (edgeDistance >= 3 && Math.floorMod(dx * 3 + dz * 5, 5) == 0)
            return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        if (Math.floorMod(dx - dz, 3) == 0) return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        return ((dx + dz) & 1) == 0 ? Asterion.ANCIENT_STONE.defaultBlockState()
                : Asterion.ANCIENT_BRICKS.defaultBlockState();
    }

    public static void tickPlayer(ServerPlayer player) {
        syncBiomeAtmosphere(player);
        PendingTransition pending = PENDING_TRANSITIONS.get(player.getUUID());
        if (pending != null) {
            tickTransition(player, pending);
            return;
        }
        if (tickSummonedPortal(player)) return;
        if (player.level().dimension().equals(Level.OVERWORLD)) {
            BlockPos gateway = gatewayPosition(player.level().getSeed());
            double dx = player.getX() - (gateway.getX() + 0.5D);
            double dz = player.getZ() - (gateway.getZ() + 0.5D);
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < 64.0 * 64.0 && !GATEWAY_SURFACE_Y.containsKey(player.level().getSeed()))
                buildGateway(player.level(), gateway);
            Long lastPortalSync = LAST_PORTAL_SYNC.get(player.getUUID());
            long now = player.level().getGameTime();
            if (distanceSquared < 64.0 * 64.0 && (lastPortalSync == null || now - lastPortalSync >= 100L)
                    && ServerPlayNetworking.canSend(player, GatewayPortalPayload.TYPE)) {
                int portalY = GATEWAY_SURFACE_Y.computeIfAbsent(player.level().getSeed(), ignored ->
                        player.level().getHeight(Heightmap.Types.WORLD_SURFACE, gateway.getX(), gateway.getZ()));
                ServerPlayNetworking.send(player, portalPayload(player.level().getServer(), gateway, portalY,
                        mix(player.level().getSeed() ^ 0xA0761D6478BD642FL)));
                LAST_PORTAL_SYNC.put(player.getUUID(), now);
            }
            if (Math.max(Math.abs(dx), Math.abs(dz)) < 2.45D) {
                int surface = GATEWAY_SURFACE_Y.computeIfAbsent(player.level().getSeed(), ignored ->
                        player.level().getHeight(Heightmap.Types.WORLD_SURFACE, gateway.getX(), gateway.getZ()));
                if (player.getY() > surface + 0.4D || player.getY() < surface - 2.2D) return;
                ServerLevel maze = player.level().getServer().getLevel(Asterion.ASTERION_LEVEL);
                if (maze != null) beginTransition(player, maze);
            }
        } else if (player.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            if (rescueFromMazeVoid(player)) return;
            tickElectrified(player);
            tickMazeWard(player);
        } else {
            ABOVE_WALL_TICKS.remove(player.getUUID());
        }
    }

    private static void syncBiomeAtmosphere(ServerPlayer player) {
        int biome = 0;
        if (player.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            MazeBiomes.Kind kind = mazeBiomeAt(activeMazeTerrainSeed, player.getBlockX(),
                    player.getBlockZ(), AsterionConfig.INSTANCE.cellSize).kind();
            biome = kind == MazeBiomes.Kind.OVERGROWTH ? 1
                    : kind == MazeBiomes.Kind.CRIMSON_MARSHLANDS ? 2 : 0;
        }
        Integer previous = LAST_BIOME_ATMOSPHERE.get(player.getUUID());
        boolean refresh = player.level().getGameTime() % 100L == 0L;
        if ((previous == null || previous != biome || refresh)
                && ServerPlayNetworking.canSend(player, BiomeAtmospherePayload.TYPE)) {
            ServerPlayNetworking.send(player, new BiomeAtmospherePayload(biome));
            LAST_BIOME_ATMOSPHERE.put(player.getUUID(), biome);
        }
    }

    private static boolean rescueFromMazeVoid(ServerPlayer player) {
        ServerLevel maze = player.level();
        if (player.getY() >= maze.getMinY() + 4) return false;

        BlockPos ground = findNearestSafeGround(maze, Mth.floor(player.getX()), Mth.floor(player.getZ()));
        if (ground == null) {
            Vec3 corridor = nearestMazeCorridor(player.getX(), player.getZ());
            ground = new BlockPos(Mth.floor(corridor.x), FLOOR_Y, Mth.floor(corridor.z));
            maze.getChunkAt(ground);
        }

        int rescueY = Math.min(maze.getMaxY() - 4, ground.getY() + SKYFALL_CLEARANCE);
        player.teleportTo(maze, ground.getX() + 0.5D, rescueY, ground.getZ() + 0.5D,
                java.util.Set.of(), player.getYRot(), 0.0F, true);
        player.setDeltaMovement(0.0D, -0.12D, 0.0D);
        player.resetFallDistance();
        player.hurtMarked = true;
        RagdollServerNetworking.forceAuthority(player, player.getDeltaMovement());
        WARD_FALL_PROTECTION.put(player.getUUID(), 20 * 45);
        return true;
    }

    private static BlockPos findNearestSafeGround(ServerLevel level, int originX, int originZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int top = level.getMaxY() - 4;
        int bottom = level.getMinY();
        for (int radius = 0; radius <= 48; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                BlockPos found = safeGroundInColumn(level, cursor, originX + dx, originZ - radius, top, bottom);
                if (found != null) return found;
                if (radius != 0) {
                    found = safeGroundInColumn(level, cursor, originX + dx, originZ + radius, top, bottom);
                    if (found != null) return found;
                }
            }
            for (int dz = -radius + 1; dz < radius; dz++) {
                BlockPos found = safeGroundInColumn(level, cursor, originX - radius, originZ + dz, top, bottom);
                if (found != null) return found;
                if (radius != 0) {
                    found = safeGroundInColumn(level, cursor, originX + radius, originZ + dz, top, bottom);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    private static BlockPos safeGroundInColumn(ServerLevel level, BlockPos.MutableBlockPos cursor,
                                                int x, int z, int top, int bottom) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        for (int y = top; y >= bottom; y--) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).isAir()) continue;
            if (level.getBlockState(cursor.set(x, y + 1, z)).isAir()
                    && level.getBlockState(cursor.set(x, y + 2, z)).isAir())
                return new BlockPos(x, y, z);
        }
        return null;
    }

    private static boolean tickSummonedPortal(ServerPlayer player) {
        SummonedPortal portal = summonedPortal;
        if (portal == null || !player.level().dimension().equals(portal.dimension)) return false;
        double dx = player.getX() - (portal.center.getX() + 0.5D);
        double dz = player.getZ() - (portal.center.getZ() + 0.5D);
        double distanceSquared = dx * dx + dz * dz;
        long now = player.level().getGameTime();
        Long lastSync = LAST_PORTAL_SYNC.get(player.getUUID());
        if (distanceSquared < 144.0D * 144.0D && (lastSync == null || now - lastSync >= 100L)
                && ServerPlayNetworking.canSend(player, GatewayPortalPayload.TYPE)) {
            ServerPlayNetworking.send(player, portalPayload(player.level().getServer(),
                    portal.center, portal.surfaceY, portal.visualSeed));
            LAST_PORTAL_SYNC.put(player.getUUID(), now);
        }
        if (portal.dimension.equals(Asterion.ASTERION_LEVEL)) {
            if (Math.abs(dx) > 1.55D || Math.abs(dz) > 1.15D
                    || Math.abs(player.getY() - portal.surfaceY) > 2.55D) return false;
            if (!AsterionWorldState.get((ServerLevel)player.level()).minotaurDefeated()) return false;
            beginArenaExit(player);
            return true;
        }
        if (Math.max(Math.abs(dx), Math.abs(dz)) > 1.48D
                || Math.abs(player.getY() - portal.surfaceY) > 2.0D) return false;
        ServerLevel maze = player.level().getServer().getLevel(Asterion.ASTERION_LEVEL);
        if (maze == null) return false;
        beginTransition(player, maze);
        return true;
    }

    private static GatewayPortalPayload portalPayload(MinecraftServer server, BlockPos center,
                                                       int surfaceY, long visualSeed) {
        return new GatewayPortalPayload(true, center, surfaceY, visualSeed);
    }

    private static void tickPhasingEntities(MinecraftServer server) {
        SummonedPortal portal = summonedPortal;
        if (portal != null) {
            ServerLevel source = server.getLevel(portal.dimension);
            if (source != null) {
                Vec3 center = new Vec3(portal.center.getX() + 0.5D, portal.surfaceY, portal.center.getZ() + 0.5D);
                boolean vertical = portal.dimension.equals(Asterion.ASTERION_LEVEL);
                AABB intake = vertical
                        ? new AABB(center.x - 1.55D, center.y - 2.55D, center.z - 1.15D,
                                center.x + 1.55D, center.y + 2.55D, center.z + 1.15D)
                        : new AABB(center.x - 1.48D, center.y - 2.0D, center.z - 1.48D,
                                center.x + 1.48D, center.y + 2.0D, center.z + 1.48D);
                for (Entity entity : source.getEntitiesOfClass(Entity.class, intake,
                        entity -> !(entity instanceof ServerPlayer) && !entity.isRemoved())) {
                    double dx = entity.getX() - center.x;
                    double dz = entity.getZ() - center.z;
                    if (vertical || Math.max(Math.abs(dx), Math.abs(dz)) <= 1.48D)
                        PHASING_ENTITIES.computeIfAbsent(entity.getUUID(), ignored -> new PhasingEntity(entity));
                }
            }
        }

        var iterator = PHASING_ENTITIES.entrySet().iterator();
        while (iterator.hasNext()) {
            PhasingEntity phase = iterator.next().getValue();
            Entity entity = phase.entity;
            if (entity.isRemoved()) {
                iterator.remove();
                continue;
            }
            entity.noPhysics = true;
            entity.setNoGravity(true);
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPos(entity.getX(), entity.getY() - (0.10D + phase.ticks * 0.012D), entity.getZ());
            if (phase.ticks++ < 14) continue;

            ServerLevel maze = server.getLevel(Asterion.ASTERION_LEVEL);
            boolean moved = false;
            if (maze != null) {
                BlockPos landing = randomMazeArrival(maze, entity.getUUID(), maze.getGameTime());
                prepareMazeArrival(maze, landing);
                moved = entity.teleportTo(maze, landing.getX() + 0.5D, skyfallY(), landing.getZ() + 0.5D,
                        java.util.Set.of(), entity.getYRot(), entity.getXRot(), true);
            }
            entity.noPhysics = phase.wasNoPhysics;
            entity.setNoGravity(phase.hadNoGravity);
            if (moved) {
                entity.setDeltaMovement(new Vec3(0.08D, -0.28D, -0.05D));
                entity.hurtMarked = true;
                entity.resetFallDistance();
                if (entity instanceof LivingEntity)
                    WARD_FALL_PROTECTION.put(entity.getUUID(), 20 * 45);
            } else {
                entity.setPos(phase.origin.x, phase.origin.y, phase.origin.z);
                entity.setDeltaMovement(phase.originalVelocity);
            }
            iterator.remove();
        }
    }

    private static void tickMazeEntities(ServerLevel maze) {
        for (Entity entity : maze.getAllEntities()) {
            if (entity instanceof LivingEntity living && !(living instanceof ServerPlayer) && living.isAlive()) {
                tickElectrified(living);
                tickMazeWard(living);
            }
        }
    }

    private static void tickMinotaurDirector(ServerLevel maze) {
        if (bossFinale != null) return;
        if (AsterionWorldState.get(maze).minotaurDefeated()) {
            for (Entity entity : maze.getAllEntities())
                if (entity instanceof MinotaurEntity minotaur && !minotaur.isDefeatedBoss())
                    entity.discard();
            return;
        }
        List<ServerPlayer> players = maze.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator()
                        && (!net.krodark.asterion.worldgen.CatacombLayout.contains(player.blockPosition())
                        || MinotaurArenaEntrances.corridorAt(player.position()) != null)).toList();
        if (players.isEmpty()) { ARENA_PREVIOUS_POSITIONS.clear(); return; }
        List<MinotaurEntity> minotaurs = new ArrayList<>();
        for (Entity entity : maze.getAllEntities()) {
            if (!(entity instanceof MinotaurEntity minotaur)
                    || !minotaur.isAlive() || minotaur.isRemoved()) continue;
            // Overgrowth is a sanctuary. The central boss remains valid because its
            // arena is Ancient; roaming/stalking Minotaurs are never retained here.
            if (minotaur.behaviorPhase() != MinotaurEntity.BehaviorPhase.BOSS
                    && isOvergrowthBiomeAt(minotaur.getX(), minotaur.getZ())) {
                minotaur.discard();
                continue;
            }
            minotaurs.add(minotaur);
        }

        if (minotaurs.size() > 1) {
            MinotaurEntity keeper = minotaurs.stream()
                    .filter(entity -> entity.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS)
                    .findFirst().orElse(minotaurs.get(0));
            for (MinotaurEntity entity : minotaurs)
                if (entity != keeper && entity.canDespawnUnseen()) entity.discard();
            minotaurs = new ArrayList<>(List.of(keeper));
        }

        for (ServerPlayer player : players) {
            Vec3 previous = ARENA_PREVIOUS_POSITIONS.put(player.getUUID(), player.position());
            Direction entrance = MinotaurArenaEntrances.crossedEntrance(previous, player.position());
            Long requestedAt=BOSS_START_REQUESTS.get(player.getUUID());
            if(entrance==null&&requestedAt!=null&&maze.getGameTime()>=requestedAt
                    &&player.distanceToSqr(Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(
                    MinotaurArenaEntrances.PLAYER_ENTRANCE)))<=24D*24D)
                entrance=MinotaurArenaEntrances.PLAYER_ENTRANCE;
            if (entrance == null || !isBossArenaReady() || BOSS_ENTRANTS.contains(player.getUUID())) continue;
            if (!(maze.getBlockEntity(MinotaurArenaEntrances.door(entrance)) instanceof MinotaurDoorBlockEntity door)
                    || !door.allowsArenaEntry()) continue;
            MinotaurEntity existing = minotaurs.stream().filter(entity -> entity.isAssignedTo(player))
                    .findFirst().orElse(minotaurs.stream().findFirst().orElse(null));
            MinotaurEntity boss = MinotaurEntity.activateCenterBoss(maze, player, existing, entrance);
            if (boss != null) {
                BOSS_START_REQUESTS.remove(player.getUUID());
                BOSS_ENTRANTS.add(player.getUUID());
                if (!minotaurs.contains(boss)) minotaurs.add(boss);
                BossArenaEncounter.begin(maze, player, boss, entrance);
            }
        }
        ARENA_PREVIOUS_POSITIONS.keySet().removeIf(id -> players.stream().noneMatch(player -> player.getUUID().equals(id)));

        if (minotaurs.isEmpty() && !DeadSunEventSystem.isEclipseActive(maze)) {
            ServerPlayer candidate = players.stream()
                    .filter(player -> !player.isCreative())
                    .filter(player -> !WorldGenerator.isApproachingCenter(player.position()))
                    .filter(player -> !WorldGenerator.isOvergrowthBiomeAt(
                            player.getX(), player.getZ()))
                    .min(Comparator.comparingLong(player -> roamerRevealTick(maze, player))).orElse(null);
            if (candidate != null && maze.getGameTime() >= roamerRevealTick(maze, candidate)) {
                MinotaurEntity spawned = MinotaurEntity.spawnRoamer(maze, candidate);
                ROAMER_REVEAL_TICKS.put(candidate.getUUID(), maze.getGameTime()
                        + (spawned == null ? 20L * 18L : 20L * 150L));
            }
        }
        ROAMER_REVEAL_TICKS.keySet().removeIf(id -> maze.getPlayerByUUID(id) == null);
    }

    private static long roamerRevealTick(ServerLevel level, ServerPlayer player) {
        return ROAMER_REVEAL_TICKS.computeIfAbsent(player.getUUID(), id -> {
            long roll = mix(level.getSeed() ^ id.getMostSignificantBits()
                    ^ Long.rotateLeft(id.getLeastSignificantBits(), 17) ^ level.getGameTime());
            return level.getGameTime() + 20L * (45L + Math.floorMod(roll, 106L));
        });
    }

    public static void beginBossFinale(ServerLevel level, MinotaurEntity boss) {
        if (AsterionWorldState.get(level).minotaurDefeated()) return;
        BossArenaEncounter.finishDefeated(level);
        AsterionWorldState.get(level).markMinotaurDefeated();
        var omegaKey = boss.spawnAtLocation(level, new net.minecraft.world.item.ItemStack(Asterion.OMEGA_KEY));
        ServerPlayer nearest = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator())
                .min(Comparator.comparingDouble(player -> player.distanceToSqr(boss))).orElse(null);
        net.krodark.asterion.game.EncounterKeyRecovery.track(level, omegaKey, nearest);
        net.krodark.asterion.worldgen.OmegaTreasure.reward(level);
        openArenaExit(level);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.asterion.omega_treasure"));
        }
    }

    private static void openArenaExit(ServerLevel level) {
        for (int x=-1;x<=1;x++) for (int y=-2;y<=2;y++)
            level.setBlock(ARENA_EXIT_PORTAL.offset(x,y,0), Blocks.AIR.defaultBlockState(), 2);
        long seed = mix(level.getSeed() ^ 0x51A7E0B1D4C3A29FL) | Long.MIN_VALUE;
        summonedPortal = new SummonedPortal(level.dimension(), ARENA_EXIT_PORTAL, ARENA_EXIT_PORTAL.getY(), seed);
        AsterionWorldState.get(level).setSummonedPortal(level.dimension(), ARENA_EXIT_PORTAL,
                ARENA_EXIT_PORTAL.getY(), seed);
        GatewayPortalPayload payload = portalPayload(level.getServer(), ARENA_EXIT_PORTAL,
                ARENA_EXIT_PORTAL.getY(), seed);
        for (ServerPlayer player:level.players()) if(ServerPlayNetworking.canSend(player,GatewayPortalPayload.TYPE))
            ServerPlayNetworking.send(player,payload);
    }

    private static void beginArenaExit(ServerPlayer entrant) {
        if (bossFinale != null) return;
        ServerLevel maze=entrant.level();
        UUID defeatedBoss = new UUID(0, 0);
        for (Entity entity : maze.getAllEntities()) {
            if (entity instanceof MinotaurEntity minotaur && minotaur.isDefeatedBoss()) {
                defeatedBoss = minotaur.getUUID();
                break;
            }
        }
        bossFinale=new BossFinale(defeatedBoss);
        for(ServerPlayer player:maze.players()) {
            bossFinale.previousInvulnerability.put(player.getUUID(),player.isInvulnerable());
            player.setInvulnerable(true);
        }
    }

    private static void tickBossFinale(ServerLevel maze) {
        BossFinale finale = bossFinale;
        if (finale == null) return;
        finale.ticks++;
        if (finale.ticks <= 0) return;
        if (finale.ticks == 1) for (ServerPlayer player : maze.players())
            if (ServerPlayNetworking.canSend(player, BossFinalePayload.TYPE)) ServerPlayNetworking.send(player, BossFinalePayload.INSTANCE);
        if ((finale.ticks % 6) == 0 && finale.ticks <= 260) {
            MazeShiftPayload rumble = new MazeShiftPayload(BlockPos.containing(bossArenaCenter()),
                    4096.0F, Math.min(6.0F, 0.55F + finale.ticks / 42.0F), 20);
            for (ServerPlayer player : maze.players())
                if (ServerPlayNetworking.canSend(player, MazeShiftPayload.TYPE))
                    ServerPlayNetworking.send(player, rumble);
            maze.sendParticles(ParticleTypes.EXPLOSION, 0.5D, BOSS_FLOOR_Y + 3.0D, 0.5D,
                    8 + finale.ticks / 8, 20.0D, 7.0D, 20.0D, 0.08D);
        }
        if (finale.ticks >= 28 && finale.ticks <= 222 && finale.ticks % 3 == 0)
            crumbleFinaleMazeRing(maze, finale.ticks);
        int lightningInterval = switch (AsterionConfig.INSTANCE.cinematicQuality) {
            case 0 -> 18;
            case 1 -> 12;
            default -> 8;
        };
        if (finale.ticks >= 34 && finale.ticks <= 220 && finale.ticks % lightningInterval == 0)
            strikeFinaleAroundPlayers(maze, finale.ticks, false);
        if (finale.ticks == 220) strikeFinaleAroundPlayers(maze, finale.ticks, true);
        if (finale.ticks == 260) for (ServerPlayer player : maze.players()) {
            player.setInvulnerable(true);
            player.setDeltaMovement(Vec3.ZERO);
        }
        if (finale.ticks >= 260 && finale.ticks < 310) for (ServerPlayer player : maze.players())
            player.setDeltaMovement(Vec3.ZERO);
        if (finale.ticks == 310) {
            for (ServerPlayer player : new ArrayList<>(maze.players())) {
                finale.previousInvulnerability.putIfAbsent(player.getUUID(), player.isInvulnerable());
                player.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ECHO_SHARD, 8));
                player.giveExperiencePoints(750);
                FinaleDestination destination = finaleDestination(player);
                player.teleportTo(destination.level(), destination.position().getX() + 0.5D,
                        destination.position().getY() + 1.1D, destination.position().getZ() + 0.5D,
                        Set.of(), destination.yaw(), 0.0F, true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                player.setInvulnerable(true);
            }
        }
        if (finale.ticks == 362) {
            ServerLevel overworld = maze.getServer().overworld();
            for (var entry : finale.previousInvulnerability.entrySet()) {
                var found = overworld.getPlayerByUUID(entry.getKey());
                if (found instanceof ServerPlayer player) player.setInvulnerable(entry.getValue());
            }
        }
        if (finale.ticks >= 390) {
            bossFinale = null;
            BossArenaEncounter.finishDefeated(maze);
            clearBossArenaTransientState(maze);
        }
    }

    private static FinaleDestination finaleDestination(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerPlayer.RespawnConfig config = player.getRespawnConfig();
        LevelData.RespawnData respawn = config == null ? null : config.respawnData();
        if (respawn != null && respawn.dimension().equals(Level.OVERWORLD)) {
            ServerLevel target = server.getLevel(respawn.dimension());
            if (target != null) {
                target.getChunkAt(respawn.pos());
                return new FinaleDestination(target, respawn.pos(), respawn.yaw());
            }
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
        overworld.getChunkAt(spawn);
        int surface = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawn.getX(), spawn.getZ());
        return new FinaleDestination(overworld,
                new BlockPos(spawn.getX(), surface, spawn.getZ()), 0.0F);
    }

    private static void strikeFinaleAroundPlayers(ServerLevel level, int ticks, boolean detonation) {
        float progress = Mth.clamp(ticks / 220.0F, 0.0F, 1.0F);
        int count = detonation ? 10 : 1 + Mth.floor(progress * 2.0F);
        for (ServerPlayer player : new ArrayList<>(level.players())) {
            for (int index = 0; index < count; index++) {
                long roll = mix(level.getSeed() ^ player.getUUID().getLeastSignificantBits()
                        ^ (long) ticks * 0x9E3779B97F4A7C15L ^ index * 0xD1B54A32D192ED03L);
                double angle = Mth.TWO_PI * (unitFloat(roll) + index / (double) count);
                double distance = detonation ? 5.0D + index * 2.8D
                        : 5.0D + unitFloat(roll >>> 7) * (10.0D + progress * 18.0D);
                int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
                int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
                BlockPos target = findDeadSunStrikeTarget(level, x, z, Mth.floor(player.getY()));
                DeadSunStrikePayload payload = new DeadSunStrikePayload(target, 0,
                        detonation ? 7.5F : 3.4F + progress * 2.4F, roll);
                for (ServerPlayer viewer : level.players())
                    if (ServerPlayNetworking.canSend(viewer, DeadSunStrikePayload.TYPE))
                        ServerPlayNetworking.send(viewer, payload);
                finaleLightningImpact(level, target, detonation ? 7 : 3 + Mth.floor(progress * 3.0F));
            }
        }
        level.playSound(null, BlockPos.containing(bossArenaCenter()), SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, detonation ? 12.0F : 5.0F, detonation ? 0.42F : 0.58F);
        if (detonation)
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, 0.5D, BOSS_FLOOR_Y + 8.0D, 0.5D,
                    18, 26.0D, 12.0D, 26.0D, 0.18D);
    }

    public static BlockPos findDeadSunStrikeTarget(ServerLevel level, int x, int z, int aroundY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x,
                Math.min(DIMENSION_CEILING_Y - 1, Math.max(BOSS_FLOOR_Y + 1, aroundY + 8)), z);
        for (int y = cursor.getY(); y >= Math.max(1, aroundY - 12); y--) {
            cursor.setY(y);
            if (!level.getBlockState(cursor).isAir()) return cursor.above().immutable();
        }
        return new BlockPos(x, Math.max(BOSS_FLOOR_Y + 1, aroundY), z);
    }

    public static void applyDeadSunBarrageImpact(ServerLevel level, BlockPos target, float radius) {
        int blockRadius = Math.max(2, Mth.floor(radius));
        AABB breach = new AABB(target).inflate(blockRadius, 1.0D, blockRadius)
                .expandTowards(0.0D, Math.min(9.0D, blockRadius + 4.0D), 0.0D);
        breakMazeWallAround(level, breach, null);
        Vec3 center = Vec3.atCenterOf(target);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(target).inflate(radius + 1.5D), ServerPlayer::isAlive)) {
            Vec3 away = player.position().subtract(center);
            if (away.lengthSqr() < 0.01D) away = new Vec3(0.4D, 0.0D, 0.2D);
            away = new Vec3(away.x, 0.0D, away.z).normalize();
            player.hurtServer(level, player.damageSources().lightningBolt(), 7.0F);
            Vec3 impulse = away.scale(1.45D).add(0.0D, 0.7D, 0.0D);
            player.setDeltaMovement(impulse);
            player.hurtMarked = true;
            if (ServerPlayNetworking.canSend(player, RagdollExplosionPayload.TYPE))
                ServerPlayNetworking.send(player, new RagdollExplosionPayload(center, radius + 2.0F));
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z,
                8 + blockRadius, radius * 0.4D, 1.2D, radius * 0.4D, 0.1D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 1.0D, center.z,
                48, radius * 0.55D, 2.8D, radius * 0.55D, 0.22D);
        level.playSound(null, target, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                5.5F, 0.52F + level.getRandom().nextFloat() * 0.12F);
    }

    private static void finaleLightningImpact(ServerLevel level, BlockPos target, int radius) {
        int rubble = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            for (int dy = 0; dy <= Math.min(8, radius + 2); dy++) {
                cursor.set(target.getX() + dx, target.getY() + dy, target.getZ() + dz);
                BlockState state = level.getBlockState(cursor);
                if (state.isAir() || state.is(Blocks.BEDROCK) || isActivePortalProtected(level, cursor)) continue;
                if (rubble < 12 && Math.floorMod(dx * 13 + dz * 7 + dy * 5, 4) == 0) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    Vec3 away = new Vec3(dx, 0.5D, dz);
                    if (away.lengthSqr() < 0.01D) away = new Vec3(1.0D, 0.5D, 0.0D);
                    away = away.normalize().scale(0.55D + level.getRandom().nextDouble() * 0.7D);
                    net.krodark.asterion.worldgen.ArenaDebris.queue(level, Vec3.atCenterOf(cursor), new Vec3(away.x, 0.7D + level.getRandom().nextDouble(), away.z));
                    rubble++;
                } else level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D, 5 + radius, radius * 0.45D, 1.4D,
                radius * 0.45D, 0.12D);
    }

    private static void crumbleFinaleMazeRing(ServerLevel level, int finaleTicks) {
        double progress = Mth.clamp((finaleTicks - 28) / 194.0D, 0.0D, 1.0D);
        double mazeCorner = AsterionConfig.INSTANCE.mazeRadiusCells
                * AsterionConfig.INSTANCE.cellSize * Math.sqrt(2.0D);
        double radius = 4.0D + Math.pow(progress, 1.58D) * mazeCorner;
        int fragments = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int sample = 0; sample < 32; sample++) {
            double angle = Mth.TWO_PI * sample / 32.0D + finaleTicks * 0.037D;
            int x = Mth.floor(Math.cos(angle) * radius);
            int z = Mth.floor(Math.sin(angle) * radius);
            int bottom = radius <= PIT_HALF_WIDTH + 3 ? BOSS_FLOOR_Y : FLOOR_Y;
            int top = radius <= PIT_HALF_WIDTH + 3
                    ? bossRoofY(x, z)
                    : FLOOR_Y + AsterionConfig.INSTANCE.wallHeight;
            for (int layer = 0; layer < 3; layer++) {
                int y = bottom + 1 + Math.floorMod(sample * 11 + finaleTicks * 3 + layer * 7,
                        Math.max(1, top - bottom));
                cursor.set(x, y, z);
                if (!level.getChunkSource().hasChunk(cursor.getX() >> 4, cursor.getZ() >> 4)) continue;
                BlockState state = level.getBlockState(cursor);
                if (state.isAir() || state.is(Blocks.BEDROCK)) continue;
                if (fragments < 14 && Math.floorMod(sample + layer + finaleTicks, 3) == 0) {
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    Vec3 outward = new Vec3(x, 0.0D, z);
                    if (outward.lengthSqr() < 0.01D) outward = new Vec3(1.0D, 0.0D, 0.0D);
                    outward = outward.normalize();
                    net.krodark.asterion.worldgen.ArenaDebris.queue(level, Vec3.atCenterOf(cursor), new Vec3(outward.x * (0.35D + level.getRandom().nextDouble() * 0.55D),
                            0.45D + level.getRandom().nextDouble() * 0.75D,
                            outward.z * (0.35D + level.getRandom().nextDouble() * 0.55D)));
                    fragments++;
                }
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, 0.5D, bottomForFinaleRadius(radius) + 3.0D, 0.5D,
                16, radius * 0.55D, 4.0D, radius * 0.55D, 0.045D);
    }

    private static int bottomForFinaleRadius(double radius) {
        return radius <= PIT_HALF_WIDTH + 3 ? BOSS_FLOOR_Y : FLOOR_Y;
    }

    /** Complete the chamber during world loading, before anyone can see or fall into its pit. */
    public static void prepareBossArenaBeforePlayers(ServerLevel level) {
        if (bossArenaPrepared) return;
        bossArenaPrepared = true;
        bossArenaBuild = new BossArenaBuild();
        // The room exists regardless of whether this world's boss was already beaten.
        // Only encounter activation is victory-gated; architecture is always installed.
        net.krodark.asterion.worldgen.AuthoredCatacombs.placeArena(level);
    }

    private static int arenaRevision() { return 907; }

    private static void rebuildBossArena(ServerLevel level) {
        bossArenaPrepared = true;
        // The NBT files own the room: no generated floor, dome, pillars or furniture.
        bossArenaBuild = new BossArenaBuild();
        ARENA_PREVIOUS_POSITIONS.clear();
        net.krodark.asterion.worldgen.AuthoredCatacombs.placeArena(level);
    }

    private static void finishBossArenaBuildIfReady(ServerLevel level) {
        if (!bossArenaPrepared || bossArenaBuild == null || bossArenaBuild.ready
                || !net.krodark.asterion.worldgen.AuthoredCatacombs.arenaComplete(level)) return;
        discoverAuthoredBossPillars(level, bossArenaBuild);
        net.krodark.asterion.worldgen.AuthoredCatacombs.ensureArenaPillars(level);
        MinotaurArenaEntrances.build(level);
        bossArenaBuild.ready = true;
        ARENA_PREVIOUS_POSITIONS.clear();
        AsterionWorldState.get(level).setBossArenaRevision(arenaRevision());
    }

    public static void arenaChunkPlaced(ServerLevel level) {
        finishBossArenaBuildIfReady(level);
    }

    private static void discoverAuthoredBossPillars(ServerLevel level, BossArenaBuild build) {
        build.pillars.clear();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS;
             x <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS; x++)
            for (int z = -net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS;
                 z <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS; z++) {
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                for (int y = net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_BASE_Y;
                     y <= BOSS_FLOOR_Y + 2; y++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(Asterion.PILLAR)
                            && net.krodark.asterion.block.PillarBlock.isRoot(state)) {
                        build.pillars.add(new BossPillar(x, y, z));
                        break;
                    }
                }
            }
        build.pillars.sort(java.util.Comparator.comparingInt((BossPillar p) -> p.x)
                .thenComparingInt(p -> p.z));
    }

    private static void ensureAuthoredBossPillars(ServerLevel level) {
        if (bossArenaBuild != null && bossArenaBuild.ready && bossArenaBuild.pillars.isEmpty())
            discoverAuthoredBossPillars(level, bossArenaBuild);
    }

    public static void registerAuthoredArenaPillars(ServerLevel level, LevelChunk chunk) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null) return;
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++)
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++)
                for (int y = BOSS_FLOOR_Y; y <= BOSS_FLOOR_Y + 2; y++) {
                    cursor.set(x, y, z);
                    BlockState state = chunk.getBlockState(cursor);
                    if (!state.is(Asterion.PILLAR)
                            || !net.krodark.asterion.block.PillarBlock.isRoot(state)) continue;
                    boolean known = false;
                    for (BossPillar pillar : build.pillars)
                        if (pillar.x == x && pillar.y == y && pillar.z == z) { known = true; break; }
                    if (!known) build.pillars.add(new BossPillar(x, y, z));
                    break;
                }
    }

    private static int bossPillarHeight(int x, int z) {
        if (net.krodark.asterion.worldgen.AuthoredCatacombs.enabled()) return 27;
        int ceiling = Integer.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int px = x + dx, pz = z + dz;
            int radius = Mth.floor(Math.sqrt(px * px + pz * pz));
            boolean rib = Math.abs(px) <= 1 || Math.abs(pz) <= 1 || Math.floorMod(radius, 7) == 0;
            ceiling = Math.min(ceiling, bossRoofY(px, pz) - (rib ? 1 : 0));
        }
        return Mth.clamp(ceiling - BOSS_FLOOR_Y - 1, 1, 27);
    }

    private static int bossRoofY(int x, int z) {
        int distance = Mth.floor(Math.sqrt(x * x + z * z));
        return BOSS_FLOOR_Y + 18 + Math.max(0, (PIT_HALF_WIDTH - distance) / 4);
    }

    public static boolean isBossArenaReady() {
        return bossArenaBuild != null && bossArenaBuild.ready;
    }
    public static boolean ensureBossArenaReady(ServerLevel level) {
        if(isBossArenaReady())return true;
        if(!bossArenaPrepared)prepareBossArenaBeforePlayers(level);
        for(ChunkPos pos:net.krodark.asterion.worldgen.ZoneRunePlacement.arenaChunks())
            net.krodark.asterion.worldgen.AuthoredCatacombs.placeArenaChunk(
                    level,level.getChunk(pos.x(),pos.z()));
        finishBossArenaBuildIfReady(level);
        return isBossArenaReady();
    }
    /** Key insertion arms the encounter even if latency misses the exact doorway plane. */
    public static void requestBossArenaStart(ServerPlayer player) {
        if(player.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            ensureBossArenaReady((ServerLevel)player.level());
            BOSS_START_REQUESTS.put(player.getUUID(),player.level().getGameTime()
                    +net.krodark.asterion.block.MinotaurDoorMotion.OPEN_TICKS);
        }
    }
    public static void clearBossEntryTracking() {
        BOSS_ENTRANTS.clear();
        BOSS_START_REQUESTS.clear();
        ARENA_PREVIOUS_POSITIONS.clear();
    }

    public static int bossPillarsRemaining() {
        if (bossArenaBuild == null) return AsterionConfig.INSTANCE.minotaurBossPillarCount;
        if (net.krodark.asterion.worldgen.AuthoredCatacombs.enabled()
                && bossArenaBuild.pillars.isEmpty()) return 12;
        return (int)bossArenaBuild.pillars.stream().filter(pillar -> !pillar.broken).count();
    }

    public static int activeBossBraziers(ServerLevel level) {
        return net.krodark.asterion.worldgen.CatacombArena.litBraziers(level).size();
    }

    public static boolean breakBossPillar(ServerLevel level, AABB impact) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null || !build.ready) return false;
        ensureAuthoredBossPillars(level);
        for (BossPillar pillar : build.pillars) {
            if (pillar.broken) continue;
            BlockPos root = new BlockPos(pillar.x, pillar.y, pillar.z);
            BlockState anchor = level.getBlockState(root);
            int height = anchor.is(Asterion.PILLAR) ? anchor.getValue(net.krodark.asterion.block.PillarBlock.HEIGHT)
                    : bossPillarHeight(pillar.x, pillar.z);
            AABB bounds = new AABB(pillar.x - 1, pillar.y, pillar.z - 1,
                    pillar.x + 2, pillar.y + height, pillar.z + 2);
            if (!impact.intersects(bounds)) continue;
            destroyBossPillar(level, pillar, root, height);
            return true;
        }
        return false;
    }

    /** Debug and scripted path: destroys every currently intact authored arena pillar. */
    public static int destroyAllBossPillars(ServerLevel level) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null || !build.ready) return 0;
        ensureAuthoredBossPillars(level);
        int destroyed = 0;
        for (BossPillar pillar : build.pillars) {
            if (pillar.broken) continue;
            BlockPos root = new BlockPos(pillar.x, pillar.y, pillar.z);
            BlockState anchor = level.getBlockState(root);
            int height = anchor.is(Asterion.PILLAR)
                    ? anchor.getValue(net.krodark.asterion.block.PillarBlock.HEIGHT)
                    : bossPillarHeight(pillar.x, pillar.z);
            destroyBossPillar(level, pillar, root, height);
            destroyed++;
        }
        return destroyed;
    }

    private static void destroyBossPillar(ServerLevel level, BossPillar pillar, BlockPos root, int height) {
        pillar.broken = true;
        net.krodark.asterion.block.PillarBlock.removeStructure(level, root, height);
        for (int fragment = 0; fragment < 28; fragment++) {
            double angle = fragment * 2.399963229728653;
            double speed = 0.22D + level.getRandom().nextDouble() * 0.32D;
            Vec3 origin = Vec3.atBottomCenterOf(root).add(Math.cos(angle),
                    (fragment + 0.5D) * height / 28D, Math.sin(angle));
            net.krodark.asterion.worldgen.ArenaDebris.queue(level, origin,
                    new Vec3(Math.cos(angle) * speed, 0.28D + level.getRandom().nextDouble() * 0.36D,
                            Math.sin(angle) * speed));
        }
        level.sendParticles(ParticleTypes.EXPLOSION, pillar.x + 0.5D, BOSS_FLOOR_Y + 7.0D,
                pillar.z + 0.5D, 12, 1.2D, 5.0D, 1.2D, 0.04D);
        // The roof immediately sheds weight above a failed support, foreshadowing the
        // phase transition instead of leaving the ceiling visually unaffected.
        double roofY = net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_BASE_Y + 46.0D;
        for (int fragment = 0; fragment < 22; fragment++) {
            Vec3 origin = new Vec3(pillar.x + .5D + (level.getRandom().nextDouble() - .5D) * 8.0D,
                    roofY - level.getRandom().nextDouble() * 2.0D,
                    pillar.z + .5D + (level.getRandom().nextDouble() - .5D) * 8.0D);
            net.krodark.asterion.worldgen.ArenaDebris.queue(level, origin,
                    new Vec3((level.getRandom().nextDouble() - .5D) * .18D,
                            -.46D - level.getRandom().nextDouble() * .42D,
                            (level.getRandom().nextDouble() - .5D) * .18D),
                    .7F + level.getRandom().nextFloat() * 1.15F);
        }
        level.sendParticles(ParticleTypes.DUST_PLUME, pillar.x + .5D, roofY, pillar.z + .5D,
                48, 4.2D, .7D, 4.2D, .055D);
        level.playSound(null, new BlockPos(pillar.x, BOSS_FLOOR_Y + 2, pillar.z),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.2F, 0.55F);
    }

    public static Vec3 bossPillarChargeTarget(Vec3 boss, Vec3 player) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null) return null;
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (BossPillar pillar : build.pillars) {
            if (pillar.broken) continue;
            Vec3 target = new Vec3(pillar.x + 0.5D, BOSS_FLOOR_Y + 1.0D, pillar.z + 0.5D);
            Vec3 line = target.subtract(boss);
            double length = line.horizontalDistance();
            if (length < 4.0D) continue;
            Vec3 direction = new Vec3(line.x, 0, line.z).normalize();
            Vec3 toPlayer = player.subtract(boss);
            double along = new Vec3(toPlayer.x, 0, toPlayer.z).dot(direction);
            Vec3 nearest = boss.add(direction.scale(Mth.clamp(along, 0.0D, length)));
            double laneDistance = new Vec3(player.x - nearest.x, 0, player.z - nearest.z).length();
            // A pillar charge should read as an attempt to run through the player,
            // with the pillar visibly behind them. Wide side-on lanes made him stare
            // at masonry and then sprint away from the player.
            if (along <= 2.5D || along >= length - 2.5D || laneDistance > 2.15D) continue;
            Vec3 towardPlayer = new Vec3(toPlayer.x, 0, toPlayer.z).normalize();
            if (towardPlayer.dot(direction) < 0.975D) continue;
            double score = laneDistance * 8.0D + length;
            if (score < bestScore) { bestScore = score; best = target; }
        }
        return best;
    }

    public static Vec3 bossPillarSetupPosition(Vec3 player) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null) return bossArenaCenter();
        BossPillar chosen = null;
        double best = Double.MAX_VALUE;
        for (BossPillar pillar : build.pillars) if (!pillar.broken) {
            double distance = player.distanceToSqr(pillar.x + 0.5D, player.y, pillar.z + 0.5D);
            if (distance < best) { best = distance; chosen = pillar; }
        }
        if (chosen == null) return bossArenaCenter();
        Vec3 pillar = new Vec3(chosen.x + 0.5D, BOSS_FLOOR_Y + 1.0D, chosen.z + 0.5D);
        Vec3 away = pillar.subtract(player);
        away = new Vec3(away.x, 0, away.z);
        if (away.lengthSqr() < 0.01D) away = new Vec3(1, 0, 0);
        Vec3 setup = pillar.add(away.normalize().scale(10.0D));
        double radius = Math.sqrt(setup.x * setup.x + setup.z * setup.z);
        if (radius > PIT_HALF_WIDTH - 4.0D)
            setup = new Vec3(setup.x / radius * (PIT_HALF_WIDTH - 4.0D), setup.y,
                    setup.z / radius * (PIT_HALF_WIDTH - 4.0D));
        return setup;
    }

    public static Vec3 bossArenaTacticalWaypoint(Vec3 from, Vec3 target) {
        Vec3 desired = new Vec3(target.x - from.x, 0, target.z - from.z);
        if (desired.lengthSqr() < 0.01D) return target;
        desired = desired.normalize();
        BossArenaBuild build = bossArenaBuild;
        if (build != null) for (BossPillar pillar : build.pillars) {
            if (pillar.broken) continue;
            Vec3 away = new Vec3(from.x - (pillar.x + 0.5D), 0, from.z - (pillar.z + 0.5D));
            double distance = away.length();
            if (distance < 9.0D && distance > 0.01D)
                desired = desired.add(away.normalize().scale((9.0D - distance) * 0.22D));
        }
        if (desired.lengthSqr() < 0.01D) desired = new Vec3(1, 0, 0);
        Vec3 waypoint = from.add(desired.normalize().scale(6.0D));
        return clampBossArena(waypoint);
    }

    public static Vec3 clampBossArena(Vec3 point) {
        double radius = Math.sqrt(point.x * point.x + point.z * point.z);
        double limit = PIT_HALF_WIDTH - 4.5D;
        if (radius <= limit) return new Vec3(point.x, BOSS_FLOOR_Y + 1.0D, point.z);
        return new Vec3(point.x / radius * limit, BOSS_FLOOR_Y + 1.0D, point.z / radius * limit);
    }

    public static void scarBossArena(ServerLevel level, Vec3 origin, int radius) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            double distance = Math.sqrt(x * x + z * z);
            if (distance > radius || Math.floorMod(x * 17 + z * 29, 4) != 0) continue;
            cursor.set(Mth.floor(origin.x) + x, BOSS_FLOOR_Y, Mth.floor(origin.z) + z);
            if (net.krodark.asterion.worldgen.CatacombArena.puddle(cursor.getX(), cursor.getZ())) continue;
            if (!level.getBlockState(cursor).isAir()) {
                if (distance < Math.min(2.2D, radius * 0.30D)
                        && level.getEntitiesOfClass(ServerPlayer.class, new AABB(cursor).inflate(0.05D),
                        ServerPlayer::isAlive).isEmpty())
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                else level.setBlock(cursor, distance < radius * 0.35D
                        ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                        : Blocks.DEEPSLATE_TILES.defaultBlockState(), 2);
            }
        }
    }

    public static int clearLowBossChargeObstacle(ServerLevel level, AABB impact) {
        int cleared = 0;
        for (int x = Mth.floor(impact.minX); x <= Mth.floor(impact.maxX); x++)
            for (int z = Mth.floor(impact.minZ); z <= Mth.floor(impact.maxZ); z++)
                for (int y = BOSS_FLOOR_Y + 1; y <= BOSS_FLOOR_Y + 4; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!(state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.TUFF)
                        || state.is(Blocks.COBBLESTONE) || state.is(Blocks.GRAVEL))) continue;
                level.destroyBlock(pos, false);
                cleared++;
            }
        return cleared;
    }

    private static void tickBossArenaDebris(ServerLevel level) {
        if (!bossArenaPrepared || (level.getGameTime() % 10L) != 0L) return;
        if(level.players().stream().noneMatch(player->player.isAlive()
                && player.distanceToSqr(.5,BOSS_FLOOR_Y+1,.5)<96D*96D))return;
        int diameter = PIT_HALF_WIDTH * 2 + 1;
        long phase = level.getGameTime() / 10L;
        for (int sample = 0; sample < 96; sample++) {
            long roll = mix(level.getSeed() ^ phase * 0x9E3779B97F4A7C15L
                    ^ sample * 0xD1B54A32D192ED03L);
            int x = (int)Math.floorMod(roll, diameter) - PIT_HALF_WIDTH;
            int z = (int)Math.floorMod(roll >>> 24, diameter) - PIT_HALF_WIDTH;
            if (x * x + z * z > PIT_HALF_WIDTH * PIT_HALF_WIDTH) continue;
            if(!level.getChunkSource().hasChunk(x>>4,z>>4))continue;
            for (int y = BOSS_FLOOR_Y + 1; y <= BOSS_FLOOR_Y + 8; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!(state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.TUFF)
                        || state.is(Blocks.COBBLESTONE) || state.is(Blocks.GRAVEL))) continue;
                if (!level.getEntities((Entity)null, new AABB(pos), Entity::isAlive).isEmpty()) continue;
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                if ((sample & 7) == 0)
                    level.sendParticles(ParticleTypes.ASH, x + 0.5D, y + 0.35D, z + 0.5D,
                            3, 0.25D, 0.18D, 0.25D, 0.01D);
                break;
            }
        }
        int outer = PIT_HALF_WIDTH + PIT_WALL_THICKNESS;
        AABB arena = new AABB(-outer, BOSS_FLOOR_Y, -outer,
                outer + 1, DIMENSION_CEILING_Y, outer + 1);
        for (FallingBlockEntity rubble : level.getEntitiesOfClass(FallingBlockEntity.class, arena))
            if (rubble.time > 72) rubble.discard();
    }

    public static void collapseBossRoofRing(ServerLevel level, Vec3 origin, int step) {
        BossArenaBuild build = bossArenaBuild;
        if ((!net.krodark.asterion.worldgen.AuthoredCatacombs.enabled() && build == null)
                || step < 0 || step > PIT_HALF_WIDTH) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        java.util.List<Vec3> launches = new java.util.ArrayList<>();
        int roofY = net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_BASE_Y + 47;
        double distance = Math.min(net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS,
                step * 1.78D);
        // Nine uneven primary faults crawl away from the impact. Alternating branches
        // split off later, producing forks and missing slabs instead of a geometric ring.
        for (int fault = 0; fault < 9; fault++) {
            long faultSeed = mix(level.getSeed() ^ fault * 0x9E3779B97F4A7C15L);
            double baseAngle = Mth.TWO_PI * fault / 9.0D
                    + ((faultSeed >>> 11) * 0x1.0p-53 - .5D) * .42D;
            double bend = Math.sin(step * .41D + fault * 1.73D) * .16D;
            fractureRoofAt(level, cursor, launches, origin, distance, baseAngle + bend,
                    roofY, fault % 3 == 0 ? 2 : 1);
            if (step > 9 && (fault & 1) == 0) {
                double branchDistance = Math.max(0.0D, distance - (step - 9) * .58D);
                double split = (fault & 2) == 0 ? .43D : -.47D;
                fractureRoofAt(level, cursor, launches, origin, branchDistance,
                        baseAngle + bend + split, roofY, 1);
            }
        }
        int count = Math.min(42, launches.size());
        for (int i = 0; i < count; i++) {
            Vec3 pos = launches.get(i);
            float scale = 1.25F + level.getRandom().nextFloat() * 1.15F;
            net.krodark.asterion.worldgen.ArenaDebris.queue(level, pos,
                    new Vec3((level.getRandom().nextDouble()-.5)*.12, -.48-level.getRandom().nextDouble()*.34,
                            (level.getRandom().nextDouble()-.5)*.12), scale);
            level.sendParticles(Asterion.DOOR_SMOKE, pos.x, pos.y, pos.z, 5, 1.1, .65, 1.1, .03);
            if ((i & 1) == 0)
                level.sendParticles(Asterion.DOOR_SMOKE, pos.x, BOSS_FLOOR_Y + .5, pos.z, 3, 1.4, .35, 1.4, .04);
        }
        if (step >= 34) cleanupUnsupportedRoofFixtures(level,
                net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS, -1, roofY);
    }

    private static boolean isPermanentUpperMazeBuild(BlockPos pos) {
        return pos.getY() >= FLOOR_Y + 1
                && !(Math.abs((long)pos.getX()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS
                && Math.abs((long)pos.getZ()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS);
    }

    private static boolean isPermanentUpperPlayerBlock(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isPermanentUpperMazeBuild(pos)) return false;
        Block expected = PLAYER_PLACED_BLOCKS.get(new BlockKey(level.dimension(), pos.immutable()));
        return expected != null && state.is(expected);
    }

    private static void fractureRoofAt(ServerLevel level, BlockPos.MutableBlockPos cursor,
                                       java.util.List<Vec3> launches, Vec3 center, double distance,
                                       double angle, int width, int roofY) {
        int centerX = Mth.floor(center.x + Math.cos(angle) * distance);
        int centerZ = Mth.floor(center.z + Math.sin(angle) * distance);
        for (int dx = -width; dx <= width; dx++) for (int dz = -width; dz <= width; dz++) {
            if (dx * dx + dz * dz > width * width + 1) continue;
            int x = centerX + dx, z = centerZ + dz;
            int underside = findArenaRoofUnderside(level, cursor, x, z, roofY);
            if (underside < 0) continue;
            boolean removed = false;
            for (int y = underside; y <= roofY; y++) {
                cursor.set(x, y, z);
                if (level.getBlockState(cursor).isAir()) continue;
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                removed = true;
            }
            if (removed) launches.add(new Vec3(x + .5D, underside - .15D, z + .5D));
        }
    }

    /**
     * Finds the visible underside of the authored vault rather than assuming a flat
     * ceiling. A short air run below the candidate avoids mistaking furniture and
     * hanging fixtures for the roof while still following its low outer arches.
     */
    private static int findArenaRoofUnderside(ServerLevel level, BlockPos.MutableBlockPos cursor,
                                              int x, int z, int roofY) {
        int firstY = BOSS_FLOOR_Y + 8;
        for (int y = firstY; y <= roofY; y++) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).isAir()) continue;
            int clearBelow = 0;
            for (int below = 1; below <= 4; below++) {
                cursor.set(x, y - below, z);
                if (level.getBlockState(cursor).isAir()) clearBelow++;
            }
            if (clearBelow >= 3) return y;
        }
        return -1;
    }

    private static void cleanupUnsupportedRoofFixtures(ServerLevel level, int outerRadius,
                                                        int innerRadius, int roofY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        java.util.List<BlockPos> remove = new java.util.ArrayList<>();
        for (int x = -outerRadius; x <= outerRadius; x++) for (int z = -outerRadius; z <= outerRadius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) <= innerRadius) continue;
            for (int y = BOSS_FLOOR_Y; y <= roofY; y++) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.is(Asterion.MAZESTEEL_CHAIN)) {
                    BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos(x, y + 1, z);
                    while (above.getY() <= roofY && level.getBlockState(above).is(Asterion.MAZESTEEL_CHAIN))
                        above.move(Direction.UP);
                    BlockState anchor = level.getBlockState(above);
                    if (!Block.canSupportCenter(level, above, Direction.DOWN)) remove.add(cursor.immutable());
                } else if (isArenaTorch(state) && !arenaTorchSupported(level,cursor,state)) {
                    remove.add(cursor.immutable());
                } else if (state.is(Blocks.SPRUCE_TRAPDOOR) && !hasFixtureSupport(level,cursor)) {
                    remove.add(cursor.immutable());
                }
            }
        }
        for (BlockPos pos : remove) if (level.getBlockState(pos).is(Asterion.MAZESTEEL_CHAIN)
                || isArenaTorch(level.getBlockState(pos))
                || level.getBlockState(pos).is(Blocks.SPRUCE_TRAPDOOR)) level.destroyBlock(pos, false);
    }

    private static boolean arenaTorchSupported(ServerLevel level,BlockPos pos,BlockState state) {
        if(state.getBlock() instanceof net.krodark.asterion.block.GreekFireTorchBlock torch&&!torch.wall) {
            BlockPos.MutableBlockPos bottom=pos.mutable();
            while(level.getBlockState(bottom.below()).getBlock()==state.getBlock())bottom.move(Direction.DOWN);
            return level.getBlockState(bottom).canSurvive(level,bottom);
        }
        return state.canSurvive(level,pos);
    }

    private static boolean hasFixtureSupport(ServerLevel level,BlockPos pos) {
        for(Direction direction:Direction.values()) {
            BlockPos support=pos.relative(direction);
            if(level.getBlockState(support).isFaceSturdy(level,support,direction.getOpposite()))return true;
        }
        return false;
    }

    private static boolean isArenaTorch(BlockState state) {
        Block block = state.getBlock();
        return block instanceof net.krodark.asterion.block.GreekFireTorchBlock
                || block instanceof net.minecraft.world.level.block.TorchBlock
                || block instanceof net.minecraft.world.level.block.WallTorchBlock;
    }

    public static boolean buryBossInRubble(ServerLevel level, Vec3 origin) {
        if (bossArenaBuild == null) return false;
        bossArenaBuild.buriedUntil = level.getGameTime() + 80;
        bossArenaBuild.buriedPosition = origin;
        for (int i = 0; i < 80; i++) {
            double angle = level.getRandom().nextDouble() * Mth.TWO_PI;
            double radius = 1.5 + level.getRandom().nextDouble() * 3.5;
            Vec3 pos = origin.add(Math.cos(angle) * radius, 4 + level.getRandom().nextDouble() * 5, Math.sin(angle) * radius);
            net.krodark.asterion.worldgen.ArenaDebris.queue(level, pos, new Vec3(0, -.35, 0));
        }
        level.sendParticles(Asterion.DOOR_SMOKE, origin.x, BOSS_FLOOR_Y + 2, origin.z, 100, 3.8, 1.8, 3.8, .07);
        return true;
    }

    public static boolean isBossBuried(ServerLevel level, Vec3 origin) {
        return bossArenaBuild != null && level.getGameTime() < bossArenaBuild.buriedUntil
                && bossArenaBuild.buriedPosition != null && origin.distanceToSqr(bossArenaBuild.buriedPosition) < 16;
    }

    public static void explodeBossRubble(ServerLevel level, Vec3 origin) {
        if (bossArenaBuild != null) bossArenaBuild.buriedUntil = 0;
        for (int i = 0; i < 56; i++) {
            Vec3 away = new Vec3(level.getRandom().nextDouble() - .5, .2 + level.getRandom().nextDouble() * .3,
                    level.getRandom().nextDouble() - .5).normalize();
            net.krodark.asterion.worldgen.ArenaDebris.queue(level, origin.add(away.scale(2.5)).add(0, 1, 0),
                    away.scale(.65 + level.getRandom().nextDouble() * .65));
        }
        for (var player : level.players()) if (player.distanceToSqr(origin) < 96 * 96
                && ServerPlayNetworking.canSend(player, RagdollExplosionPayload.TYPE))
            ServerPlayNetworking.send(player, new RagdollExplosionPayload(origin, 10));
    }

    public static boolean isElectrified(LivingEntity entity) {
        return entity != null && ELECTRIFIED.containsKey(entity.getUUID());
    }

    public static void clearRuntimeState(MinecraftServer server) {
        RESET_DEATHS.clear();
        BossArenaEncounter.clear();
        DeadSunEventSystem.clearRuntimeState(server);
        ServerLevel maze = server.getLevel(Asterion.ASTERION_LEVEL);
        if (maze != null) for (var entry : ELECTRIFIED.entrySet()) {
            Entity entity = maze.getEntityInAnyDimension(entry.getKey());
            if (entity instanceof Mob mob) mob.setNoAi(entry.getValue().wasNoAi);
        }
        for (var entry : PENDING_TRANSITIONS.entrySet()) {
            PendingTransition pending = entry.getValue();
            pending.maze.getChunkSource().removeTicketWithRadius(
                    TicketType.PORTAL, pending.destinationChunk, 1);
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.setInvulnerable(pending.wasInvulnerable);
                player.setNoGravity(pending.hadNoGravity);
                player.noPhysics = pending.wasNoPhysics;
                player.setDeltaMovement(Vec3.ZERO);
            }
        }
        for (PhasingEntity phase : PHASING_ENTITIES.values()) {
            if (phase.entity.isRemoved()) continue;
            phase.entity.noPhysics = phase.wasNoPhysics;
            phase.entity.setNoGravity(phase.hadNoGravity);
            phase.entity.setDeltaMovement(phase.originalVelocity);
        }
        ELECTRIFIED.clear();
        ROAMER_REVEAL_TICKS.clear();
        BOSS_ENTRANTS.clear();
        BOSS_START_REQUESTS.clear();
        bossArenaPrepared = false;
        ARENA_PREVIOUS_POSITIONS.clear();
        bossArenaBuild = null;
        bossFinale = null;
        PENDING_TRANSITIONS.clear();
        PRE_MAZE_RESPAWNS.clear();
        PHASING_ENTITIES.clear();
        ABOVE_WALL_TICKS.clear();
        WARD_FALL_PROTECTION.clear();
        LAST_PORTAL_SYNC.clear();
        LAST_BIOME_ATMOSPHERE.clear();
        GATEWAY_SURFACE_Y.clear();
        PLAYER_PLACED_BLOCKS.clear();
        DECAYING_BLOCKS.clear();
        RESTORING_BLOCKS.clear();
        MAZE_TOPOLOGIES.clear();
        prewarmSeed = Long.MIN_VALUE;
        prewarmIndex = 0;
        summonedPortal = null;
        MazeNbtStructures.clearRuntimeState();
        MazeBiomes.reset();
    }

    private static void electrify(LivingEntity entity, int durationTicks) {
        ELECTRIFIED.compute(entity.getUUID(), (ignored, existing) -> {
            if (existing != null) {
                existing.remainingTicks = Math.max(existing.remainingTicks, durationTicks);
                return existing;
            }
            boolean wasNoAi = entity instanceof Mob mob && mob.isNoAi();
            if (entity instanceof Mob mob) mob.setNoAi(true);
            return new ElectrifiedState(Math.max(1, durationTicks), wasNoAi);
        });
    }

    private static void tickElectrified(LivingEntity entity) {
        ElectrifiedState state = ELECTRIFIED.get(entity.getUUID());
        if (state == null) return;
        if (entity instanceof Mob mob) mob.setNoAi(true);
        if (--state.remainingTicks > 0 && entity.isAlive()) return;
        if (entity instanceof Mob mob) mob.setNoAi(state.wasNoAi);
        ELECTRIFIED.remove(entity.getUUID());
    }

    private static void tickMazeWard(LivingEntity entity) {
        if (entity instanceof BombadierBeetleEntity beetle) {
            ABOVE_WALL_TICKS.remove(entity.getUUID());
            WARD_FALL_PROTECTION.remove(entity.getUUID());
            ElectrifiedState oldState = ELECTRIFIED.remove(entity.getUUID());
            if (oldState != null) beetle.setNoAi(oldState.wasNoAi);
            return;
        }
        Integer protection = WARD_FALL_PROTECTION.get(entity.getUUID());
        if (protection != null) {
            entity.resetFallDistance();
            if (protection <= 1) {
                WARD_FALL_PROTECTION.remove(entity.getUUID());
            } else if (entity.onGround() && entity.getY() < mazeFloorY(((ServerLevel)entity.level()).getSeed(),
                    entity.getBlockX(), entity.getBlockZ(), AsterionConfig.INSTANCE.cellSize) + 4.0D) {
                WARD_FALL_PROTECTION.put(entity.getUUID(), Math.min(40, protection - 1));
            } else {
                WARD_FALL_PROTECTION.put(entity.getUUID(), protection - 1);
            }
        }
        AsterionConfig config = AsterionConfig.INSTANCE;
        int mazeLimit = config.mazeRadiusCells * config.cellSize;
        boolean exemptPlayer = entity instanceof ServerPlayer player && (player.isCreative() || player.isSpectator());
        boolean aboveMaze = Math.abs(entity.getX()) < mazeLimit && Math.abs(entity.getZ()) < mazeLimit
                && entity.getY() > mazeFloorY(((ServerLevel)entity.level()).getSeed(), entity.getBlockX(), entity.getBlockZ(),
                        config.cellSize) + config.wallHeight + 0.25D;
        if (exemptPlayer || !aboveMaze) {
            ABOVE_WALL_TICKS.remove(entity.getUUID());
            return;
        }
        int ticks = ABOVE_WALL_TICKS.merge(entity.getUUID(), 1, Integer::sum);
        if (ticks < config.wallZapDelayTicks) return;
        ABOVE_WALL_TICKS.put(entity.getUUID(), 0);

        Vec3 source = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ);
        int chargeTicks = 60;
        Vec3 corridor = nearestMazeCorridor(entity.getX(), entity.getZ());
        Vec3 inward = corridor.subtract(entity.position());
        if (inward.horizontalDistanceSqr() < 0.01D)
            inward = new Vec3(-entity.getX(), 0.0D, -entity.getZ());
        inward = new Vec3(inward.x, 0.0D, inward.z).normalize();
        Vec3 launch = new Vec3(inward.x * 3.10D, -1.60D, inward.z * 3.10D);
        MazeZapPayload payload = new MazeZapPayload(entity.getId(), source, launch, chargeTicks);
        Set<ServerPlayer> viewers = new HashSet<>(PlayerLookup.tracking(entity));
        if (entity instanceof ServerPlayer player) viewers.add(player);
        viewers.forEach(viewer -> {
            if (ServerPlayNetworking.canSend(viewer, MazeZapPayload.TYPE))
                ServerPlayNetworking.send(viewer, payload);
        });
        ServerLevel level = (ServerLevel) entity.level();
        entity.hurtServer(level, entity.damageSources().lightningBolt(), 2.0F);

        entity.setDeltaMovement(launch);
        entity.hurtMarked = true;
        entity.resetFallDistance();
        electrify(entity, chargeTicks);
        WARD_FALL_PROTECTION.put(entity.getUUID(), 240);
    }

    public static Vec3 nearestMazeCorridor(double x, double z) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        int size = config.mazeRadiusCells * 2;
        int limit = config.mazeRadiusCells * config.cellSize;
        int gx = Math.floorDiv(Mth.floor(x) + limit, config.cellSize);
        int gz = Math.floorDiv(Mth.floor(z) + limit, config.cellSize);
        int center = config.wallThickness + (config.cellSize - config.wallThickness) / 2;
        double corridorX = -limit + gx * config.cellSize + center + 0.5D;
        double corridorZ = -limit + gz * config.cellSize + center + 0.5D;
        return new Vec3(corridorX, mazeFloorY(activeMazeTerrainSeed, Mth.floor(corridorX),
                Mth.floor(corridorZ), config.cellSize) + 1.0D, corridorZ);
    }

    public static boolean isApproachingCenter(Vec3 position) {
        double trigger = AsterionConfig.INSTANCE.cellSize * 7.0D;
        return Math.max(Math.abs(position.x), Math.abs(position.z)) <= trigger;
    }

    public static double volumetricDustDensity(Vec3 point, long gameTime) {
        double speed = AsterionConfig.INSTANCE.shaderAnimationSpeed;
        Vec3 wind = new Vec3(gameTime * 0.006D, gameTime * 0.0015D, -gameTime * 0.004D).scale(speed);
        double banks = volumeNoise(point.add(wind).multiply(0.032D, 0.052D, 0.032D));
        double wisps = volumeNoise(point.subtract(wind.scale(1.4D)).multiply(0.080D, 0.024D, 0.080D)
                .add(17.0D, 3.0D, -9.0D));
        double circulation = Math.sin(Math.atan2(point.z, point.x) * 4.0D
                + Math.sqrt(point.x * point.x + point.z * point.z) * 0.034D
                - gameTime * 0.012D * speed) * 0.045D;
        double lowAir = 1.0D - Mth.clamp((point.y - 28.0D) / 84.0D, 0.0D, 1.0D);
        double t = Mth.clamp((banks * 0.64D + wisps * 0.36D + circulation - 0.26D) / 0.46D,
                0.0D, 1.0D);
        return t * t * (3.0D - 2.0D * t) * Mth.lerp(lowAir, 0.70D, 1.14D);
    }

    private static double volumeNoise(Vec3 point) {
        int x = Mth.floor(point.x), y = Mth.floor(point.y), z = Mth.floor(point.z);
        double fx = point.x - x, fy = point.y - y, fz = point.z - z;
        fx = fx * fx * (3.0D - 2.0D * fx);
        fy = fy * fy * (3.0D - 2.0D * fy);
        fz = fz * fz * (3.0D - 2.0D * fz);
        double x00 = Mth.lerp(fx, volumeHash(x, y, z), volumeHash(x + 1, y, z));
        double x10 = Mth.lerp(fx, volumeHash(x, y + 1, z), volumeHash(x + 1, y + 1, z));
        double x01 = Mth.lerp(fx, volumeHash(x, y, z + 1), volumeHash(x + 1, y, z + 1));
        double x11 = Mth.lerp(fx, volumeHash(x, y + 1, z + 1), volumeHash(x + 1, y + 1, z + 1));
        return Mth.lerp(fz, Mth.lerp(fy, x00, x10), Mth.lerp(fy, x01, x11));
    }

    private static double volumeHash(int x, int y, int z) {
        long value = x * 0x632BE59BD9B4E019L ^ y * 0x9E3779B97F4A7C15L ^ z * 0x94D049BB133111EBL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return (value >>> 11) * 0x1.0p-53;
    }

    public static boolean hasReachedMazeCenter(Vec3 position) {
        double radius = PIT_HALF_WIDTH - 2.0D;
        return position.x * position.x + position.z * position.z <= radius * radius
                && position.y >= BOSS_FLOOR_Y + 0.5D;
    }

    public static boolean hasEnteredCenterPerimeter(Vec3 position) {
        double radius = PIT_HALF_WIDTH + PIT_WALL_THICKNESS + 4.0D;
        return position.x * position.x + position.z * position.z <= radius * radius
                && position.y > BOSS_FLOOR_Y + 1.0D;
    }

    public static GreekRune runeForMazePosition(Vec3 position) {
        return GreekRune.forRadius(position.x, position.z);
    }

    public static boolean isBossEncounterActive(ServerLevel level) {
        if (bossFinale != null) return true;
        AABB centerSearch = new AABB(-128.0D, level.getMinY(), -128.0D,
                128.0D, level.getMaxY(), 128.0D);
        for (MinotaurEntity minotaur : level.getEntitiesOfClass(MinotaurEntity.class, centerSearch))
            if (minotaur.isAlive() && minotaur.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS)
                return true;
        return false;
    }

    public static boolean isInsideBossArena(Vec3 position) {
        double radius = PIT_HALF_WIDTH + 0.5D;
        return position.x * position.x + position.z * position.z <= radius * radius
                && position.y >= BOSS_FLOOR_Y - 2.0D && position.y <= FLOOR_Y - 2.0D;
    }

    public static Vec3 bossArenaCenter() {
        return new Vec3(0.5D, BOSS_FLOOR_Y + 1.0D, 0.5D);
    }

    public static Vec3 bossArenaApproach(Vec3 from) {
        double ax = Math.abs(from.x), az = Math.abs(from.z);
        double rim = PIT_HALF_WIDTH + PIT_WALL_THICKNESS + 2.5D;
        if (ax > az) return new Vec3(Math.copySign(rim, from.x == 0 ? 1 : from.x), FLOOR_Y + 1.0D, 0.5D);
        return new Vec3(0.5D, FLOOR_Y + 1.0D, Math.copySign(rim, from.z == 0 ? 1 : from.z));
    }

    public static Vec3 nextMazeWaypoint(ServerLevel level, Vec3 from, Vec3 target,
                                        double bodyWidth, double bodyHeight, int maxVisitedCells) {
        List<Vec3> route = mazeRoute(level, from, target, bodyWidth, bodyHeight, maxVisitedCells);
        return route.isEmpty() ? null : route.get(0);
    }

    public static List<Vec3> mazeRoute(ServerLevel level, Vec3 from, Vec3 target,
                                       double bodyWidth, double bodyHeight, int maxVisitedCells) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        int cell = config.cellSize;
        int limit = config.mazeRadiusCells * cell;
        int startX = Math.floorDiv(Mth.floor(from.x) + limit, cell);
        int startZ = Math.floorDiv(Mth.floor(from.z) + limit, cell);
        int targetX = Math.floorDiv(Mth.floor(target.x) + limit, cell);
        int targetZ = Math.floorDiv(Mth.floor(target.z) + limit, cell);
        if (startX == targetX && startZ == targetZ) return List.of(target);

        long start = cellKey(startX, startZ), goal = cellKey(targetX, targetZ);
        PriorityQueue<RouteNode> open = new PriorityQueue<>(Comparator.comparingDouble(RouteNode::score));
        Map<Long, Long> previous = new HashMap<>();
        Map<Long, Integer> costs = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        open.add(new RouteNode(start, mazeHeuristic(startX, startZ, targetX, targetZ)));
        previous.put(start, start);
        costs.put(start, 0);
        int[] dx = {0, 1, 0, -1}, dz = {-1, 0, 1, 0};
        int visited = 0;
        while (!open.isEmpty() && visited++ < Math.max(32, maxVisitedCells)) {
            long current = open.remove().key();
            if (!closed.add(current)) continue;
            int gx = (int) (current >> 32), gz = (int) current;
            for (int direction = 0; direction < 4; direction++) {
                int nx = gx + dx[direction], nz = gz + dz[direction];
                if (nx < 0 || nz < 0 || nx >= config.mazeRadiusCells * 2
                        || nz >= config.mazeRadiusCells * 2) continue;
                long next = cellKey(nx, nz);
                if (closed.contains(next)
                        || !liveCorridorClear(level, gx, gz, nx, nz, config, bodyWidth, bodyHeight)) continue;
                int nextCost = costs.get(current) + 10;
                if (nextCost >= costs.getOrDefault(next, Integer.MAX_VALUE)) continue;
                previous.put(next, current);
                costs.put(next, nextCost);
                if (next == goal) {
                    ArrayList<Vec3> route = new ArrayList<>();
                    long step = next;
                    while (step != start) {
                        route.add(cellCenter((int) (step >> 32), (int) step, config));
                        step = previous.get(step);
                    }
                    java.util.Collections.reverse(route);
                    return route;
                }
                open.add(new RouteNode(next,
                        nextCost + mazeHeuristic(nx, nz, targetX, targetZ) * 10.0D));
            }
        }
        return List.of();
    }

    private static int mazeHeuristic(int x, int z, int targetX, int targetZ) {
        return Math.abs(targetX - x) + Math.abs(targetZ - z);
    }

    private record RouteNode(long key, double score) { }

    private static boolean liveCorridorClear(ServerLevel level, int ax, int az, int bx, int bz,
                                             AsterionConfig config, double width, double height) {
        Vec3 a = cellCenter(ax, az, config), b = cellCenter(bx, bz, config);
        int steps = config.cellSize;
        int lateral = Math.max(1, Mth.ceil(width * 0.5D));
        int vertical = Math.max(3, Mth.ceil(height));
        boolean alongX = ax != bx;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int x = Mth.floor(Mth.lerp(t, a.x, b.x));
            int z = Mth.floor(Mth.lerp(t, a.z, b.z));
            if (!level.hasChunk(x >> 4, z >> 4)) return false;
            for (int side = -lateral; side <= lateral; side++) for (int y = 0; y < vertical; y++) {
                int sx = alongX ? x : x + side;
                int sz = alongX ? z + side : z;
                int floorY = mazeFloorY(level.getSeed(), sx, sz, config.cellSize);
                BlockState state = level.getBlockState(cursor.set(sx, floorY + 1 + y, sz));
                if (!state.isAir() && !state.is(Blocks.COBWEB)) return false;
            }
        }
        return true;
    }

    private static Vec3 cellCenter(int gx, int gz, AsterionConfig config) {
        int limit = config.mazeRadiusCells * config.cellSize;
        int center = config.wallThickness + (config.cellSize - config.wallThickness) / 2;
        double x = -limit + gx * config.cellSize + center + 0.5D;
        double z = -limit + gz * config.cellSize + center + 0.5D;
        return new Vec3(x, mazeFloorY(activeMazeTerrainSeed, Mth.floor(x), Mth.floor(z),
                config.cellSize) + 1.0D, z);
    }

    private static long cellKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public static boolean isCenterAccessProtected(BlockPos pos) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        int centerSafeRadius = config.cellSize * 5;
        if (Math.abs(pos.getX()) < centerSafeRadius && Math.abs(pos.getZ()) < centerSafeRadius)
            return true;
        MazeTopology topology = MAZE_TOPOLOGIES.values().stream().findFirst().orElse(null);
        if (topology == null) return false;
        int halfCorridor = (config.cellSize - config.wallThickness) / 2;
        for (int offset = -halfCorridor; offset <= halfCorridor; offset++) {
            if (topology.onSolutionTrail(pos.getX() + offset, pos.getZ(), config.cellSize,
                    config.mazeRadiusCells)
                    || topology.onSolutionTrail(pos.getX(), pos.getZ() + offset, config.cellSize,
                    config.mazeRadiusCells)) return true;
        }
        return false;
    }

    private static void beginTransition(ServerPlayer player, ServerLevel maze) {
        if (prewarmSeed != maze.getSeed()) {
            prewarmSeed = maze.getSeed();
            prewarmIndex = 0;
        }
        BlockPos destination = randomMazeArrival(maze, player.getUUID(), maze.getGameTime());
        PendingTransition pending = new PendingTransition(maze, destination,
                player.isInvulnerable(), player.isNoGravity(), player.noPhysics);
        PENDING_TRANSITIONS.put(player.getUUID(), pending);
        maze.getChunkSource().addTicketWithRadius(TicketType.PORTAL, pending.destinationChunk, 1);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.noPhysics = true;
        player.setDeltaMovement(Vec3.ZERO);
        if (ServerPlayNetworking.canSend(player, DimensionTransitionPayload.TYPE))
            ServerPlayNetworking.send(player, new DimensionTransitionPayload(
                    PORTAL_FADE_IN_TICKS, PORTAL_BLACK_HOLD_TICKS));
    }

    private static void tickTransition(ServerPlayer player, PendingTransition pending) {
        try {
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if (!pending.teleported && pending.ticks < 6)
                player.setPos(player.getX(), player.getY() - (0.10D + pending.ticks * 0.012D), player.getZ());
            if (!pending.teleported && prewarmIndex < PREWARM_OFFSETS.length) {
                generateNextPrewarmChunk(pending.maze, pending.destination);
                pending.ticks++;
                return;
            }
            if (pending.preloadIndex < PRELOAD_OFFSETS.length) {
                int[] offset = PRELOAD_OFFSETS[pending.preloadIndex++];
                pending.maze.getChunk((pending.destination.getX() >> 4) + offset[0],
                        (pending.destination.getZ() >> 4) + offset[1]);
            }
            if (!pending.teleported
                    && pending.ticks < PORTAL_FADE_IN_TICKS + PORTAL_BLACK_HOLD_TICKS) {
                pending.ticks++;
                return;
            }
            if (!pending.teleported && pending.preloadIndex >= PRELOAD_OFFSETS.length) {
                prepareMazeArrival(pending.maze, pending.destination);
                long facingRoll = mix(pending.maze.getSeed() ^ pending.destination.asLong());
                float yaw = (float) Math.floorMod(facingRoll, 360);
                player.teleportTo(pending.maze, pending.destination.getX() + 0.5,
                        skyfallY(), pending.destination.getZ() + 0.5,
                        java.util.Set.of(), yaw, 0, true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                pending.teleported = true;
            }
            if (pending.teleported && !pending.clientReady) {
                player.setPos(pending.destination.getX() + 0.5D,
                        skyfallY(), pending.destination.getZ() + 0.5D);
            }
            pending.ticks++;
            if (pending.teleported && pending.clientReady) finishTransition(player, pending);
            else if (pending.ticks >= 400) {
                Asterion.LOGGER.warn("Transition ready acknowledgement timed out for {}; releasing safely",
                        player.getScoreboardName());
                finishTransition(player, pending);
            }
        } catch (RuntimeException exception) {
            Asterion.LOGGER.error("Asterion transition failed safely for {}", player.getScoreboardName(), exception);
            finishTransition(player, pending);
        }
    }

    public static void markTransitionReady(ServerPlayer player) {
        PendingTransition pending = PENDING_TRANSITIONS.get(player.getUUID());
        if (pending != null && pending.teleported
                && player.level().dimension().equals(Asterion.ASTERION_LEVEL))
            pending.clientReady = true;
        if (pending != null && pending.clientReady) {
            for (ServerPlayer listener : pending.maze.players())
                if (ServerPlayNetworking.canSend(listener, EntryOmenPayload.TYPE))
                    ServerPlayNetworking.send(listener, EntryOmenPayload.INSTANCE);
        }
    }

    private static void finishTransition(ServerPlayer player, PendingTransition pending) {
        pending.maze.getChunkSource().removeTicketWithRadius(
                TicketType.PORTAL, pending.destinationChunk, 1);
        player.setInvulnerable(pending.wasInvulnerable);
        player.setNoGravity(pending.hadNoGravity);
        player.noPhysics = pending.wasNoPhysics;
        if (pending.teleported && player.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            Vec3 fallVelocity = new Vec3(0.08D, -0.38D, -0.05D);
            player.setDeltaMovement(fallVelocity);
            player.hurtMarked = true;
            WARD_FALL_PROTECTION.put(player.getUUID(), 20 * 15);
            if (ServerPlayNetworking.canSend(player, RagdollImpulsePayload.TYPE)) {
                Vec3 tear = player.position().add(0.0D, 5.0D, 0.0D);
                ServerPlayNetworking.send(player, new RagdollImpulsePayload(tear, fallVelocity, 0.9F));
            }
        } else {
            player.setDeltaMovement(Vec3.ZERO);
        }
        player.resetFallDistance();
        PENDING_TRANSITIONS.remove(player.getUUID());
    }

    public static boolean hasFallProtection(Entity entity) {
        return entity != null && WARD_FALL_PROTECTION.containsKey(entity.getUUID());
    }

    private static BlockPos randomMazeArrival(ServerLevel maze, UUID entrant, long salt) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        int radius = config.mazeRadiusCells;
        int size = radius * 2;
        int limit = radius * config.cellSize;
        long roll = mix(maze.getSeed() ^ entrant.getMostSignificantBits()
                ^ Long.rotateLeft(entrant.getLeastSignificantBits(), 23) ^ salt);
        int centerCell = size / 2;
        int maxOffset = Math.max(6, Math.min(radius - 2,
                (2000 - config.cellSize) / Math.max(1, config.cellSize)));
        int usable = maxOffset * 2 + 1;
        int gx = centerCell - maxOffset + (int)Math.floorMod(roll, usable);
        int gz = centerCell - maxOffset + (int)Math.floorMod(roll >>> 24, usable);

        if (Math.abs(gx - centerCell) <= 2 && Math.abs(gz - centerCell) <= 2)
            gx = Math.min(centerCell + maxOffset, gx + 4);

        int corridorCenter = config.wallThickness
                + (config.cellSize - config.wallThickness) / 2;
        int x = -limit + gx * config.cellSize + corridorCenter;
        int z = -limit + gz * config.cellSize + corridorCenter;
        return new BlockPos(x, mazeFloorY(maze.getSeed(), x, z, config.cellSize) + 1, z);
    }

    private static int skyfallY() {
        return FLOOR_Y + AsterionConfig.INSTANCE.wallHeight + SKYFALL_CLEARANCE;
    }

    private static void prepareMazeArrival(ServerLevel maze, BlockPos arrival) {
        maze.getChunkAt(arrival);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        AsterionConfig config = AsterionConfig.INSTANCE;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int floorY = mazeFloorY(maze.getSeed(), arrival.getX() + dx, arrival.getZ() + dz,
                    config.cellSize);
            for (int depth = 0; depth < config.floorThickness; depth++)
                maze.setBlock(p.set(arrival.getX() + dx, floorY - depth, arrival.getZ() + dz),
                        Asterion.ANCIENT_STONE.defaultBlockState(), 2);
            int clearHeight = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? 15 : 5;
            for (int y = 1; y <= clearHeight; y++)
                maze.setBlock(p.set(arrival.getX() + dx, floorY + y, arrival.getZ() + dz),
                        Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void buildMazeChunk(ServerLevel level, LevelChunk chunk, BlockPos marker) {
        generateMazeChunk(chunk, net.krodark.asterion.worldgen.MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState()));
    }

    public static void generateMazeChunk(ChunkAccess chunk, long seed) {
        net.krodark.asterion.worldgen.CatacombLayout.generate(chunk, seed);
        AsterionConfig config = AsterionConfig.INSTANCE;
        int radius = config.mazeRadiusCells;
        int cell = config.cellSize;
        int thickness = config.wallThickness;
        int limit = radius * cell;
        MazeTopology topology = topology(seed, radius, config.mazeLoopChance, config.mazeLandmarkChance);
        MazeNbtStructures.Layout structures = ENABLE_MAZE_NBT_STRUCTURES
                ? MazeNbtStructures.generationLayout(seed) : MazeNbtStructures.emptyLayout();
        ChunkPos chunkPos = chunk.getPos();
        BlockPos marker = new BlockPos(chunkPos.getMinBlockX(), 1, chunkPos.getMinBlockZ());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int startX = chunkPos.getMinBlockX();
        int endX = chunkPos.getMaxBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int endZ = chunkPos.getMaxBlockZ();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {

                // The complete central chamber comes from arena_part1-9. Terrain generation
                // deliberately leaves this volume alone so no retired pit floor or wall can
                // appear before or underneath the authored templates.
                if (Math.abs(x) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS
                        && Math.abs(z) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS) continue;

                if (isPitOpening(x, z)) {
                    placeFloorColumn(chunk, p, seed, x, z, BOSS_FLOOR_Y, config.floorThickness,
                            topology, cell, radius);
                    continue;
                }

                int floorY = structures.floorY(x, z, mazeFloorY(seed, x, z, cell));
                placeFloorColumn(chunk, p, seed, x, z, floorY, config.floorThickness,
                        topology, cell, radius);

                if (isPitShaftWall(x, z)) {
                    for (int y = BOSS_FLOOR_Y + 1; y <= FLOOR_Y; y++)
                        bufferedSet(chunk, x, y, z, Asterion.ANCIENT_BRICKS.defaultBlockState());
                    continue;
                }

                MazeBiomes.Biome biome = mazeBiomeAt(seed, x, z, cell);
                int biomeWallHeight = biome.kind() == MazeBiomes.Kind.CRIMSON_MARSHLANDS
                        ? Math.min(DIMENSION_CEILING_Y - floorY - 2,
                                Math.max(config.wallHeight + 28, 56))
                        : config.wallHeight;
                boolean wall = isWall(topology, structures, seed, biome,
                        x, z, cell, thickness, radius);
                if (wall) {
                    boolean core = isMazeWallCore(topology, structures, seed, biome,
                            x, z, cell, thickness, radius);
                    for (int y = 1; y <= biomeWallHeight; y++)
                        bufferedSet(chunk, x, floorY + y, z,
                                core ? Asterion.MAZE_WALL_CORE.defaultBlockState()
                                        : patternedWall(seed, x, y, z, biome, cell, radius));
                    if (!core) placeBiomeWallDetail(chunk, seed, x, z, biomeWallHeight, biome, floorY);
                } else {
                    // Reserved footprints and their approaches are already shaped during normal
                    // chunk generation, so NBT placement never needs to rebuild the chunk later.
                    if (structures.reserved(x, z)) continue;
                    if (needsElevationSlab(seed, x, z, cell, floorY))
                        bufferedSet(chunk, x, floorY + 1, z,
                                Asterion.ANCIENT_STONE_SLAB.defaultBlockState());
                    if (biome.kind() != MazeBiomes.Kind.CRIMSON_MARSHLANDS
                            && isArchOpening(topology, x, z, cell, thickness, radius)) {
                        int archY = Math.max(9, biomeWallHeight / 3);
                        for (int y = archY; y <= archY + 2; y++)
                            bufferedSet(chunk, x, floorY + y, z,
                                    patternedWall(seed, x, y, z, biome, cell, radius));
                    }
                    // Cell-shaped motifs do not belong in the circular maze: against curved
                    // walls they became disconnected rectangular shelves in open space.
                    if (biome.kind() != MazeBiomes.Kind.CRIMSON_MARSHLANDS
                            && placeMazeMotifColumn(chunk, seed, x, z, cell, thickness,
                            biomeWallHeight, biome, radius, floorY)) continue;
                    placeDecorationColumn(chunk, p, topology, structures, seed, x, z, cell,
                            thickness, radius, biomeWallHeight, biome, floorY);
                }
            }
        }

        bufferedSet(chunk, marker.getX(), marker.getY(), marker.getZ(), Blocks.BEDROCK.defaultBlockState());
        for (LevelChunkSection section : chunk.getSections())
            if (!section.hasOnlyAir()) section.recalcBlockCounts();
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.markUnsaved();
        if (ENABLE_MAZE_NBT_STRUCTURES) structures.markTerrainGenerated(chunkPos);
        if (ENABLE_MAZE_NBT_STRUCTURES && chunk instanceof LevelChunk levelChunk)
            structures.onChunkBuilt(levelChunk);
    }

    private static void placeFloorColumn(ChunkAccess chunk, BlockPos.MutableBlockPos p, long seed,
                                         int x, int z, int topY, int depth, MazeTopology topology,
                                         int cell, int radius) {
        for (int layer = 0; layer < depth; layer++) {
            bufferedSet(chunk, x, topY - layer, z,
                    patternedFloor(seed, x, z, layer, topology, cell, radius).defaultBlockState());
        }
    }

    private static int mazeFloorY(long seed, int x, int z, int cell) {
        double centerDistance = Math.max(Math.abs(x), Math.abs(z));
        double flatRadius = cell * 7.0D;
        if (centerDistance <= flatRadius) return FLOOR_Y;
        double blend = Mth.clamp((centerDistance - flatRadius) / (cell * 5.0D), 0.0D, 1.0D);
        double phaseX = ((seed >>> 16) & 1023L) * 0.013D;
        double phaseZ = ((seed >>> 36) & 1023L) * 0.011D;
        double height = 2.65D
                + Math.sin(x / (cell * 8.5D) + phaseX) * 1.45D
                + Math.cos(z / (cell * 10.5D) + phaseZ) * 1.20D
                + Math.sin((x - z) / (cell * 6.5D) + phaseX * 0.37D) * 0.80D;
        return FLOOR_Y + Mth.clamp(Mth.floor(height * blend + 0.5D), 0, 6);
    }

    public static int mazeFloorHeight(long seed, int x, int z) {
        return mazeFloorY(seed, x, z, AsterionConfig.INSTANCE.cellSize);
    }

    public static long mazeTerrainSeed() {
        return activeMazeTerrainSeed;
    }

    public static void initializeMazeTerrain(ServerLevel level) {
        MazeBiomes.load(level);
        activeMazeTerrainSeed = net.krodark.asterion.worldgen.MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
    }

    public static boolean mazeBiomeHasFeature(long seed, int x, int z, String feature) {
        return mazeBiomeAt(seed, x, z, AsterionConfig.INSTANCE.cellSize).hasFeature(feature);
    }

    public static boolean mazeBiomeHasFeature(int x, int z, String feature) {
        return mazeBiomeAt(activeMazeTerrainSeed, x, z, AsterionConfig.INSTANCE.cellSize)
                .hasFeature(feature);
    }

    public static boolean isOvergrowthBiomeAt(double x, double z) {
        return mazeBiomeAt(activeMazeTerrainSeed, Mth.floor(x), Mth.floor(z),
                AsterionConfig.INSTANCE.cellSize).kind() == MazeBiomes.Kind.OVERGROWTH;
    }

    public static boolean isCrimsonMarshlandsAt(double x, double z) {
        return mazeBiomeAt(activeMazeTerrainSeed, Mth.floor(x), Mth.floor(z),
                AsterionConfig.INSTANCE.cellSize).kind() == MazeBiomes.Kind.CRIMSON_MARSHLANDS;
    }

    private static boolean needsElevationSlab(long seed, int x, int z, int cell, int floorY) {
        return mazeFloorY(seed, x + 1, z, cell) > floorY
                || mazeFloorY(seed, x - 1, z, cell) > floorY
                || mazeFloorY(seed, x, z + 1, cell) > floorY
                || mazeFloorY(seed, x, z - 1, cell) > floorY;
    }

    private static void bufferedSet(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(x & 15, y & 15, z & 15, state, false);
    }

    private static boolean isPitOpening(int x, int z) {
        return x * x + z * z <= PIT_HALF_WIDTH * PIT_HALF_WIDTH;
    }

    private static boolean isPitShaftWall(int x, int z) {
        int distanceSquared = x * x + z * z;
        int outer = PIT_HALF_WIDTH + PIT_WALL_THICKNESS;
        return distanceSquared > PIT_HALF_WIDTH * PIT_HALF_WIDTH && distanceSquared <= outer * outer;
    }

    private static boolean isCenterArena(int x, int z, int cell) {
        return Math.abs(x) < cell * 3 && Math.abs(z) < cell * 3;
    }

    private static boolean isWall(MazeTopology topology, MazeNbtStructures.Layout structures,
                                  long seed, MazeBiomes.Biome biome, int x, int z,
                                  int size, int thickness, int radius) {
        int limit = radius * size;
        if (isCenterArena(x, z, size)) return false;
        if (structures.reserved(x, z)) return false;
        if (biome.kind() == MazeBiomes.Kind.CRIMSON_MARSHLANDS)
            return isCircularMazeWall(seed, x, z, size, thickness);
        int gx = Math.floorDiv(x + limit, size);
        int gz = Math.floorDiv(z + limit, size);
        int lx = Math.floorMod(x + limit, size);
        int lz = Math.floorMod(z + limit, size);
        if (lx < thickness && lz < thickness) return true;
        if (lx < thickness) return !topology.openInfinite(gx - 1, gz, gx, gz)
                && !biomeOpensWall(seed, biome, gx - 1, gz, gx, gz, true);
        if (lz < thickness) return !topology.openInfinite(gx, gz - 1, gx, gz)
                && !biomeOpensWall(seed, biome, gx, gz - 1, gx, gz, false);
        return false;
    }

    private static boolean isMazeWallCore(MazeTopology topology, MazeNbtStructures.Layout structures,
                                          long seed, MazeBiomes.Biome biome, int x, int z,
                                          int size, int thickness, int radius) {
        if (!isWall(topology, structures, seed, biome, x, z, size, thickness, radius)) return false;
        if (biome.kind() == MazeBiomes.Kind.CRIMSON_MARSHLANDS)
            return isCircularMazeWallCore(seed, x, z, size, thickness);

        int limit = radius * size;
        int gx = Math.floorDiv(x + limit, size);
        int gz = Math.floorDiv(z + limit, size);
        int lx = Math.floorMod(x + limit, size);
        int lz = Math.floorMod(z + limit, size);
        int core = Math.min(thickness - 1, thickness / 2);
        boolean intersection = lx < thickness && lz < thickness;
        boolean xWall = lx < thickness && (intersection
                || !topology.openInfinite(gx - 1, gz, gx, gz)
                && !biomeOpensWall(seed, biome, gx - 1, gz, gx, gz, true));
        boolean zWall = lz < thickness && (intersection
                || !topology.openInfinite(gx, gz - 1, gx, gz)
                && !biomeOpensWall(seed, biome, gx, gz - 1, gx, gz, false));
        return xWall && lx == core || zWall && lz == core;
    }

    private static boolean isCircularMazeWallCore(long seed, int x, int z,
                                                   int cell, int thickness) {
        MazeBiomes.Catalog catalog = MazeBiomes.current();
        int regionSize = cell * catalog.regionSizeCells();
        int regionX = Math.floorDiv(x, regionSize);
        int regionZ = Math.floorDiv(z, regionSize);
        double centerX = regionX * (double)regionSize + regionSize * 0.5D;
        double centerZ = regionZ * (double)regionSize + regionSize * 0.5D;
        double dx = x + 0.5D - centerX;
        double dz = z + 0.5D - centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.atan2(dz, dx);
        if (angle < 0.0D) angle += Math.PI * 2.0D;
        double ringSpacing = cell * 1.72D;
        int ring = Math.max(1, Mth.floor(distance / ringSpacing + 0.5D));
        long regionSeed = mix(seed ^ (long)regionX * 0xD6E8FEB86659FD93L
                ^ (long)regionZ * 0xA5A3564E27F8862BL);
        long ringSeed = mix(regionSeed ^ ring * 0x9E3779B97F4A7C15L);
        double ringCenter = ring * ringSpacing + signedUnitFloat(ringSeed) * ringSpacing * 0.14D;
        boolean ringCore = Math.abs(distance - ringCenter) < 0.5D;

        int spokes = 12;
        double spokeStep = Math.PI * 2.0D / spokes;
        int spoke = Mth.floor(angle / spokeStep + 0.5D) % spokes;
        double difference = Math.abs(angle - spoke * spokeStep);
        difference = Math.min(difference, Math.PI * 2.0D - difference);
        boolean spokeCore = distance * difference < 0.5D;
        return ringCore || spokeCore;
    }

    /** Concentric passages split by short radial partitions, with deterministic gates in
     * every ring. Each Crimson region receives its own circular maze center. */
    private static boolean isCircularMazeWall(long seed, int x, int z, int cell, int thickness) {
        MazeBiomes.Catalog catalog = MazeBiomes.current();
        int regionSize = cell * catalog.regionSizeCells();
        int regionX = Math.floorDiv(x, regionSize);
        int regionZ = Math.floorDiv(z, regionSize);
        double centerX = regionX * (double)regionSize + regionSize * 0.5D;
        double centerZ = regionZ * (double)regionSize + regionSize * 0.5D;
        double dx = x + 0.5D - centerX;
        double dz = z + 0.5D - centerZ;
        double radius = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.atan2(dz, dx);
        if (angle < 0.0D) angle += Math.PI * 2.0D;

        double ringSpacing = cell * 1.72D;
        if (radius < ringSpacing * 0.72D) return false;
        int ring = Math.max(1, Mth.floor(radius / ringSpacing + 0.5D));
        double ringDistance = Math.abs(radius - ring * ringSpacing);
        long regionSeed = mix(seed ^ (long)regionX * 0xD6E8FEB86659FD93L
                ^ (long)regionZ * 0xA5A3564E27F8862BL);
        if (isCrimsonGrandHall(regionSeed, radius, angle, ringSpacing)
                || isCrimsonChamber(regionSeed, dx, dz, radius, angle, ringSpacing)) return false;

        long ringSeed = mix(regionSeed ^ ring * 0x9E3779B97F4A7C15L);
        double ringJitter = signedUnitFloat(ringSeed) * ringSpacing * 0.14D;
        ringDistance = Math.abs(radius - (ring * ringSpacing + ringJitter));
        if (ringDistance < thickness * 0.62D) {
            int slices = 36;
            int slice = Mth.floor(angle / (Math.PI * 2.0D) * slices) % slices;
            int gateA = (int)Math.floorMod(ringSeed, slices);
            int gateB = (gateA + 8 + (int)Math.floorMod(ringSeed >>> 11, 8L)) % slices;
            int gateC = (gateB + 7 + (int)Math.floorMod(ringSeed >>> 23, 9L)) % slices;
            int gateWidth = 1 + (int)Math.floorMod(ringSeed >>> 37, 2L);
            if (circularSliceDistance(slice, gateA, slices) > 1
                    && circularSliceDistance(slice, gateB, slices) > gateWidth
                    && circularSliceDistance(slice, gateC, slices) > 1) return true;
        }

        // Radial partitions only occupy selected annuli. Alternating their phase stops the
        // rings from becoming simple racetracks while preserving multiple routes.
        int spokes = 12;
        double spokeStep = Math.PI * 2.0D / spokes;
        int spoke = Mth.floor(angle / spokeStep + 0.5D) % spokes;
        double spokeAngle = spoke * spokeStep;
        double difference = Math.abs(angle - spokeAngle);
        difference = Math.min(difference, Math.PI * 2.0D - difference);
        double arcDistance = radius * difference;
        int annulus = Math.max(0, Mth.floor(radius / ringSpacing));
        long partition = mix(regionSeed ^ (long)spoke * 0xC2B2AE3D27D4EB4FL
                ^ (long)annulus * 0x165667B19E3779F9L);
        boolean broadAnnularHall = Math.floorMod(mix(regionSeed ^ annulus * 0x94D049BB133111EBL), 6L) == 0L;
        return !broadAnnularHall && arcDistance < thickness * 0.52D
                && Math.floorMod(partition, 7L) <= 2L;
    }

    private static boolean isCrimsonGrandHall(long regionSeed, double radius, double angle,
                                              double ringSpacing) {
        int hallCount = 2 + (int)Math.floorMod(regionSeed >>> 7, 3L);
        for (int hall = 0; hall < hallCount; hall++) {
            long value = mix(regionSeed ^ hall * 0xDB4F0B9175AE2165L);
            double hallAngle = unitFloat(value) * Math.PI * 2.0D;
            double difference = Math.abs(angle - hallAngle);
            difference = Math.min(difference, Math.PI * 2.0D - difference);
            double width = ringSpacing * (0.24D + unitFloat(value >>> 17) * 0.16D);
            if (radius * difference < width && radius > ringSpacing * 0.62D) return true;
        }
        return false;
    }

    private static boolean isCrimsonChamber(long regionSeed, double dx, double dz,
                                             double radius, double angle, double ringSpacing) {
        int annulus = Math.max(0, Mth.floor(radius / ringSpacing));
        int sectors = 10;
        int sector = Mth.floor(angle / (Math.PI * 2.0D) * sectors + 0.5D) % sectors;
        long chamber = mix(regionSeed ^ (long)annulus * 0xA24BAED4963EE407L
                ^ (long)sector * 0x9FB21C651E98DF25L);
        if (Math.floorMod(chamber, 5L) != 0L) return false;
        double chamberAngle = sector * Math.PI * 2.0D / sectors
                + signedUnitFloat(chamber >>> 19) * 0.12D;
        double chamberRadius = (annulus + 0.52D) * ringSpacing;
        double chamberX = Math.cos(chamberAngle) * chamberRadius;
        double chamberZ = Math.sin(chamberAngle) * chamberRadius;
        double size = ringSpacing * (0.52D + unitFloat(chamber >>> 31) * 0.20D);
        double differenceX = dx - chamberX;
        double differenceZ = dz - chamberZ;
        return differenceX * differenceX + differenceZ * differenceZ < size * size;
    }

    private static int circularSliceDistance(int first, int second, int count) {
        int difference = Math.abs(first - second);
        return Math.min(difference, count - difference);
    }

    private static boolean biomeOpensWall(long seed, MazeBiomes.Biome biome, int ax, int az,
                                          int bx, int bz, boolean northSouthBoundary) {
        int divisor = biome.wallOpeningDivisor();
        if (divisor <= 0) return false;
        long edge = mix(seed ^ (long)Math.min(ax, bx) * 0x9E3779B97F4A7C15L
                ^ (long)Math.min(az, bz) * 0xD1B54A32D192ED03L
                ^ (northSouthBoundary ? 0xA24BAED4963EE407L : 0x9FB21C651E98DF25L));
        return Math.floorMod(edge, divisor) == 0;
    }

    private static boolean isArchOpening(MazeTopology topology, int x, int z, int size,
                                         int thickness, int radius) {
        if (isCenterArena(x, z, size)) return false;
        int limit = radius * size;
        int gx = Math.floorDiv(x + limit, size);
        int gz = Math.floorDiv(z + limit, size);
        int lx = Math.floorMod(x + limit, size);
        int lz = Math.floorMod(z + limit, size);
        if (lx < thickness && lz >= thickness && topology.openInfinite(gx - 1, gz, gx, gz))
            return topology.archInfinite(gx - 1, gz, gx, gz);
        return lz < thickness && lx >= thickness
                && topology.openInfinite(gx, gz - 1, gx, gz)
                && topology.archInfinite(gx, gz - 1, gx, gz);
    }

    private static void placeDecorationColumn(ChunkAccess chunk, BlockPos.MutableBlockPos p,
                                              MazeTopology topology, MazeNbtStructures.Layout structures,
                                              long seed, int x, int z,
                                              int cell, int thickness, int radius, int wallHeight,
                                              MazeBiomes.Biome biome, int floorY) {
        if (isCenterArena(x, z, cell)) {
            int offset = cell * 2;
            boolean arenaPillar = Math.abs(Math.abs(x) - offset) <= 1
                    && Math.abs(Math.abs(z) - offset) <= 1;
            if (arenaPillar) {
                for (int y = 1; y <= wallHeight; y++)
                    bufferedSet(chunk, x, floorY + y, z, Asterion.ANCIENT_BRICKS.defaultBlockState());
            }
            return;
        }
        if (structures.reserved(x, z)) return;

        int limit = radius * cell;
        int gx = Math.floorDiv(x + limit, cell);
        int gz = Math.floorDiv(z + limit, cell);
        int lx = Math.floorMod(x + limit, cell);
        int lz = Math.floorMod(z + limit, cell);
        int topologyX = Math.floorMod(gx, topology.size);
        int topologyZ = Math.floorMod(gz, topology.size);
        int center = thickness + (cell - thickness) / 2;
        int innerA = thickness + 2;
        int innerB = cell - 3;

        placeBiomeFloorDetail(chunk, seed, x, z, lx, lz, center, thickness, wallHeight, biome, floorY);
        // Overgrowth owns a fully Asterion-native decoration palette. Generic landmarks
        // below intentionally remain Ancient-only because many of them use vanilla blocks.
        if (biome.kind() == MazeBiomes.Kind.OVERGROWTH) return;

        long supply = mix(seed ^ (long) gx * 0xC2B2AE3D27D4EB4FL
                ^ (long) gz * 0x165667B19E3779F9L);
        if (lx == center + 1 && lz == center - 1 && Math.floorMod(supply, 311) == 0) {
            int barrelY = floorY + 3;
            BlockPos barrelPos = new BlockPos(x, barrelY, z);
            placeLootBarrel(chunk, barrelPos, MAZE_BARREL_LOOT, supply);
            for (int y = barrelY + 1; y <= DIMENSION_CEILING_Y; y++)
                bufferedSet(chunk, x, y, z, Asterion.MAZESTEEL_CHAIN.defaultBlockState());
            if (Math.floorMod(supply >>> 17, 3) == 0)
                bufferedSet(chunk, x + 1, floorY + 1, z, Blocks.COBWEB.defaultBlockState());
        }

        if (topology.hasTrait(topologyX, topologyZ, MazeTopology.PILLAR_HALL)
                && (lx == innerA || lx == innerB) && (lz == innerA || lz == innerB)) {
            int height = 8 + (int) Math.floorMod(mix(seed ^ ((long) x << 32) ^ z), 7);
            for (int y = 1; y <= height; y++) {
                Block block = y == 1 || y == height ? Blocks.CHISELED_DEEPSLATE
                        : y % 5 == 0 ? Blocks.POLISHED_BASALT : Blocks.POLISHED_DEEPSLATE;
                bufferedSet(chunk, x, floorY + y, z, block.defaultBlockState());
            }
        }

        if (topology.hasTrait(topologyX, topologyZ, MazeTopology.DEAD_END_ALTAR) && lx == center && lz == center) {
            bufferedSet(chunk, x, floorY + 1, z, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
            bufferedSet(chunk, x, floorY + 2, z, Blocks.POLISHED_BASALT.defaultBlockState());
            bufferedSet(chunk, x, floorY + 3, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }

        if (topology.hasTrait(topologyX, topologyZ, MazeTopology.SAFE_RUNE)) {
            int dx = Math.abs(lx - center), dz = Math.abs(lz - center);
            if (dx <= 2 && dz <= 2)
                bufferedSet(chunk, x, floorY, z, ((dx + dz) & 1) == 0
                        ? Blocks.POLISHED_TUFF.defaultBlockState()
                        : Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
            if (lx == center && lz == center) {
                bufferedSet(chunk, x, floorY + 1, z, Blocks.LODESTONE.defaultBlockState());
                bufferedSet(chunk, x, floorY + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
            } else if (dx == 2 && dz == 2)
                bufferedSet(chunk, x, floorY + 1, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
            int signedDx = lx - center;
            int signedDz = lz - center;
            if (Math.abs(signedDx) == 2 && signedDz == 0) {
                int distanceCells = Math.max(Math.abs(gx - radius), Math.abs(gz - radius));
                float distanceRatio = distanceCells / (float)Math.max(1, radius);
                ResourceKey<LootTable> loot = distanceRatio >= 0.68F ? SAFE_RUNE_FAR_LOOT
                        : distanceRatio >= 0.36F ? SAFE_RUNE_MID_LOOT : SAFE_RUNE_NEAR_LOOT;
                BlockPos barrelPos = new BlockPos(x, floorY + 1, z);
                placeLootBarrel(chunk, barrelPos, loot,
                        mix(seed ^ ((long)x << 32) ^ z ^ 0xA0761D6478BD642FL));
            }
        }

        if (topology.hasTrait(topologyX, topologyZ, MazeTopology.RUBBLE)) {
            long rubble = mix(seed ^ (long) gx * 0x9E3779B97F4A7C15L ^ (long) gz * 0xD1B54A32D192ED03L);
            int span = Math.max(1, cell - thickness - 3);
            int rx = thickness + 1 + (int) Math.floorMod(rubble, span);
            int rz = thickness + 1 + (int) Math.floorMod(rubble >>> 12, span);
            if (lx == rx && lz == rz) {
                Block rubbleBlock = (rubble & 1) == 0 ? Blocks.COBBLED_DEEPSLATE : Blocks.TUFF;
                bufferedSet(chunk, x, floorY + 1, z, rubbleBlock.defaultBlockState());
                if ((rubble & 7) == 0)
                    bufferedSet(chunk, x, floorY + 2, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
            }
        }

        if (topology.hasTrait(topologyX, topologyZ, MazeTopology.PLAZA)) {
            int distance = Math.max(Math.abs(lx - center), Math.abs(lz - center));
            if (distance == 2 && (lx == center || lz == center))
                bufferedSet(chunk, x, floorY + 1, z, Blocks.POLISHED_TUFF.defaultBlockState());
            if (lx == center && lz == center) {
                bufferedSet(chunk, x, floorY + 1, z, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
                bufferedSet(chunk, x, floorY + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

    }

    private static void placeLootBarrel(ChunkAccess chunk, BlockPos pos,
                                        ResourceKey<LootTable> loot, long seed) {
        BlockState state = Blocks.BARREL.defaultBlockState();
        chunk.setBlockState(pos, state, 0);
        BarrelBlockEntity barrel = new BarrelBlockEntity(pos, state);
        barrel.setLootTable(loot);
        barrel.setLootTableSeed(seed);
        chunk.setBlockEntity(barrel);
    }

    private static BlockState patternedWall(long seed, int x, int y, int z, MazeBiomes.Biome biome,
                                            int cell, int radius) {
        if (isCenterArena(x, z, cell)) return Asterion.ANCIENT_BRICKS.defaultBlockState();
        // Interpolated, low-frequency fields produce broad organic regions instead of
        // floorDiv-aligned cubes. A small second octave softens transitions at their edges.
        double broad = wallNoise(seed ^ 0x9E3779B97F4A7C15L, x, y, z, 18.0D);
        double secondary = wallNoise(seed ^ 0xD1B54A32D192ED03L, x, y, z, 9.0D);
        double erosion = broad * 0.78D + secondary * 0.22D;
        double biomeBlend = biome.kind() == MazeBiomes.Kind.OVERGROWTH
                ? overgrowthBlendAt(x, z, cell) : 0.0D;

        if (biomeBlend > 0.0D && biome.hasFeature("mossy_walls")) {
            double moss = wallNoise(seed ^ 0xA24BAED4963EE407L, x, y, z, 15.0D) * 0.82D
                    + secondary * 0.18D;
            double threshold = 0.62D + (1.0D - biomeBlend) * 0.28D;
            if (moss > threshold + 0.12D) return Asterion.MOSSY_ANCIENT_STONE.defaultBlockState();
            if (moss > threshold) return Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState();
        }
        if (erosion > 0.70D) return Asterion.ANCIENT_STONE.defaultBlockState();
        return Asterion.ANCIENT_BRICKS.defaultBlockState();
    }

    private static Block patternedFloor(long seed, int x, int z, int depth, MazeTopology topology,
                                        int cell, int radius) {
        if (depth > 0) {
            int foundation = x * 31 ^ z * 17 ^ depth * 13 ^ (int) seed;
            return (foundation & 3) == 0 ? Asterion.ANCIENT_STONE : Asterion.ANCIENT_BRICKS;
        }
        if (topology != null && topology.onSolutionTrail(x, z, cell, radius)) {
            long trail = mix(seed ^ (long) x * 31L ^ z);
            return (trail & 7) == 0 ? Asterion.ANCIENT_BRICKS : Asterion.ANCIENT_STONE;
        }
        long patch = mix(seed ^ (long) Math.floorDiv(x, 5) * 0x9E3779B97F4A7C15L
                ^ (long) Math.floorDiv(z, 5) * 0xD1B54A32D192ED03L);
        long detail = mix(patch ^ (long) x * 341873128712L ^ (long) z * 132897987541L);
        if (Math.floorMod(detail, 17) == 0) return Asterion.ANCIENT_BRICKS;
        return Asterion.ANCIENT_STONE;
    }

    private static MazeBiomes.Biome mazeBiomeAt(long seed, int x, int z, int cell) {
        MazeBiomes.Catalog catalog = MazeBiomes.current();
        int regionSize = cell * catalog.regionSizeCells();
        int regionX = Math.floorDiv(x, regionSize);
        int regionZ = Math.floorDiv(z, regionSize);
        long region = mix(seed ^ (long)regionX * 0x9E3779B97F4A7C15L
                ^ (long)regionZ * 0xD1B54A32D192ED03L);
        if (Math.max(Math.abs(x), Math.abs(z)) < cell * catalog.ancientCenterRadiusCells())
            return catalog.ancient();
        int localX = Math.floorMod(x, regionSize);
        int localZ = Math.floorMod(z, regionSize);
        int transition = Math.max(1, Math.round(cell * catalog.transitionWidthCells()));
        if (localX < transition || localZ < transition
                || localX >= regionSize - transition || localZ >= regionSize - transition)
            return catalog.ancient();
        return catalog.select(region);
    }

    private static double overgrowthBlendAt(int x, int z, int cell) {
        MazeBiomes.Catalog catalog = MazeBiomes.current();
        int regionSize = cell * catalog.regionSizeCells();
        int localX = Math.floorMod(x, regionSize);
        int localZ = Math.floorMod(z, regionSize);
        int edge = Math.min(Math.min(localX, regionSize - 1 - localX),
                Math.min(localZ, regionSize - 1 - localZ));
        int neutralBand = Math.max(1, Math.round(cell * catalog.transitionWidthCells()));
        int blendWidth = Math.max(0, Math.round(cell * catalog.blendWidthCells()));
        if (edge <= neutralBand) return 0.0D;
        if (blendWidth == 0 || edge >= neutralBand + blendWidth) return 1.0D;
        double amount = (edge - neutralBand) / (double)blendWidth;
        return amount * amount * (3.0D - 2.0D * amount);
    }

    private static boolean placeMazeMotifColumn(ChunkAccess chunk, long seed, int x, int z,
                                                int cell, int thickness, int wallHeight,
                                                MazeBiomes.Biome biome, int radius, int floorY) {
        int limit = AsterionConfig.INSTANCE.mazeRadiusCells * cell;
        int gx = Math.floorDiv(x + limit, cell);
        int gz = Math.floorDiv(z + limit, cell);
        int lx = Math.floorMod(x + limit, cell);
        int lz = Math.floorMod(z + limit, cell);
        int center = thickness + (cell - thickness) / 2;
        int dx = lx - center, dz = lz - center;
        int clearHalfWidth = 2;
        if (Math.abs(dx) <= clearHalfWidth || Math.abs(dz) <= clearHalfWidth) return false;
        long cellRoll = mix(seed ^ (long)gx * 0xD6E8FEB86659FD93L
                ^ (long)gz * 0xA5A3564E27F8862BL);
        int chance = biome.motifChance();
        if (Math.floorMod(cellRoll, chance) != 0) return false;

        int motif = biome.kind() == MazeBiomes.Kind.ANCIENT
                ? (int)Math.floorMod(cellRoll >>> 9, 4L) : 0;
        boolean shaped = switch (motif) {
            case 0 -> {
                double ring = Math.sqrt(dx * dx + dz * dz);
                yield ring >= 3.2D && ring <= 4.35D;
            }
            case 1 -> Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1;
            case 2 -> Math.max(Math.abs(dx), Math.abs(dz)) == 4;
            default -> (Math.abs(dz) == 3 && Integer.signum(dx) == Integer.signum(dz))
                    || (Math.abs(dx) == 4 && Integer.signum(dx) != Integer.signum(dz));
        };
        if (!shaped) return false;
        int motifHeight = Math.min(wallHeight, 11 + (int)Math.floorMod(cellRoll >>> 17, 7L));
        for (int y = 1; y <= motifHeight; y++)
            bufferedSet(chunk, x, floorY + y, z,
                    patternedWall(seed ^ cellRoll, x, y, z, biome, cell, radius));
        return true;
    }

    private static void placeBiomeWallDetail(ChunkAccess chunk, long seed, int x, int z,
                                             int wallHeight,
                                             MazeBiomes.Biome biome, int floorY) {
        if (biome.hasFeature("leaf_crowns") || biome.hasFeature("tainted_foliage")) {
            boolean tainted = biome.hasFeature("tainted_foliage");
            BlockState leaves = (tainted ? Asterion.TAINTED_LEAVES : Asterion.ANCIENT_LEAVES).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            // Guaranteed eye-level foliage uses broad 3D fields, producing continuous
            // organic wall growth instead of relying entirely on decoration attempts.
            for (int rise = 4; rise <= wallHeight - 3; rise++) {
                double wallGrowth = wallNoise(seed ^ 0xC6BC279692B5CC83L,
                        x, floorY + rise, z, 7.5D) * 0.72D
                        + wallNoise(seed ^ 0x4F1BBCDCBFA54001L,
                        x, floorY + rise, z, 16.0D) * 0.28D;
                if (wallGrowth > (tainted ? 0.68D : 0.62D))
                    bufferedSet(chunk, x, floorY + rise, z, leaves);
            }
            double crown = wallNoise(seed ^ 0xE7037ED1A0B428DBL, x, 0, z, 5.8D)
                    * 0.72D + wallNoise(seed ^ 0x8EBC6AF09C88C6E3L, x, 0, z, 12.0D) * 0.28D;
            if (crown < (tainted ? 0.61D : 0.55D)) return;
            int crownY = floorY + wallHeight;
            bufferedSet(chunk, x, crownY, z, leaves);
            if (crown > 0.70D && crownY > floorY + 4)
                bufferedSet(chunk, x, crownY - 1, z, leaves);
        }
    }

    private static void placeBiomeFloorDetail(ChunkAccess chunk, long seed, int x, int z,
                                              int lx, int lz, int center, int thickness,
                                              int wallHeight, MazeBiomes.Biome biome, int floorY) {
        long detail = mix(seed ^ (long)x * 0xDB4F0B9175AE2165L ^ (long)z * 0xBBE0563303A4615FL);
        boolean corridorInterior = lx >= thickness + 1 && lz >= thickness + 1;
        if (!corridorInterior) return;
        if (biome.kind() == MazeBiomes.Kind.OVERGROWTH) {
            // A coherent low layer gives every overgrown corridor its identity even when
            // large placed features cannot find suitable supports. All growth is walkable.
            if (biome.hasFeature("moss_patches") && wallNoise(seed ^ 0x76CB124FL, x, 0, z, 6.5D) > .56D)
                bufferedSet(chunk, x, floorY, z, Asterion.ANCIENT_MOSS.defaultBlockState());
            if (biome.hasFeature("floor_plants") && Math.floorMod(detail, 13) == 0)
                bufferedSet(chunk, x, floorY + 1, z, Asterion.ANCIENT_MOSS_CARPET.defaultBlockState());
            else if (biome.hasFeature("floor_plants") && Math.floorMod(detail, 29) == 0)
                bufferedSet(chunk, x, floorY + 1, z, Asterion.SHORT_GRASS.defaultBlockState());
        }
    }

    private static double wallNoise(long seed, int x, int y, int z, double scale) {
        double sampleX = x / scale;
        double sampleY = y / scale;
        double sampleZ = z / scale;
        int x0 = Mth.floor(sampleX), y0 = Mth.floor(sampleY), z0 = Mth.floor(sampleZ);
        double tx = smoothNoiseStep(sampleX - x0);
        double ty = smoothNoiseStep(sampleY - y0);
        double tz = smoothNoiseStep(sampleZ - z0);
        double c000 = noiseCorner(seed, x0, y0, z0);
        double c100 = noiseCorner(seed, x0 + 1, y0, z0);
        double c010 = noiseCorner(seed, x0, y0 + 1, z0);
        double c110 = noiseCorner(seed, x0 + 1, y0 + 1, z0);
        double c001 = noiseCorner(seed, x0, y0, z0 + 1);
        double c101 = noiseCorner(seed, x0 + 1, y0, z0 + 1);
        double c011 = noiseCorner(seed, x0, y0 + 1, z0 + 1);
        double c111 = noiseCorner(seed, x0 + 1, y0 + 1, z0 + 1);
        double x00 = lerpNoise(tx, c000, c100), x10 = lerpNoise(tx, c010, c110);
        double x01 = lerpNoise(tx, c001, c101), x11 = lerpNoise(tx, c011, c111);
        return lerpNoise(tz, lerpNoise(ty, x00, x10), lerpNoise(ty, x01, x11));
    }

    private static double noiseCorner(long seed, int x, int y, int z) {
        long value = mix(seed ^ (long)x * 0x9E3779B97F4A7C15L
                ^ (long)y * 0x94D049BB133111EBL ^ (long)z * 0xD1B54A32D192ED03L);
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double smoothNoiseStep(double value) {
        return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerpNoise(double amount, double first, double second) {
        return first + (second - first) * amount;
    }

    private static float unitFloat(long value) {
        return (value >>> 40) / (float) (1L << 24);
    }

    private static float signedUnitFloat(long value) {
        return unitFloat(value) * 2.0F - 1.0F;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static MazeTopology topology(long seed, int radius, int loopChance, int landmarkChance) {
        MazeKey key = new MazeKey(seed, radius, loopChance, landmarkChance);
        return MAZE_TOPOLOGIES.computeIfAbsent(key,
                ignored -> new MazeTopology(seed, radius * 2, loopChance, landmarkChance));
    }

    private record MazeKey(long seed, int radius, int loopChance, int landmarkChance) {
    }

    private record DecayingBlock(ResourceKey<Level> dimension, BlockPos pos, Block expectedBlock, long dueTick) {
    }

    private record RestoringBlock(ResourceKey<Level> dimension, BlockPos pos, BlockState state, long dueTick) {
    }

    private static final class BossPillar {
        private final int x, y, z;
        private boolean broken;
        private BossPillar(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
    private static final class BossArenaBuild {
        long buriedUntil;
        Vec3 buriedPosition;
        private final List<BossPillar> pillars = new ArrayList<>();
        private boolean ready;
    }
    private static final class BossFinale {
        private final UUID bossId;
        private final Map<UUID, Boolean> previousInvulnerability = new HashMap<>();
        private int ticks;
        private BossFinale(UUID bossId) { this.bossId = bossId; }
    }

    public static boolean isAncientBiomeAt(double x,double z) {
        return mazeBiomeAt(activeMazeTerrainSeed,Mth.floor(x),Mth.floor(z),
                AsterionConfig.INSTANCE.cellSize).kind()==MazeBiomes.Kind.ANCIENT;
    }
    private record FinaleDestination(ServerLevel level, BlockPos position, float yaw) { }

    private static final class ElectrifiedState {
        private int remainingTicks;
        private final boolean wasNoAi;

        private ElectrifiedState(int remainingTicks, boolean wasNoAi) {
            this.remainingTicks = remainingTicks;
            this.wasNoAi = wasNoAi;
        }
    }

    private record BlockKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private record SummonedPortal(ResourceKey<Level> dimension, BlockPos center,
                                  int surfaceY, long visualSeed) {
    }

    private static final class PhasingEntity {
        private final Entity entity;
        private final boolean wasNoPhysics;
        private final boolean hadNoGravity;
        private final Vec3 origin;
        private final Vec3 originalVelocity;
        private int ticks;

        private PhasingEntity(Entity entity) {
            this.entity = entity;
            this.wasNoPhysics = entity.noPhysics;
            this.hadNoGravity = entity.isNoGravity();
            this.origin = entity.position();
            this.originalVelocity = entity.getDeltaMovement();
        }
    }

    private static final class PendingTransition {
        private final ServerLevel maze;
        private final BlockPos destination;
        private final boolean wasInvulnerable;
        private final boolean hadNoGravity;
        private final boolean wasNoPhysics;
        private final ChunkPos destinationChunk;
        private int ticks;
        private int preloadIndex;
        private boolean teleported;
        private boolean clientReady;

        private PendingTransition(ServerLevel maze, BlockPos destination,
                                  boolean wasInvulnerable, boolean hadNoGravity, boolean wasNoPhysics) {
            this.maze = maze;
            this.destination = destination;
            this.wasInvulnerable = wasInvulnerable;
            this.hadNoGravity = hadNoGravity;
            this.wasNoPhysics = wasNoPhysics;
            this.destinationChunk = ChunkPos.containing(destination);
        }
    }

    private static final class MazeTopology {
        private static final int[] DX = {0, 1, 0, -1};
        private static final int[] DZ = {-1, 0, 1, 0};
        private static final byte ROOM = 1;
        private static final byte PILLAR_HALL = 2;
        private static final byte DEAD_END_ALTAR = 4;
        private static final byte RUBBLE = 8;
        private static final byte SAFE_RUNE = 16;
        private static final byte PLAZA = 32;
        private static final byte GARDEN = 64;
        private final long seed;
        private final int size;
        private final byte[] openings;
        private final byte[] solutionOpenings;
        private final byte[] traits;
        private final boolean[] reachableFromCenter;

        private MazeTopology(long seed, int size, int loopChance, int landmarkChance) {
            this.seed = seed;
            this.size = size;
            this.openings = new byte[size * size];
            this.solutionOpenings = new byte[openings.length];
            this.traits = new byte[openings.length];
            this.reachableFromCenter = new boolean[openings.length];
            boolean[] visited = new boolean[openings.length];
            int[] route = new int[openings.length];
            int routeCount = carvePrimaryRoute(visited, route);
            growBranches(visited, route, routeCount);
            addRoomsAndLoops(loopChance);
            shapeDistrictLandmarks();
            assignLandmarks(landmarkChance);
            markReachableFromCenter();
            int entrance = index(size / 2, size - 1);
            if (!reachableFromCenter[entrance])
                throw new IllegalStateException("Generated asterion entrance cannot reach its center");
            validateCompletionRoute(entrance, index(size / 2, size / 2));
        }

        private void markReachableFromCenter() {
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            int center = index(size / 2, size / 2);
            reachableFromCenter[center] = true;
            queue.add(center);
            while (!queue.isEmpty()) {
                int cell = queue.removeFirst();
                int x = cell % size;
                int z = cell / size;
                int paths = openings[cell] & 15;
                for (int direction = 0; direction < 4; direction++) {
                    if ((paths & (1 << direction)) == 0) continue;
                    int nx = x + DX[direction];
                    int nz = z + DZ[direction];
                    if (!inBounds(nx, nz)) continue;
                    int next = index(nx, nz);
                    if (!reachableFromCenter[next]) {
                        reachableFromCenter[next] = true;
                        queue.addLast(next);
                    }
                }
            }
        }

        private int carvePrimaryRoute(boolean[] visited, int[] route) {
            int center = size / 2;
            int x = center;
            int z = size - 1;
            int count = 0;
            int current = index(x, z);
            visited[current] = true;
            route[count++] = current;
            int segment = 0;

            while (z > center) {
                long choice = mix(seed ^ (long) segment++ * 0x9E3779B97F4A7C15L);
                // The solution alternates deliberate long corridors with lateral dog-legs. The
                // shorter cap prevents a single obvious sightline running down the middle.
                int northRun = 3 + (int) Math.floorMod(choice, 6);
                for (int i = 0; i < northRun && z > center; i++) {
                    int next = index(x, --z);
                    carve(current, next, 0, true);
                    current = next;
                    if (!visited[current]) route[count++] = current;
                    visited[current] = true;
                }
                if (z == center) break;

                int remaining = z - center;
                int spread = Math.max(4, remaining / 2);
                int desired = center + (int) Math.floorMod(choice >>> 16, spread * 2 + 1) - spread;
                desired = Mth.clamp(desired, 3, size - 4);
                if (Math.abs(desired - x) < 3) {
                    int side = ((choice >>> 28) & 1L) == 0L ? -1 : 1;
                    desired = Mth.clamp(x + side * (3 + (int)Math.floorMod(choice >>> 36, 5)),
                            3, size - 4);
                }
                int maxHorizontal = 4 + (int) Math.floorMod(choice >>> 32, 7);
                desired = Mth.clamp(desired, x - maxHorizontal, x + maxHorizontal);
                while (x != desired) {
                    int direction = desired > x ? 1 : 3;
                    x += DX[direction];
                    int next = index(x, z);
                    carve(current, next, direction, true);
                    current = next;
                    if (!visited[current]) route[count++] = current;
                    visited[current] = true;
                }
            }

            while (x != center) {
                int direction = center > x ? 1 : 3;
                x += DX[direction];
                int next = index(x, z);
                carve(current, next, direction, true);
                current = next;
                if (!visited[current]) route[count++] = current;
                visited[current] = true;
            }
            return count;
        }

        private void growBranches(boolean[] visited, int[] roots, int rootCount) {
            int[] stack = new int[openings.length];
            byte[] enteredDirection = new byte[openings.length];
            for (int rootIndex = 0; rootIndex < rootCount; rootIndex++) {
                int top = 0;
                stack[0] = roots[rootIndex];
                enteredDirection[0] = -1;
                while (top >= 0) {
                    int cell = stack[top];
                    int cx = cell % size;
                    int cz = cell / size;
                    int direction = chooseUnvisitedDirection(visited, cell, cx, cz, enteredDirection[top]);
                    if (direction < 0) {
                        top--;
                        continue;
                    }
                    int next = index(cx + DX[direction], cz + DZ[direction]);
                    carve(cell, next, direction, false);
                    visited[next] = true;
                    stack[++top] = next;
                    enteredDirection[top] = (byte) direction;
                }
            }
        }

        private int chooseUnvisitedDirection(boolean[] visited, int cell, int cx, int cz, int entered) {
            long random = mix(seed ^ (long) cell * 0xD1B54A32D192ED03L ^ Integer.rotateLeft(cell, 13));
            if (entered >= 0 && (random & 3) != 0) {
                int nx = cx + DX[entered];
                int nz = cz + DZ[entered];
                if (inBounds(nx, nz) && !visited[index(nx, nz)]) return entered;
            }
            int best = -1;
            long bestScore = Long.MAX_VALUE;
            for (int direction = 0; direction < 4; direction++) {
                int nx = cx + DX[direction];
                int nz = cz + DZ[direction];
                if (!inBounds(nx, nz) || visited[index(nx, nz)]) continue;
                long score = mix(random ^ (long) direction * 0x94D049BB133111EBL) & Long.MAX_VALUE;
                if (score < bestScore) {
                    bestScore = score;
                    best = direction;
                }
            }
            return best;
        }

        private void addRoomsAndLoops(int loopChance) {
            for (int z = 1; z < size - 2; z += 2) for (int x = 1; x < size - 2; x += 2) {
                long roomRoll = mix(seed ^ (long) x * 0xDB4F0B9175AE2165L ^ (long) z * 0xBBE0563303A4615FL);
                if (Math.floorMod(roomRoll, 53) != 0 || solutionOpenings[index(x, z)] != 0) continue;
                carve(index(x, z), index(x + 1, z), 1, false);
                carve(index(x, z), index(x, z + 1), 2, false);
                carve(index(x + 1, z), index(x + 1, z + 1), 2, false);
                carve(index(x, z + 1), index(x + 1, z + 1), 1, false);
                traits[index(x, z)] |= ROOM;
                traits[index(x + 1, z)] |= ROOM;
                traits[index(x, z + 1)] |= ROOM;
                traits[index(x + 1, z + 1)] |= ROOM;
            }
            for (int z = 1; z < size - 1; z++) for (int x = 1; x < size - 1; x++) {
                int cell = index(x, z);
                long roll = mix(seed ^ (long) cell * 0xA24BAED4963EE407L);
                int direction = (roll & 1) == 0 ? 1 : 2;
                int nx = x + DX[direction];
                int nz = z + DZ[direction];
                if (inBounds(nx, nz) && !open(x, z, nx, nz) && Math.floorMod(roll >>> 8, loopChance) == 0)
                    carve(cell, index(nx, nz), direction, false);
            }
        }

        /** Opens occasional two-by-two districts after the spanning maze is complete. These
         * spaces break the grid rhythm without touching the guaranteed entrance solution. */
        private void shapeDistrictLandmarks() {
            int center = size / 2;
            for (int z = 3; z < size - 4; z += 3) for (int x = 3; x < size - 4; x += 3) {
                long roll = mix(seed ^ (long)x * 0xD1342543DE82EF95L
                        ^ (long)z * 0x94D049BB133111EBL);
                byte district = Math.floorMod(roll, 59) == 0 ? PLAZA
                        : Math.floorMod(roll >>> 9, 79) == 0 ? GARDEN : 0;
                if (district == 0 || Math.abs(x - center) < 7 && Math.abs(z - center) < 7) continue;
                boolean clear = true;
                for (int dz = 0; dz <= 1 && clear; dz++) for (int dx = 0; dx <= 1; dx++) {
                    int cell = index(x + dx, z + dz);
                    if (solutionOpenings[cell] != 0 || traits[cell] != 0) { clear = false; break; }
                }
                if (!clear) continue;
                carve(index(x, z), index(x + 1, z), 1, false);
                carve(index(x, z), index(x, z + 1), 2, false);
                carve(index(x + 1, z), index(x + 1, z + 1), 2, false);
                carve(index(x, z + 1), index(x + 1, z + 1), 1, false);
                for (int dz = 0; dz <= 1; dz++) for (int dx = 0; dx <= 1; dx++)
                    traits[index(x + dx, z + dz)] |= district;
            }
        }

        private void assignLandmarks(int landmarkChance) {
            for (int z = 1; z < size - 1; z++) for (int x = 1; x < size - 1; x++) {
                int cell = index(x, z);
                if ((traits[cell] & (ROOM | PLAZA | GARDEN)) != 0 || solutionOpenings[cell] != 0) continue;
                long roll = mix(seed ^ (long) cell * 0x9E3779B97F4A7C15L);
                int degree = Integer.bitCount(openings[cell] & 15);
                if (degree == 1 && Math.floorMod(roll, 17) == 0) traits[cell] |= SAFE_RUNE;
                else if (degree == 1 && Math.floorMod(roll, 3) == 0) traits[cell] |= DEAD_END_ALTAR;
                else if (Math.floorMod(roll, landmarkChance) == 0) traits[cell] |= PILLAR_HALL;
                else if (Math.floorMod(roll >>> 11, landmarkChance) == 1) traits[cell] |= RUBBLE;
            }
        }

        private boolean canReserveStructure(int minX, int minZ, int maxX, int maxZ) {
            if (minX < 2 || minZ < 2 || maxX >= size - 2 || maxZ >= size - 2) return false;
            int center = size / 2;
            if (minX <= center + 3 && maxX >= center - 3
                    && minZ <= center + 3 && maxZ >= center - 3) return false;
            boolean connected = false;
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                int cell = index(x, z);
                if (solutionOpenings[cell] != 0) return false;
                if ((traits[cell] & (ROOM | PLAZA | GARDEN | SAFE_RUNE)) != 0) return false;
                connected |= (openings[cell] & 15) != 0;
            }
            return connected;
        }

        private void validateCompletionRoute(int entrance, int goal) {
            boolean[] visited = new boolean[openings.length];
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            visited[entrance] = true;
            queue.add(entrance);
            while (!queue.isEmpty()) {
                int cell = queue.removeFirst();
                if (cell == goal) return;
                int x = cell % size, z = cell / size;
                int paths = solutionOpenings[cell] & 15;
                for (int direction = 0; direction < 4; direction++) {
                    if ((paths & (1 << direction)) == 0) continue;
                    int nx = x + DX[direction], nz = z + DZ[direction];
                    if (!inBounds(nx, nz)) continue;
                    int next = index(nx, nz);
                    if ((openings[cell] & (1 << direction)) == 0)
                        throw new IllegalStateException("Solution route crosses a closed maze wall");
                    if (!visited[next]) { visited[next] = true; queue.addLast(next); }
                }
            }
            throw new IllegalStateException("Generated asterion has no entrance-to-center solution route");
        }

        private void carve(int from, int to, int direction, boolean solution) {
            openings[from] |= (byte) (1 << direction);
            openings[to] |= (byte) (1 << ((direction + 2) & 3));
            if (solution) {
                solutionOpenings[from] |= (byte) (1 << direction);
                solutionOpenings[to] |= (byte) (1 << ((direction + 2) & 3));
            }
        }

        private int index(int x, int z) {
            return z * size + x;
        }

        private boolean inBounds(int x, int z) {
            return x >= 0 && z >= 0 && x < size && z < size;
        }

        private boolean open(int ax, int az, int bx, int bz) {
            if (!inBounds(ax, az) || !inBounds(bx, bz)) return false;
            int direction = direction(bx - ax, bz - az);
            return direction >= 0 && (openings[index(ax, az)] & (1 << direction)) != 0;
        }

        private boolean openInfinite(int ax, int az, int bx, int bz) {
            if (Math.abs(ax - bx) + Math.abs(az - bz) != 1) return false;
            int tax = Math.floorMod(ax, size), taz = Math.floorMod(az, size);
            int tbx = Math.floorMod(bx, size), tbz = Math.floorMod(bz, size);
            if (Math.abs(tax - tbx) + Math.abs(taz - tbz) == 1)
                return open(tax, taz, tbx, tbz);
            long seam = mix(seed ^ (long) Math.min(ax, bx) * 0x9E3779B97F4A7C15L
                    ^ (long) Math.min(az, bz) * 0xD1B54A32D192ED03L);
            return Math.floorMod(seam, 11) == 0;
        }

        private boolean archInfinite(int ax, int az, int bx, int bz) {
            int tax = Math.floorMod(ax, size), taz = Math.floorMod(az, size);
            int tbx = Math.floorMod(bx, size), tbz = Math.floorMod(bz, size);
            if (Math.abs(tax - tbx) + Math.abs(taz - tbz) == 1)
                return arch(tax, taz, tbx, tbz);
            return false;
        }

        private boolean arch(int ax, int az, int bx, int bz) {
            int a = index(ax, az);
            int b = index(bx, bz);
            long edge = mix(seed ^ (long) Math.min(a, b) * 0xD6E8FEB86659FD93L
                    ^ (long) Math.max(a, b) * 0xA5A3564E27F8862BL);
            return Math.floorMod(edge, 13) == 0;
        }

        private boolean hasTrait(int x, int z, byte trait) {
            return inBounds(x, z) && (traits[index(x, z)] & trait) != 0;
        }

        private boolean safeArrivalCell(int x, int z) {
            if (!inBounds(x, z)) return false;
            int cell = index(x, z);
            return (traits[cell] & (DEAD_END_ALTAR | PILLAR_HALL)) == 0
                    && reachableFromCenter[cell] && Integer.bitCount(openings[cell] & 15) > 0;
        }

        private boolean onSolutionTrail(int worldX, int worldZ, int cellSize, int radius) {
            int limit = radius * cellSize;
            int gx = Math.floorMod(Math.floorDiv(worldX + limit, cellSize), size);
            int gz = Math.floorMod(Math.floorDiv(worldZ + limit, cellSize), size);
            int paths = solutionOpenings[index(gx, gz)] & 15;
            if (paths == 0) return false;
            int lx = Math.floorMod(worldX + limit, cellSize);
            int lz = Math.floorMod(worldZ + limit, cellSize);
            int center = cellSize / 2 + 2;
            int band = 1;
            if (Math.abs(lx - center) <= band && Math.abs(lz - center) <= band) return true;
            if ((paths & 1) != 0 && Math.abs(lx - center) <= band && lz <= center) return true;
            if ((paths & 2) != 0 && Math.abs(lz - center) <= band && lx >= center) return true;
            if ((paths & 4) != 0 && Math.abs(lx - center) <= band && lz >= center) return true;
            return (paths & 8) != 0 && Math.abs(lz - center) <= band && lx <= center;
        }

        private static int direction(int dx, int dz) {
            return dx == 1 ? 1 : dx == -1 ? 3 : dz == 1 ? 2 : dz == -1 ? 0 : -1;
        }
    }
}
