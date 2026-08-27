package net.krodark.labyrinth;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.labyrinth.network.DimensionTransitionPayload;
import net.krodark.labyrinth.network.GatewayPortalPayload;
import net.krodark.labyrinth.network.MazeZapPayload;
import net.krodark.labyrinth.network.ragdoll.RagdollImpulsePayload;
import net.krodark.labyrinth.event.DeadSunEventSystem;
import net.krodark.labyrinth.worldgen.MazeNbtStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGenerator {
    // Temporary stability switch: NBT landmarks stay completely out of chunk generation until
    // their placement pipeline is re-enabled after profiling. The procedural maze remains intact.
    private static final boolean ENABLE_MAZE_NBT_STRUCTURES = false;
    private static final int FLOOR_Y = 48;
    private static final int BOSS_FLOOR_Y = 36;
    // A 31x31 opening: large enough to dominate the center arena while still leaving
    // a broad fighting/walking ring before the surrounding maze begins.
    private static final int PIT_HALF_WIDTH = 15;
    private static final int PIT_WALL_THICKNESS = 4;
    private static final int SKYFALL_CLEARANCE = 42;
    private static final ResourceKey<LootTable> MAZE_BARREL_LOOT = ResourceKey.create(
            Registries.LOOT_TABLE, Labyrinth.id("chests/maze_supply_barrel"));
    // Small render-safety buffer in nearest-first order. These are generated across separate
    // ticks, avoiding one giant stall while still satisfying the vanilla receiving-world screen.
    private static final int[][] PRELOAD_OFFSETS = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };
    private static final Map<Long, Integer> GATEWAY_SURFACE_Y = new ConcurrentHashMap<>();
    private static final Map<MazeKey, MazeTopology> MAZE_TOPOLOGIES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingTransition> PENDING_TRANSITIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_PORTAL_SYNC = new HashMap<>();
    private static final Map<UUID, PhasingEntity> PHASING_ENTITIES = new HashMap<>();
    private static SummonedPortal summonedPortal;
    private static final Map<UUID, Integer> ABOVE_WALL_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> WARD_FALL_PROTECTION = new HashMap<>();
    private static final Map<UUID, ElectrifiedState> ELECTRIFIED = new HashMap<>();
    private static final PriorityQueue<DecayingBlock> DECAYING_BLOCKS = new PriorityQueue<>(
            Comparator.comparingLong(DecayingBlock::dueTick));
    private static final PriorityQueue<RestoringBlock> RESTORING_BLOCKS = new PriorityQueue<>(
            Comparator.comparingLong(RestoringBlock::dueTick));
    private static final Map<BlockKey, Block> PLAYER_PLACED_BLOCKS = new HashMap<>();

    private WorldGenerator() {
    }

    public static void onChunkLoad(ServerLevel level, LevelChunk chunk, boolean newlyGenerated) {
        if (level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) {
            ChunkPos pos = chunk.getPos();
            BlockPos marker = new BlockPos(pos.getMinBlockX(), 1, pos.getMinBlockZ());
            boolean generatedNow = !chunk.getBlockState(marker).is(Blocks.BEDROCK);
            if (generatedNow) buildMazeChunk(level, chunk, marker);
            if (generatedNow) MazeNbtStructures.markCopperClean(chunk);
            else MazeNbtStructures.cleanLegacyCopper(chunk,
                    BOSS_FLOOR_Y - LabyrinthConfig.INSTANCE.floorThickness,
                    FLOOR_Y + LabyrinthConfig.INSTANCE.wallHeight);
        }
    }

    public static void tickServer(MinecraftServer server) {
        DeadSunEventSystem.tick(server);
        tickDecayingBlocks(server);
        tickRestoringBlocks(server);
        server.getPlayerList().getPlayers().forEach(WorldGenerator::tickPlayer);
        tickPhasingEntities(server);
        ServerLevel maze = server.getLevel(Labyrinth.LABYRINTH_LEVEL);
        if (maze != null) {
            // Dense fog hides the shorter horizon. A tight dimension-local cap prevents large
            // client settings (the supplied log used 32) from queuing thousands of maze chunks.
            maze.getChunkSource().setViewDistance(5);
            maze.getChunkSource().setSimulationDistance(3);
            tickMazeEntities(maze);
            // Do not force-load an unrelated multi-chunk NBT landmark while a player is in the
            // loading handshake; its bounded queue resumes as soon as the handoff completes.
            if (ENABLE_MAZE_NBT_STRUCTURES && PENDING_TRANSITIONS.isEmpty())
                MazeNbtStructures.tick(maze);
        }
        // These are stale-entry safeguards, not gameplay logic. Running the map scans once per
        // second removes needless UUID/entity lookups from every server tick.
        if ((server.overworld().getGameTime() % 20L) == 0L) {
            ABOVE_WALL_TICKS.keySet().removeIf(id -> maze == null || maze.getEntityInAnyDimension(id) == null);
            WARD_FALL_PROTECTION.keySet().removeIf(id -> maze == null || maze.getEntityInAnyDimension(id) == null);
            ELECTRIFIED.entrySet().removeIf(entry -> maze == null
                    || maze.getEntityInAnyDimension(entry.getKey()) == null);
            PENDING_TRANSITIONS.entrySet().removeIf(entry -> {
                if (server.getPlayerList().getPlayer(entry.getKey()) != null) return false;
                PendingTransition pending = entry.getValue();
                pending.maze.getChunkSource().removeTicketWithRadius(
                        TicketType.PORTAL, pending.destinationChunk, 1);
                return true;
            });
            LAST_PORTAL_SYNC.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
    }

    public static void trackPlayerPlacement(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) return;
        PLAYER_PLACED_BLOCKS.put(new BlockKey(level.dimension(), pos.immutable()), state.getBlock());
        DECAYING_BLOCKS.add(new DecayingBlock(level.dimension(), pos.immutable(), state.getBlock(),
                level.getGameTime() + LabyrinthConfig.INSTANCE.playerBlockDecayTicks));
    }

    public static void trackMazeBreak(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.dimension().equals(Labyrinth.LABYRINTH_LEVEL) || state.isAir()) return;
        BlockKey key = new BlockKey(level.dimension(), pos.immutable());
        Block placed = PLAYER_PLACED_BLOCKS.get(key);
        if (placed == state.getBlock()) {
            PLAYER_PLACED_BLOCKS.remove(key);
            return;
        }
        RESTORING_BLOCKS.removeIf(entry -> entry.dimension.equals(level.dimension()) && entry.pos.equals(pos));
        RESTORING_BLOCKS.add(new RestoringBlock(level.dimension(), pos.immutable(), state,
                level.getGameTime() + 100L));
    }

    /** The active gateway and its supporting well cannot be mined out from under the renderer. */
    public static boolean isActivePortalProtected(ServerLevel level, BlockPos pos) {
        SummonedPortal portal = summonedPortal;
        if (portal == null || !portal.dimension.equals(level.dimension())) return false;
        int dx = Math.abs(pos.getX() - portal.center.getX());
        int dz = Math.abs(pos.getZ() - portal.center.getZ());
        return dx <= 3 && dz <= 3
                && pos.getY() >= portal.surfaceY - 3 && pos.getY() <= portal.surfaceY + 1;
    }

    /** Removes only blocks tracked as player-placed, allowing the Minotaur to counter pillaring without eating the maze. */
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
                    PLAYER_PLACED_BLOCKS.remove(key);
                    level.destroyBlock(cursor, false, null, 512);
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
        int distance = LabyrinthConfig.INSTANCE.gatewayDistance;
        return new BlockPos((int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
    }

    /** Replaces the current command-spawned portal and reveals it to nearby players immediately. */
    public static void summonPortal(ServerLevel level, BlockPos center, int surfaceY) {
        int riftY = surfaceY - 1;
        buildSummonedWell(level, center.getX(), surfaceY, center.getZ());
        long visualSeed = mix(level.getSeed() ^ center.asLong() ^ level.getGameTime()
                ^ 0xA0761D6478BD642FL);
        summonedPortal = new SummonedPortal(level.dimension(), center.immutable(), riftY, visualSeed);
        GatewayPortalPayload payload = portalPayload(level.getServer(), center, riftY, visualSeed);
        level.players().forEach(player -> {
            if (player.distanceToSqr(Vec3.atCenterOf(center)) <= 144.0D * 144.0D
                    && ServerPlayNetworking.canSend(player, GatewayPortalPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
                LAST_PORTAL_SYNC.put(player.getUUID(), level.getGameTime());
            }
        });
    }

    private static void buildSummonedWell(ServerLevel level, int centerX, int surfaceY, int centerZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int distance = dx * dx + dz * dz;
            int x = centerX + dx;
            int z = centerZ + dz;
            if (distance <= 5) {
                level.setBlock(cursor.set(x, surfaceY - 1, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(cursor.set(x, surfaceY - 2, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(cursor.set(x, surfaceY - 3, z), Labyrinth.ANCIENT_STONE.defaultBlockState(), 2);
            } else if (distance <= 12) {
                Block rim = ((dx + dz) & 3) == 0 ? Labyrinth.ANCIENT_STONE : Labyrinth.ANCIENT_BRICKS;
                level.setBlock(cursor.set(x, surfaceY - 1, z), rim.defaultBlockState(), 2);
                if (distance >= 9 && ((dx * 5 + dz * 3) & 7) == 0)
                    level.setBlock(cursor.set(x, surfaceY, z), Labyrinth.ANCIENT_STONE_WALL.defaultBlockState(), 2);
            }
        }
    }

    public static void buildGateway(ServerLevel level, BlockPos horizontalTarget) {
        int x = horizontalTarget.getX();
        int z = horizontalTarget.getZ();
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        GATEWAY_SURFACE_Y.put(level.getSeed(), y);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            int distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= 36 && distanceSquared >= 10)
                level.setBlock(p.set(x + dx, y, z + dz),
                        ((dx + dz & 3) == 0 ? Labyrinth.ANCIENT_STONE : Labyrinth.ANCIENT_BRICKS).defaultBlockState(), 2);
            if (distanceSquared >= 7 && distanceSquared <= 13) {
                level.setBlock(p.set(x + dx, y + 1, z + dz), Labyrinth.ANCIENT_BRICKS.defaultBlockState(), 2);
                if ((Math.abs(dx) == 3 && dz == 0) || (Math.abs(dz) == 3 && dx == 0))
                    level.setBlock(p.set(x + dx, y + 2, z + dz), Labyrinth.ANCIENT_STONE_WALL.defaultBlockState(), 2);
            }
        }
        int shaftBottom = level.getMinY() + 5;
        for (int shaftY = y; shaftY >= shaftBottom; shaftY--) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            int distanceSquared = dx * dx + dz * dz;
            if (distanceSquared <= 5)
                level.setBlock(p.set(x + dx, shaftY, z + dz), Blocks.AIR.defaultBlockState(), 2);
            else if (distanceSquared <= 13) {
                int depth = y - shaftY;
                Block lining = depth % 9 == 0 || ((dx + dz + depth) & 15) == 0
                        ? Labyrinth.ANCIENT_STONE : Labyrinth.ANCIENT_BRICKS;
                level.setBlock(p.set(x + dx, shaftY, z + dz), lining.defaultBlockState(), 2);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
            level.setBlock(p.set(x + dx, shaftBottom - 1, z + dz), Labyrinth.ANCIENT_STONE.defaultBlockState(), 2);
        level.setBlock(p.set(x, shaftBottom, z), Blocks.SOUL_LANTERN.defaultBlockState(), 2);
    }

    public static void tickPlayer(ServerPlayer player) {
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
            if (dx * dx + dz * dz < 2.55D * 2.55D) {
                int surface = GATEWAY_SURFACE_Y.computeIfAbsent(player.level().getSeed(), ignored ->
                        player.level().getHeight(Heightmap.Types.WORLD_SURFACE, gateway.getX(), gateway.getZ()));
                if (player.getY() > surface + 0.4D || player.getY() < surface - 2.2D) return;
                ServerLevel maze = player.level().getServer().getLevel(Labyrinth.LABYRINTH_LEVEL);
                if (maze != null) beginTransition(player, maze);
            }
        } else if (player.level().dimension().equals(Labyrinth.LABYRINTH_LEVEL)) {
            if (rescueFromMazeVoid(player)) return;
            tickElectrified(player);
            tickMazeWard(player);
        } else {
            ABOVE_WALL_TICKS.remove(player.getUUID());
        }
    }

    /** Returns a falling player to the closest loaded solid surface before void damage can begin. */
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
        if (dx * dx + dz * dz > 2.55D * 2.55D
                || Math.abs(player.getY() - portal.surfaceY) > 2.35D) return false;
        ServerLevel maze = player.level().getServer().getLevel(Labyrinth.LABYRINTH_LEVEL);
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
                AABB intake = new AABB(center.x - 2.05D, center.y - 2.35D, center.z - 2.05D,
                        center.x + 2.05D, center.y + 2.35D, center.z + 2.05D);
                for (Entity entity : source.getEntitiesOfClass(Entity.class, intake,
                        entity -> !(entity instanceof ServerPlayer) && !entity.isRemoved())) {
                    double dx = entity.getX() - center.x;
                    double dz = entity.getZ() - center.z;
                    if (dx * dx / (2.05D * 2.05D) + dz * dz / (1.55D * 1.55D) <= 1.0D)
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

            ServerLevel maze = server.getLevel(Labyrinth.LABYRINTH_LEVEL);
            boolean moved = false;
            if (maze != null) {
                BlockPos landing = prepareMazeEntrance(maze);
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

    public static boolean isElectrified(LivingEntity entity) {
        return entity != null && ELECTRIFIED.containsKey(entity.getUUID());
    }

    public static void clearRuntimeState(MinecraftServer server) {
        ServerLevel maze = server.getLevel(Labyrinth.LABYRINTH_LEVEL);
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
        PENDING_TRANSITIONS.clear();
        PHASING_ENTITIES.clear();
        ABOVE_WALL_TICKS.clear();
        WARD_FALL_PROTECTION.clear();
        LAST_PORTAL_SYNC.clear();
        GATEWAY_SURFACE_Y.clear();
        PLAYER_PLACED_BLOCKS.clear();
        DECAYING_BLOCKS.clear();
        RESTORING_BLOCKS.clear();
        MAZE_TOPOLOGIES.clear();
        summonedPortal = null;
        MazeNbtStructures.clearRuntimeState();
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
        Integer protection = WARD_FALL_PROTECTION.get(entity.getUUID());
        if (protection != null) {
            entity.resetFallDistance();
            if (protection <= 1) {
                WARD_FALL_PROTECTION.remove(entity.getUUID());
            } else if (entity.onGround() && entity.getY() < FLOOR_Y + 4.0D) {
                // Keep a short landing grace so a client ragdoll contact packet cannot race the
                // server's ordinary on-ground detection and deal delayed impact damage.
                WARD_FALL_PROTECTION.put(entity.getUUID(), Math.min(40, protection - 1));
            } else {
                WARD_FALL_PROTECTION.put(entity.getUUID(), protection - 1);
            }
        }
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        int mazeLimit = config.mazeRadiusCells * config.cellSize;
        boolean exemptPlayer = entity instanceof ServerPlayer player && (player.isCreative() || player.isSpectator());
        boolean aboveMaze = Math.abs(entity.getX()) < mazeLimit && Math.abs(entity.getZ()) < mazeLimit
                && entity.getY() > FLOOR_Y + config.wallHeight + 0.25D;
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
        Vec3 launch = new Vec3(inward.x * 2.15D, -1.25D, inward.z * 2.15D);
        MazeZapPayload payload = new MazeZapPayload(entity.getId(), source, launch, chargeTicks);
        Set<ServerPlayer> viewers = new HashSet<>(PlayerLookup.tracking(entity));
        if (entity instanceof ServerPlayer player) viewers.add(player);
        viewers.forEach(viewer -> {
            if (ServerPlayNetworking.canSend(viewer, MazeZapPayload.TYPE))
                ServerPlayNetworking.send(viewer, payload);
        });
        ServerLevel level = (ServerLevel) entity.level();
        entity.hurtServer(level, entity.damageSources().lightningBolt(), 2.0F);

        // Non-player entities and clients without the ragdoll channel retain
        // an ordinary authoritative knockback fallback.
        entity.setDeltaMovement(launch);
        entity.hurtMarked = true;
        entity.resetFallDistance();
        electrify(entity, chargeTicks);
        WARD_FALL_PROTECTION.put(entity.getUUID(), 240);
    }

    public static Vec3 nearestMazeCorridor(double x, double z) {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        int limit = config.mazeRadiusCells * config.cellSize;
        int size = config.mazeRadiusCells * 2;
        int gx = Mth.clamp(Math.floorDiv(Mth.floor(x) + limit, config.cellSize), 0, size - 1);
        int gz = Mth.clamp(Math.floorDiv(Mth.floor(z) + limit, config.cellSize), 0, size - 1);
        int center = config.wallThickness + (config.cellSize - config.wallThickness) / 2;
        return new Vec3(-limit + gx * config.cellSize + center + 0.5D,
                FLOOR_Y + 1.0D, -limit + gz * config.cellSize + center + 0.5D);
    }

    private static void beginTransition(ServerPlayer player, ServerLevel maze) {
        BlockPos destination = sharedMazeArrival();
        PendingTransition pending = new PendingTransition(maze, destination,
                player.isInvulnerable(), player.isNoGravity(), player.noPhysics);
        PENDING_TRANSITIONS.put(player.getUUID(), pending);
        // Keep a minimal render-safe region resident while its chunks are filled incrementally.
        maze.getChunkSource().addTicketWithRadius(TicketType.PORTAL, pending.destinationChunk, 1);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.noPhysics = true;
        player.setDeltaMovement(Vec3.ZERO);
        if (ServerPlayNetworking.canSend(player, DimensionTransitionPayload.TYPE))
            ServerPlayNetworking.send(player, new DimensionTransitionPayload(4, 0));
    }

    private static void tickTransition(ServerPlayer player, PendingTransition pending) {
        try {
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            // Let the body visibly phase through terrain before the dimension tears it away.
            if (!pending.teleported && pending.ticks < 6)
                player.setPos(player.getX(), player.getY() - (0.10D + pending.ticks * 0.012D), player.getZ());
            // One synchronous maze chunk per tick keeps the server responsive during generation.
            if (pending.preloadIndex < PRELOAD_OFFSETS.length) {
                int[] offset = PRELOAD_OFFSETS[pending.preloadIndex++];
                pending.maze.getChunk((pending.destination.getX() >> 4) + offset[0],
                        (pending.destination.getZ() >> 4) + offset[1]);
            }
            if (!pending.teleported && pending.preloadIndex >= PRELOAD_OFFSETS.length) {
                prepareMazeArrival(pending.maze, pending.destination);
                long facingRoll = mix(pending.maze.getSeed() ^ pending.destination.asLong());
                float yaw = (float) Math.floorMod(facingRoll, 360);
                player.teleportTo(pending.maze, pending.destination.getX() + 0.5,
                        pending.destination.getY(), pending.destination.getZ() + 0.5,
                        java.util.Set.of(), yaw, 0, true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                pending.teleported = true;
            }
            if (pending.teleported && !pending.clientReady) {
                player.setPos(pending.destination.getX() + 0.5D,
                        pending.destination.getY(), pending.destination.getZ() + 0.5D);
            }
            pending.ticks++;
            if (pending.teleported && pending.clientReady) finishTransition(player, pending);
            else if (pending.ticks >= 240) {
                Labyrinth.LOGGER.warn("Transition ready acknowledgement timed out for {}; releasing safely",
                        player.getScoreboardName());
                finishTransition(player, pending);
            }
        } catch (RuntimeException exception) {
            Labyrinth.LOGGER.error("Labyrinth transition failed safely for {}", player.getScoreboardName(), exception);
            finishTransition(player, pending);
        }
    }

    public static void markTransitionReady(ServerPlayer player) {
        PendingTransition pending = PENDING_TRANSITIONS.get(player.getUUID());
        if (pending != null && pending.teleported
                && player.level().dimension().equals(Labyrinth.LABYRINTH_LEVEL))
            pending.clientReady = true;
    }

    private static void finishTransition(ServerPlayer player, PendingTransition pending) {
        pending.maze.getChunkSource().removeTicketWithRadius(
                TicketType.PORTAL, pending.destinationChunk, 1);
        player.setInvulnerable(pending.wasInvulnerable);
        player.setNoGravity(pending.hadNoGravity);
        player.noPhysics = pending.wasNoPhysics;
        if (pending.teleported && player.level().dimension().equals(Labyrinth.LABYRINTH_LEVEL)) {
            // A short fall inside the corridor preserves the ragdoll entrance without exposing
            // the maze from above. This begins only after the landing buffer/client handshake.
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

    /** Every portal and every player shares one multiplayer rendezvous/skyfall column. */
    private static BlockPos sharedMazeArrival() {
        return mazeEntrancePosition().atY(FLOOR_Y + 12);
    }

    private static int skyfallY() {
        return FLOOR_Y + LabyrinthConfig.INSTANCE.wallHeight + SKYFALL_CLEARANCE;
    }

    private static void prepareMazeArrival(ServerLevel maze, BlockPos arrival) {
        maze.getChunkAt(arrival);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            for (int depth = 0; depth < config.floorThickness; depth++)
                maze.setBlock(p.set(arrival.getX() + dx, FLOOR_Y - depth, arrival.getZ() + dz),
                        Labyrinth.ANCIENT_STONE.defaultBlockState(), 2);
            // Only clear the occupied corridor volume. Clearing a column all the way to the old
            // skyfall height performed thousands of needless block updates during handoff.
            int clearTop = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? FLOOR_Y + 15 : FLOOR_Y + 5;
            for (int y = 1; y <= clearTop - FLOOR_Y; y++)
                maze.setBlock(p.set(arrival.getX() + dx, FLOOR_Y + y, arrival.getZ() + dz),
                        Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static BlockPos prepareMazeEntrance(ServerLevel maze) {
        BlockPos entrance = mazeEntrancePosition();
        int cell = LabyrinthConfig.INSTANCE.cellSize;
        int radius = LabyrinthConfig.INSTANCE.mazeRadiusCells;
        maze.getChunkAt(entrance);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int depth = 0; depth < LabyrinthConfig.INSTANCE.floorThickness; depth++) {
                maze.setBlock(p.set(entrance.getX() + dx, FLOOR_Y - depth, entrance.getZ() + dz),
                        patternedFloor(maze.getSeed(), entrance.getX() + dx, entrance.getZ() + dz,
                                depth, null, cell, radius).defaultBlockState(), 2);
            }
            for (int y = 1; y <= 3; y++)
                maze.setBlock(p.set(entrance.getX() + dx, FLOOR_Y + y, entrance.getZ() + dz),
                        Blocks.AIR.defaultBlockState(), 2);
        }
        return entrance;
    }

    private static BlockPos mazeEntrancePosition() {
        int cell = LabyrinthConfig.INSTANCE.cellSize;
        int thickness = LabyrinthConfig.INSTANCE.wallThickness;
        int radius = LabyrinthConfig.INSTANCE.mazeRadiusCells;
        int limit = radius * cell;
        int interiorCenter = thickness + (cell - thickness) / 2;
        return new BlockPos(interiorCenter, FLOOR_Y + 1,
                limit - 1 - (cell - thickness) / 2);
    }

    private static void buildMazeChunk(ServerLevel level, LevelChunk chunk, BlockPos marker) {
        LabyrinthConfig config = LabyrinthConfig.INSTANCE;
        int radius = config.mazeRadiusCells;
        int cell = config.cellSize;
        int thickness = config.wallThickness;
        int limit = radius * cell;
        long seed = level.getSeed();
        MazeTopology topology = topology(seed, radius, config.mazeLoopChance, config.mazeLandmarkChance);
        MazeNbtStructures.Layout structures = ENABLE_MAZE_NBT_STRUCTURES
                ? MazeNbtStructures.layout(level, radius, cell, topology::canReserveStructure)
                : MazeNbtStructures.emptyLayout();
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int startX = Math.max(chunkPos.getMinBlockX(), -limit);
        int endX = Math.min(chunkPos.getMaxBlockX(), limit - 1);
        int startZ = Math.max(chunkPos.getMinBlockZ(), -limit);
        int endZ = Math.min(chunkPos.getMaxBlockZ(), limit - 1);

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {

                if (isPitOpening(x, z)) {
                    placeFloorColumn(chunk, p, seed, x, z, BOSS_FLOOR_Y, config.floorThickness,
                            topology, cell, radius);
                    continue;
                }

                placeFloorColumn(chunk, p, seed, x, z, FLOOR_Y, config.floorThickness,
                        topology, cell, radius);

                if (isPitShaftWall(x, z)) {
                    for (int y = BOSS_FLOOR_Y + 1; y <= FLOOR_Y; y++)
                        bufferedSet(chunk, x, y, z, patternedWall(seed, x, y, z).defaultBlockState());
                    continue;
                }

                boolean wall = isWall(topology, structures, x, z, cell, thickness, radius);
                if (wall) {
                    for (int y = 1; y <= config.wallHeight; y++)
                        bufferedSet(chunk, x, FLOOR_Y + y, z, patternedWall(seed, x, y, z).defaultBlockState());
                } else {
                    if (isArchOpening(topology, x, z, cell, thickness, radius)) {
                        int archY = Math.max(9, config.wallHeight / 3);
                        for (int y = archY; y <= archY + 2; y++)
                            bufferedSet(chunk, x, FLOOR_Y + y, z, patternedWall(seed, x, y, z).defaultBlockState());
                    }
                    placeDecorationColumn(chunk, p, topology, structures, seed, x, z, cell, thickness, radius,
                            config.wallHeight);
                }
            }
        }

        bufferedSet(chunk, marker.getX(), marker.getY(), marker.getZ(), Blocks.BEDROCK.defaultBlockState());
        for (LevelChunkSection section : chunk.getSections())
            if (!section.hasOnlyAir()) section.recalcBlockCounts();
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.markUnsaved();
        structures.onChunkBuilt(chunk);
    }

    private static void placeFloorColumn(LevelChunk chunk, BlockPos.MutableBlockPos p, long seed,
                                         int x, int z, int topY, int depth, MazeTopology topology,
                                         int cell, int radius) {
        for (int layer = 0; layer < depth; layer++) {
            bufferedSet(chunk, x, topY - layer, z,
                    patternedFloor(seed, x, z, layer, topology, cell, radius).defaultBlockState());
        }
    }

    /** Palette-buffer write used only while constructing a new empty maze chunk. */
    private static void bufferedSet(LevelChunk chunk, int x, int y, int z, BlockState state) {
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        section.setBlockState(x & 15, y & 15, z & 15, state, false);
    }

    private static boolean isPitOpening(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) <= PIT_HALF_WIDTH;
    }

    private static boolean isPitShaftWall(int x, int z) {
        int distance = Math.max(Math.abs(x), Math.abs(z));
        return distance > PIT_HALF_WIDTH && distance <= PIT_HALF_WIDTH + PIT_WALL_THICKNESS;
    }

    private static boolean isCenterArena(int x, int z, int cell) {
        return Math.abs(x) < cell * 3 && Math.abs(z) < cell * 3;
    }

    private static boolean isWall(MazeTopology topology, MazeNbtStructures.Layout structures,
                                  int x, int z, int size, int thickness, int radius) {
        int limit = radius * size;
        if (isCenterArena(x, z, size)) return false;
        if (structures.reserved(x, z)) return false;
        if (z >= limit - size && x >= thickness && x < size) return false;
        if (x >= limit - thickness || z >= limit - thickness) return true;
        int gx = Math.floorDiv(x + limit, size);
        int gz = Math.floorDiv(z + limit, size);
        int lx = Math.floorMod(x + limit, size);
        int lz = Math.floorMod(z + limit, size);
        if (lx < thickness && lz < thickness) return true;
        if (lx < thickness) return gx == 0 || !topology.open(gx - 1, gz, gx, gz);
        if (lz < thickness) return gz == 0 || !topology.open(gx, gz - 1, gx, gz);
        return false;
    }

    private static boolean isArchOpening(MazeTopology topology, int x, int z, int size,
                                         int thickness, int radius) {
        if (isCenterArena(x, z, size)) return false;
        int limit = radius * size;
        int gx = Math.floorDiv(x + limit, size);
        int gz = Math.floorDiv(z + limit, size);
        int lx = Math.floorMod(x + limit, size);
        int lz = Math.floorMod(z + limit, size);
        if (lx < thickness && lz >= thickness && gx > 0 && topology.open(gx - 1, gz, gx, gz))
            return topology.arch(gx - 1, gz, gx, gz);
        return lz < thickness && lx >= thickness && gz > 0
                && topology.open(gx, gz - 1, gx, gz) && topology.arch(gx, gz - 1, gx, gz);
    }

    private static void placeDecorationColumn(LevelChunk chunk, BlockPos.MutableBlockPos p,
                                              MazeTopology topology, MazeNbtStructures.Layout structures,
                                              long seed, int x, int z,
                                              int cell, int thickness, int radius, int wallHeight) {
        if (isCenterArena(x, z, cell)) {
            int offset = cell * 2;
            boolean arenaPillar = Math.abs(Math.abs(x) - offset) <= 1
                    && Math.abs(Math.abs(z) - offset) <= 1;
            if (arenaPillar) {
                for (int y = 1; y <= wallHeight; y++)
                    bufferedSet(chunk, x, FLOOR_Y + y, z, patternedWall(seed, x, y, z).defaultBlockState());
            }
            return;
        }
        if (structures.reserved(x, z)) return;

        int limit = radius * cell;
        int gx = Math.floorDiv(x + limit, cell);
        int gz = Math.floorDiv(z + limit, cell);
        int lx = Math.floorMod(x + limit, cell);
        int lz = Math.floorMod(z + limit, cell);
        if (!topology.inBounds(gx, gz)) return;
        int center = thickness + (cell - thickness) / 2;
        int innerA = thickness + 2;
        int innerB = cell - 3;

        long supply = mix(seed ^ (long) gx * 0xC2B2AE3D27D4EB4FL
                ^ (long) gz * 0x165667B19E3779F9L);
        if (lx == center + 1 && lz == center - 1 && Math.floorMod(supply, 311) == 0) {
            BlockPos barrelPos = new BlockPos(x, FLOOR_Y + 1, z);
            chunk.setBlockState(barrelPos, Blocks.BARREL.defaultBlockState(), 0);
            if (chunk.getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel) {
                barrel.setLootTable(MAZE_BARREL_LOOT);
                barrel.setLootTableSeed(supply);
                barrel.setChanged();
            }
            if (Math.floorMod(supply >>> 17, 3) == 0)
                bufferedSet(chunk, x, FLOOR_Y + 2, z, Blocks.COBWEB.defaultBlockState());
        }

        if (topology.hasTrait(gx, gz, MazeTopology.PILLAR_HALL)
                && (lx == innerA || lx == innerB) && (lz == innerA || lz == innerB)) {
            int height = 8 + (int) Math.floorMod(mix(seed ^ ((long) x << 32) ^ z), 7);
            for (int y = 1; y <= height; y++) {
                Block block = y == 1 || y == height ? Blocks.CHISELED_DEEPSLATE
                        : y % 5 == 0 ? Blocks.POLISHED_BASALT : Blocks.POLISHED_DEEPSLATE;
                bufferedSet(chunk, x, FLOOR_Y + y, z, block.defaultBlockState());
            }
        }

        if (topology.hasTrait(gx, gz, MazeTopology.DEAD_END_ALTAR) && lx == center && lz == center) {
            bufferedSet(chunk, x, FLOOR_Y + 1, z, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
            bufferedSet(chunk, x, FLOOR_Y + 2, z, Blocks.POLISHED_BASALT.defaultBlockState());
            bufferedSet(chunk, x, FLOOR_Y + 3, z, Blocks.SOUL_LANTERN.defaultBlockState());
        }

        if (topology.hasTrait(gx, gz, MazeTopology.RUBBLE)) {
            long rubble = mix(seed ^ (long) gx * 0x9E3779B97F4A7C15L ^ (long) gz * 0xD1B54A32D192ED03L);
            int span = Math.max(1, cell - thickness - 3);
            int rx = thickness + 1 + (int) Math.floorMod(rubble, span);
            int rz = thickness + 1 + (int) Math.floorMod(rubble >>> 12, span);
            if (lx == rx && lz == rz) {
                Block rubbleBlock = (rubble & 1) == 0 ? Blocks.COBBLED_DEEPSLATE : Blocks.TUFF;
                bufferedSet(chunk, x, FLOOR_Y + 1, z, rubbleBlock.defaultBlockState());
                if ((rubble & 7) == 0)
                    bufferedSet(chunk, x, FLOOR_Y + 2, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
            }
        }

        if (topology.hasTrait(gx, gz, MazeTopology.PLAZA)) {
            int distance = Math.max(Math.abs(lx - center), Math.abs(lz - center));
            if (distance == 2 && (lx == center || lz == center))
                bufferedSet(chunk, x, FLOOR_Y + 1, z, Blocks.POLISHED_TUFF.defaultBlockState());
            if (lx == center && lz == center) {
                bufferedSet(chunk, x, FLOOR_Y + 1, z, Blocks.CHISELED_TUFF_BRICKS.defaultBlockState());
                bufferedSet(chunk, x, FLOOR_Y + 2, z, Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }

        if (topology.hasTrait(gx, gz, MazeTopology.GARDEN)) {
            int dx = Math.abs(lx - center), dz = Math.abs(lz - center);
            if (dx == 2 && dz == 2)
                bufferedSet(chunk, x, FLOOR_Y + 1, z, Blocks.AZALEA.defaultBlockState());
            else if ((dx == 3 && dz == 2) || (dx == 2 && dz == 3))
                bufferedSet(chunk, x, FLOOR_Y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    private static Block patternedWall(long seed, int x, int y, int z) {
        return Labyrinth.ANCIENT_BRICKS;
    }

    private static Block patternedFloor(long seed, int x, int z, int depth, MazeTopology topology,
                                        int cell, int radius) {
        if (depth > 0) {
            int foundation = x * 31 ^ z * 17 ^ depth * 13 ^ (int) seed;
            return (foundation & 3) == 0 ? Labyrinth.ANCIENT_STONE : Labyrinth.ANCIENT_BRICKS;
        }
        if (topology != null && topology.onSolutionTrail(x, z, cell, radius)) {
            long trail = mix(seed ^ (long) x * 31L ^ z);
            return (trail & 7) == 0 ? Labyrinth.ANCIENT_BRICKS : Labyrinth.ANCIENT_STONE;
        }
        long patch = mix(seed ^ (long) Math.floorDiv(x, 5) * 0x9E3779B97F4A7C15L
                ^ (long) Math.floorDiv(z, 5) * 0xD1B54A32D192ED03L);
        long detail = mix(patch ^ (long) x * 341873128712L ^ (long) z * 132897987541L);
        if (Math.floorMod(detail, 17) == 0) return Labyrinth.ANCIENT_BRICKS;
        return Labyrinth.ANCIENT_STONE;
    }

    private static float unitFloat(long value) {
        return (value >>> 40) / (float) (1L << 24);
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
            assignLandmarks(landmarkChance);
            markReachableFromCenter();
            int entrance = index(size / 2, size - 1);
            if (!reachableFromCenter[entrance])
                throw new IllegalStateException("Generated labyrinth entrance cannot reach its center");
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
                int northRun = 4 + (int) Math.floorMod(choice, 10);
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
                int maxHorizontal = 5 + (int) Math.floorMod(choice >>> 32, 11);
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

        private void assignLandmarks(int landmarkChance) {
            for (int z = 1; z < size - 1; z++) for (int x = 1; x < size - 1; x++) {
                int cell = index(x, z);
                if ((traits[cell] & ROOM) != 0 || solutionOpenings[cell] != 0) continue;
                long roll = mix(seed ^ (long) cell * 0x9E3779B97F4A7C15L);
                int degree = Integer.bitCount(openings[cell] & 15);
                if (degree == 1 && Math.floorMod(roll, 3) == 0) traits[cell] |= DEAD_END_ALTAR;
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
            throw new IllegalStateException("Generated labyrinth has no entrance-to-center solution route");
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
            int gx = Math.floorDiv(worldX + limit, cellSize);
            int gz = Math.floorDiv(worldZ + limit, cellSize);
            if (!inBounds(gx, gz)) return false;
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
