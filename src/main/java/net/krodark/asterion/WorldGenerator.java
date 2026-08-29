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
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.krodark.asterion.network.ragdoll.RagdollExplosionPayload;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.worldgen.MazeNbtStructures;
import net.krodark.asterion.block.RuneDoorBlock;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
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
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WorldGenerator {
    private static final boolean ENABLE_MAZE_NBT_STRUCTURES = true;
    private static final int FLOOR_Y = 48;
    private static final int BOSS_FLOOR_Y = 36;
    private static final int DIMENSION_CEILING_Y = 127;
    private static final int PIT_HALF_WIDTH = 34;
    private static final int PIT_WALL_THICKNESS = 6;
    private static final int SKYFALL_CLEARANCE = 42;
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
    private static final Map<UUID, Long> LAST_PORTAL_SYNC = new HashMap<>();
    private static final Map<UUID, PhasingEntity> PHASING_ENTITIES = new HashMap<>();
    private static SummonedPortal summonedPortal;
    private static final Map<UUID, Integer> ABOVE_WALL_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> WARD_FALL_PROTECTION = new HashMap<>();
    private static final Map<UUID, ElectrifiedState> ELECTRIFIED = new HashMap<>();
    private static final Map<UUID, Long> ROAMER_REVEAL_TICKS = new HashMap<>();
    private static final Set<UUID> BOSS_ENTRANTS = new HashSet<>();
    private static boolean bossArenaPrepared;
    private static BossArenaBuild bossArenaBuild;
    private static BossFinale bossFinale;
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

        if (newlyGenerated) {
            MazeNbtStructures.markCopperClean(chunk);
        } else {
            MazeNbtStructures.cleanLegacyCopper(chunk,
                    BOSS_FLOOR_Y - AsterionConfig.INSTANCE.floorThickness,
                    FLOOR_Y + AsterionConfig.INSTANCE.wallHeight);
        }

        if (ENABLE_MAZE_NBT_STRUCTURES) {
            AsterionConfig config = AsterionConfig.INSTANCE;
            MazeNbtStructures.Layout layout = MazeNbtStructures.layout(level,
                    config.mazeRadiusCells, config.cellSize,
                    (minX, minZ, maxX, maxZ) -> {
                        int center = config.mazeRadiusCells;
                        return maxX < center - 6 || minX > center + 6
                                || maxZ < center - 6 || minZ > center + 6;
                    });
            layout.onChunkBuilt(chunk);
        }
    }

    public static void onChunkGenerate(ServerLevel level, LevelChunk chunk) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        ChunkPos pos = chunk.getPos();
        BlockPos marker = new BlockPos(pos.getMinBlockX(), 1, pos.getMinBlockZ());
        if (!chunk.getBlockState(marker).is(Blocks.BEDROCK)) buildMazeChunk(level, chunk, marker);
        MazeNbtStructures.markCopperClean(chunk);
    }

    public static void tickServer(MinecraftServer server) {
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

    private static void tickMaze(ServerLevel maze) {
        tickBossFinale(maze);
        tickBossArenaGrowth(maze);
        tickBossArenaDebris(maze);
        tickMinotaurDirector(maze);
        tickMazeEntities(maze);
        if (ENABLE_MAZE_NBT_STRUCTURES && PENDING_TRANSITIONS.isEmpty()) {
            MazeNbtStructures.tick(maze);
        }
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
    }

    private static void tickRuneCheckpoint(ServerPlayer player) {
        ServerLevel level = (ServerLevel)player.level();
        BlockPos checkpoint = MazeNbtStructures.safeCheckpointNear(level, player.blockPosition(), 7.0D);
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
        BlockPos checkpoint = AsterionWorldState.get(maze).runeCheckpoint(player.getUUID());
        if (checkpoint != null) {
            maze.getChunkAt(checkpoint);
            if (!MazeNbtStructures.isSafeCheckpoint(maze, checkpoint)) checkpoint = null;
        }
        if (checkpoint == null)
            checkpoint = MazeNbtStructures.nearestSafeHouse(maze, deathPosition);
        if (checkpoint == null) {
            checkpoint = randomMazeArrival(maze, player.getUUID(), 0L);
            prepareMazeArrival(maze, checkpoint);
        }
        maze.getChunkAt(checkpoint);
        player.teleportTo(maze, checkpoint.getX() + 0.5D, checkpoint.getY(), checkpoint.getZ() + 0.5D,
                Set.of(), player.getYRot(), 0.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        WARD_FALL_PROTECTION.put(player.getUUID(), 100);
    }

    public static boolean resetBossEncounterAfterDeath(ServerPlayer deadPlayer) {
        if (!(deadPlayer.level() instanceof ServerLevel maze)
                || !maze.dimension().equals(Asterion.ASTERION_LEVEL)
                || AsterionWorldState.get(maze).minotaurDefeated()) return false;
        boolean pitDeath = isInsideBossArena(deadPlayer.position())
                || (BOSS_ENTRANTS.contains(deadPlayer.getUUID()) && isBossEncounterActive(maze));
        if (!pitDeath) return false;

        for (Entity entity : maze.getAllEntities()) {
            if (entity instanceof MinotaurEntity minotaur
                    && minotaur.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS)
                minotaur.discard();
        }
        BOSS_ENTRANTS.clear();
        bossFinale = null;
        clearBossArenaTransientState(maze);
        rebuildBossArena(maze);
        ELECTRIFIED.remove(deadPlayer.getUUID());
        WARD_FALL_PROTECTION.remove(deadPlayer.getUUID());
        Asterion.LOGGER.info("Reset unfinished Minotaur encounter after {} died in the central pit",
                deadPlayer.getGameProfile().name());
        return true;
    }

    private static void clearBossArenaTransientState(ServerLevel level) {
        int outer = PIT_HALF_WIDTH + PIT_WALL_THICKNESS + 2;
        AABB arena = new AABB(-outer, BOSS_FLOOR_Y - 20, -outer,
                outer + 1, DIMENSION_CEILING_Y + 1, outer + 1);
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof FallingBlockEntity && arena.intersects(entity.getBoundingBox()))
                entity.discard();
        }
        DECAYING_BLOCKS.removeIf(entry -> entry.dimension.equals(level.dimension())
                && arena.contains(Vec3.atCenterOf(entry.pos)));
        RESTORING_BLOCKS.removeIf(entry -> entry.dimension.equals(level.dimension())
                && arena.contains(Vec3.atCenterOf(entry.pos)));
    }

    public static boolean isNearSafeRune(ServerLevel level, BlockPos center) {
        return MazeNbtStructures.safeCheckpointNear(level, center, 9.0D) != null;
    }

    public static void solveRuneRoom(ServerLevel level, BlockPos runePos, ServerPlayer player, int color) {
        BlockPos checkpoint = null;
        int opened = 0;
        for (BlockPos cursor : BlockPos.betweenClosed(runePos.offset(-10, -4, -10), runePos.offset(10, 7, 10))) {
            BlockState state = level.getBlockState(cursor);
            if (state.is(Blocks.LODESTONE)) checkpoint = cursor.above().immutable();
            if (state.is(Asterion.RUNE_ZONE_DOOR) && !state.getValue(RuneDoorBlock.OPEN)) {
                level.setBlock(cursor, state.setValue(RuneDoorBlock.OPEN, true), 3);
                opened++;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, cursor.getX() + 0.5D,
                        cursor.getY() + 0.5D, cursor.getZ() + 0.5D, 3,
                        0.25D, 0.35D, 0.25D, 0.025D);
            }
        }
        if (checkpoint != null) AsterionWorldState.get(level).setRuneCheckpoint(player.getUUID(), checkpoint);
        level.playSound(null, runePos, SoundEvents.RESPAWN_ANCHOR_CHARGE,
                SoundSource.BLOCKS, 1.4F, 0.82F);
        if (opened > 0) level.playSound(null, runePos, SoundEvents.IRON_DOOR_OPEN,
                SoundSource.BLOCKS, 1.25F, 0.62F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, runePos.getX() + 0.5D,
                runePos.getY() + 1.1D, runePos.getZ() + 0.5D, 42,
                1.1D, 1.2D, 1.1D, 0.035D);
        player.sendOverlayMessage(Component.translatable("message.asterion.rune_correct"));
    }

    public static void failRuneRoom(ServerLevel level, BlockPos runePos, ServerPlayer player) {
        level.playSound(null, runePos, SoundEvents.SCULK_SHRIEKER_SHRIEK,
                SoundSource.BLOCKS, 1.25F, 0.72F);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                SoundSource.PLAYERS, 0.9F, 0.48F);
        level.sendParticles(ParticleTypes.SCULK_SOUL, runePos.getX() + 0.5D,
                runePos.getY() + 0.9D, runePos.getZ() + 0.5D, 22,
                0.55D, 0.7D, 0.55D, 0.035D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(),
                player.getY() + 1.0D, player.getZ(), 30,
                0.45D, 0.75D, 0.45D, 0.08D);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 35, 1, false, false));
        Vec3 away = player.position().subtract(Vec3.atCenterOf(runePos));
        if (away.horizontalDistanceSqr() < 0.01D) away = new Vec3(0.0D, 0.0D, 1.0D);
        away = away.normalize().scale(0.65D);
        player.push(away.x, 0.22D, away.z);
        player.hurtMarked = true;
        player.sendOverlayMessage(Component.translatable("message.asterion.rune_wrong"));
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
        DECAYING_BLOCKS.add(new DecayingBlock(level.dimension(), pos.immutable(), state.getBlock(),
                level.getGameTime() + AsterionConfig.INSTANCE.playerBlockDecayTicks));
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
        RESTORING_BLOCKS.add(new RestoringBlock(level.dimension(), pos.immutable(), state,
                level.getGameTime() + 100L));
    }

    public static boolean isActivePortalProtected(ServerLevel level, BlockPos pos) {
        SummonedPortal portal = summonedPortal;
        if (portal == null || !portal.dimension.equals(level.dimension())) return false;
        int dx = Math.abs(pos.getX() - portal.center.getX());
        int dz = Math.abs(pos.getZ() - portal.center.getZ());
        return dx <= 3 && dz <= 3
                && pos.getY() >= portal.surfaceY - 3 && pos.getY() <= portal.surfaceY + 1;
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
                    PLAYER_PLACED_BLOCKS.remove(key);
                    level.destroyBlock(cursor, false, null, 512);
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
                    if (!mazeMasonry || isActivePortalProtected(level, cursor)) continue;
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
        int distance = AsterionConfig.INSTANCE.gatewayDistance;
        return new BlockPos((int) Math.round(Math.cos(angle) * distance), 0,
                (int) Math.round(Math.sin(angle) * distance));
    }

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
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            int x = centerX + dx;
            int z = centerZ + dz;
            if (edge <= 2) {
                level.setBlock(cursor.set(x, surfaceY - 1, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(cursor.set(x, surfaceY - 2, z), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(cursor.set(x, surfaceY - 3, z), Asterion.ANCIENT_STONE.defaultBlockState(), 2);
            } else if (edge == 3) {
                level.setBlock(cursor.set(x, surfaceY - 1, z), gatewayRimState(dx, dz, edge), 2);
                if ((Math.abs(dx) == 3 && dz == 0) || (Math.abs(dz) == 3 && dx == 0)) {
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
        GATEWAY_SURFACE_Y.put(level.getSeed(), y);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
            int edge = Math.max(Math.abs(dx), Math.abs(dz));
            if (edge >= 3 && edge <= 6)
                level.setBlock(p.set(x + dx, y, z + dz), gatewayRimState(dx, dz, edge), 2);
            if (edge == 3) {
                level.setBlock(p.set(x + dx, y + 1, z + dz), Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
                if ((Math.abs(dx) == 3 && dz == 0) || (Math.abs(dz) == 3 && dx == 0))
                    level.setBlock(p.set(x + dx, y + 2, z + dz), Asterion.ANCIENT_STONE_WALL.defaultBlockState(), 2);
            }
        }
        int shaftBottom = level.getMinY() + 5;
        for (int shaftY = y; shaftY >= shaftBottom; shaftY--) for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
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

    private static BlockState gatewayRimState(int dx, int dz, int edgeDistance) {
        if (edgeDistance >= 3 && Math.floorMod(dx * 3 + dz * 5, 5) == 0)
            return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        if (Math.floorMod(dx - dz, 3) == 0) return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
        return ((dx + dz) & 1) == 0 ? Asterion.ANCIENT_STONE.defaultBlockState()
                : Asterion.ANCIENT_BRICKS.defaultBlockState();
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
        if (Math.max(Math.abs(dx), Math.abs(dz)) > 2.45D
                || Math.abs(player.getY() - portal.surfaceY) > 2.35D) return false;
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
                if (entity instanceof MinotaurEntity) entity.discard();
            return;
        }
        List<ServerPlayer> players = maze.players().stream()
                .filter(player -> player.isAlive() && !player.isCreative() && !player.isSpectator()).toList();
        if (players.isEmpty()) return;
        tickCenterInvitation(maze, players);
        List<MinotaurEntity> minotaurs = new ArrayList<>();
        for (Entity entity : maze.getAllEntities())
            if (entity instanceof MinotaurEntity minotaur && minotaur.isAlive() && !minotaur.isRemoved())
                minotaurs.add(minotaur);

        if (minotaurs.size() > 1) {
            MinotaurEntity keeper = minotaurs.stream()
                    .filter(entity -> entity.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS)
                    .findFirst().orElse(minotaurs.get(0));
            for (MinotaurEntity entity : minotaurs)
                if (entity != keeper && entity.canDespawnUnseen()) entity.discard();
            minotaurs = new ArrayList<>(List.of(keeper));
        }

        for (ServerPlayer player : players) {
            boolean atPitThreshold = hasReachedMazeCenter(player.position());
            if (!atPitThreshold || !BOSS_ENTRANTS.add(player.getUUID())) continue;
            prepareAndSealBossArena(maze);
            MinotaurEntity existing = minotaurs.stream().filter(entity -> entity.isAssignedTo(player))
                    .findFirst().orElse(minotaurs.stream().findFirst().orElse(null));
            MinotaurEntity boss = MinotaurEntity.activateCenterBoss(maze, player, existing);
            if (boss != null && !minotaurs.contains(boss)) minotaurs.add(boss);
            zapIntoBossPit(maze, player);
        }

        if (minotaurs.isEmpty() && !DeadSunEventSystem.isEclipseActive(maze)) {
            ServerPlayer candidate = players.stream()
                    .filter(player -> !WorldGenerator.isApproachingCenter(player.position()))
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

    private static void tickCenterInvitation(ServerLevel level, List<ServerPlayer> players) {
        long time = level.getGameTime();
        if (bossArenaPrepared || !level.getChunkSource().hasChunk(0, 0) || (time % 10L) != 0L) return;
        boolean nearby = players.stream().anyMatch(player -> player.distanceToSqr(0.5D, FLOOR_Y, 0.5D) < 150.0D * 150.0D);
        if (!nearby) return;
        double angle = time * 0.075D;
        double radius = 7.0D + Math.sin(time * 0.041D) * 3.0D;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                0.5D + Math.cos(angle) * radius, FLOOR_Y + 1.2D, 0.5D + Math.sin(angle) * radius,
                5, 1.6D, 0.35D, 1.6D, 0.045D);
        level.sendParticles(ParticleTypes.ASH, 0.5D, FLOOR_Y + 4.0D, 0.5D,
                3, PIT_HALF_WIDTH * 0.55D, 2.5D, PIT_HALF_WIDTH * 0.55D, 0.012D);
        if ((time % 160L) == 0L)
            level.playSound(null, BlockPos.ZERO.atY(FLOOR_Y), SoundEvents.END_PORTAL_FRAME_FILL,
                    SoundSource.AMBIENT, 1.7F, 0.42F);
    }

    public static void beginBossFinale(ServerLevel level, MinotaurEntity boss) {
        if (bossFinale != null) return;
        AsterionWorldState.get(level).markMinotaurDefeated();
        bossFinale = new BossFinale(boss.getUUID());
        for (ServerPlayer player : level.players()) {
            bossFinale.previousInvulnerability.put(player.getUUID(), player.isInvulnerable());
            if (ServerPlayNetworking.canSend(player, BossFinalePayload.TYPE))
                ServerPlayNetworking.send(player, BossFinalePayload.INSTANCE);
        }
    }

    private static void tickBossFinale(ServerLevel maze) {
        BossFinale finale = bossFinale;
        if (finale == null) return;
        finale.ticks++;
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
            Entity boss = maze.getEntityInAnyDimension(finale.bossId);
            if (boss != null) boss.discard();
            ServerLevel overworld = maze.getServer().overworld();
            BlockPos spawn = overworld.getLevelData().getRespawnData().pos();
            overworld.getChunkAt(spawn);
            int surface = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    spawn.getX(), spawn.getZ());
            for (ServerPlayer player : new ArrayList<>(maze.players())) {
                player.addItem(new net.minecraft.world.item.ItemStack(Asterion.MINOTAUR_SIGIL));
                player.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ECHO_SHARD, 8));
                player.giveExperiencePoints(750);
                double arrivalY = surface + 18.0D;
                player.teleportTo(overworld, spawn.getX() + 0.5D, arrivalY, spawn.getZ() + 0.5D,
                        Set.of(), player.getYRot(), 0.0F, true);
                Vec3 fall = new Vec3(0.10D, -0.42D, -0.06D);
                player.setDeltaMovement(fall);
                player.hurtMarked = true;
                player.resetFallDistance();
                player.setInvulnerable(true);
                if (ServerPlayNetworking.canSend(player, RagdollImpulsePayload.TYPE))
                    ServerPlayNetworking.send(player, new RagdollImpulsePayload(
                            player.position().add(0.0D, 5.0D, 0.0D), fall, 1.15F));
            }
        }
        if (finale.ticks == 362) {
            ServerLevel overworld = maze.getServer().overworld();
            for (var entry : finale.previousInvulnerability.entrySet()) {
                var found = overworld.getPlayerByUUID(entry.getKey());
                if (found instanceof ServerPlayer player) player.setInvulnerable(entry.getValue());
            }
        }
        if (finale.ticks >= 390) bossFinale = null;
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
                    FallingBlockEntity fragment = FallingBlockEntity.fall(level, cursor.immutable(), state);
                    Vec3 away = new Vec3(dx, 0.5D, dz);
                    if (away.lengthSqr() < 0.01D) away = new Vec3(1.0D, 0.5D, 0.0D);
                    away = away.normalize().scale(0.55D + level.getRandom().nextDouble() * 0.7D);
                    fragment.setDeltaMovement(away.x, 0.7D + level.getRandom().nextDouble(), away.z);
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
                    FallingBlockEntity rubble = FallingBlockEntity.fall(level, cursor.immutable(), state);
                    Vec3 outward = new Vec3(x, 0.0D, z);
                    if (outward.lengthSqr() < 0.01D) outward = new Vec3(1.0D, 0.0D, 0.0D);
                    outward = outward.normalize();
                    rubble.setDeltaMovement(outward.x * (0.35D + level.getRandom().nextDouble() * 0.55D),
                            0.45D + level.getRandom().nextDouble() * 0.75D,
                            outward.z * (0.35D + level.getRandom().nextDouble() * 0.55D));
                    fragments++;
                } else level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, 0.5D, bottomForFinaleRadius(radius) + 3.0D, 0.5D,
                16, radius * 0.55D, 4.0D, radius * 0.55D, 0.045D);
    }

    private static int bottomForFinaleRadius(double radius) {
        return radius <= PIT_HALF_WIDTH + 3 ? BOSS_FLOOR_Y : FLOOR_Y;
    }

    private static void prepareAndSealBossArena(ServerLevel level) {
        if (bossArenaPrepared) return;
        rebuildBossArena(level);
    }

    private static void rebuildBossArena(ServerLevel level) {
        bossArenaPrepared = true;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int outer = PIT_HALF_WIDTH + PIT_WALL_THICKNESS;
        int arenaFloorDepth = Math.max(18, AsterionConfig.INSTANCE.floorThickness * 2);
        for (int x = -outer; x <= outer; x++) for (int z = -outer; z <= outer; z++) {
            double distance = Math.sqrt(x * x + z * z);
            if (distance <= PIT_HALF_WIDTH) {
                for (int depth = 0; depth < arenaFloorDepth; depth++)
                    level.setBlock(pos.set(x, BOSS_FLOOR_Y - depth, z),
                            bossFloorState(x, z, depth), 2);
                for (int y = BOSS_FLOOR_Y + 1; y <= FLOOR_Y; y++)
                    level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            } else if (distance <= outer) {
                for (int y = BOSS_FLOOR_Y + 1; y <= FLOOR_Y + 8; y++)
                    level.setBlock(pos.set(x, y, z), Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
            } else {
                for (int depth = 0; depth < AsterionConfig.INSTANCE.floorThickness; depth++)
                    level.setBlock(pos.set(x, FLOOR_Y - depth, z), bossFloorState(x, z, depth), 2);
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 8; y++)
                    level.setBlock(pos.set(x, y, z), Asterion.ANCIENT_BRICKS.defaultBlockState(), 2);
            }
        }
        bossArenaBuild = new BossArenaBuild(AsterionConfig.INSTANCE.minotaurBossPillarCount);
        queueBossRoomGrowth(bossArenaBuild);
    }

    private static BlockState bossFloorState(int x, int z, int depth) {
        int ring = Mth.floor(Math.sqrt(x * x + z * z));
        if (depth == 0 && (ring % 7 == 0 || (Math.abs(x) <= 1 && Math.abs(z) <= 1)))
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        if (depth == 0 && Math.floorMod(x * 17 + z * 29, 23) == 0)
            return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        return (depth & 3) == 3 ? Asterion.ANCIENT_BRICKS.defaultBlockState()
                : Asterion.ANCIENT_STONE.defaultBlockState();
    }

    private static void queueBossRoomGrowth(BossArenaBuild build) {
        int roofY = BOSS_FLOOR_Y + 18;
        int pillarRadius = Math.max(10, PIT_HALF_WIDTH - 8);
        for (int index = 0; index < build.pillarCount; index++) {
            double angle = Mth.TWO_PI * index / build.pillarCount;
            int cx = Mth.floor(Math.cos(angle) * pillarRadius);
            int cz = Mth.floor(Math.sin(angle) * pillarRadius);
            build.pillars.add(new BossPillar(cx, cz));
        }
        for (BossPillar pillar : build.pillars) {
            int top = bossRoofY(pillar.x, pillar.z);
            for (int y = BOSS_FLOOR_Y + 1; y <= top; y++) {
                int width = y <= BOSS_FLOOR_Y + 2 || y >= top - 1 ? 2 : 1;
                for (int dx = -width; dx <= width; dx++) for (int dz = -width; dz <= width; dz++) {
                if (width == 2 && Math.abs(dx) + Math.abs(dz) > 3) continue;
                boolean cracked = Math.floorMod(pillar.x + dx * 3 + pillar.z + dz * 5 + y, 7) <= 1;
                BlockState state = (cracked ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE)
                        .defaultBlockState();
                build.growth.add(new BuildBlock(new BlockPos(pillar.x + dx, y, pillar.z + dz), state));
            }
            }
        }
        int[] detailRadii = {10, 22, 29};
        for (int radius : detailRadii) for (int index = 0; index < 12; index++) {
            double angle = Mth.TWO_PI * index / 12.0D + radius * 0.07D;
            int x = Mth.floor(Math.cos(angle) * radius);
            int z = Mth.floor(Math.sin(angle) * radius);
            build.growth.add(new BuildBlock(new BlockPos(x, BOSS_FLOOR_Y + 1, z),
                    Blocks.CHISELED_DEEPSLATE.defaultBlockState()));
            if ((index & 2) == 0) build.growth.add(new BuildBlock(
                    new BlockPos(x, BOSS_FLOOR_Y + 2, z), Blocks.SOUL_LANTERN.defaultBlockState()));
        }
        for (int radius = PIT_HALF_WIDTH; radius >= 0; radius--)
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                int radial = Mth.floor(Math.sqrt(x * x + z * z));
                if (radial != radius || radial > PIT_HALF_WIDTH) continue;
                int y = bossRoofY(x, z);
                boolean rib = Math.abs(x) <= 1 || Math.abs(z) <= 1 || Math.floorMod(radius, 7) == 0;
                BlockState roof = Math.floorMod(x * 13 + z * 7, 19) == 0
                        ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                        : rib ? Blocks.POLISHED_DEEPSLATE.defaultBlockState()
                        : Asterion.ANCIENT_BRICKS.defaultBlockState();
                build.growth.add(new BuildBlock(new BlockPos(x, y, z), roof));
                if (rib) build.growth.add(new BuildBlock(new BlockPos(x, y - 1, z), roof));
            }
    }

    private static int bossRoofY(int x, int z) {
        int distance = Mth.floor(Math.sqrt(x * x + z * z));
        return BOSS_FLOOR_Y + 18 + Math.max(0, (PIT_HALF_WIDTH - distance) / 4);
    }

    private static void tickBossArenaGrowth(ServerLevel level) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null || build.ready) return;
        int budget = 96;
        while (budget-- > 0 && !build.growth.isEmpty()) {
            BuildBlock next = build.growth.removeFirst();
            if (!level.getEntities((Entity)null, new AABB(next.pos).deflate(0.02D), Entity::isAlive).isEmpty()) {
                build.growth.addLast(next);
                continue;
            }
            level.setBlock(next.pos, next.state, 2);
            if ((build.growth.size() & 127) == 0)
                level.sendParticles(ParticleTypes.LARGE_SMOKE, next.pos.getX() + 0.5D,
                        next.pos.getY() + 0.5D, next.pos.getZ() + 0.5D,
                        3, 0.35D, 0.25D, 0.35D, 0.015D);
        }
        if (build.growth.isEmpty()) build.ready = true;
    }

    public static boolean isBossArenaReady() {
        return bossArenaBuild != null && bossArenaBuild.ready;
    }

    public static int bossPillarsRemaining() {
        if (bossArenaBuild == null) return AsterionConfig.INSTANCE.minotaurBossPillarCount;
        return (int)bossArenaBuild.pillars.stream().filter(pillar -> !pillar.broken).count();
    }

    public static boolean breakBossPillar(ServerLevel level, AABB impact) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null || !build.ready) return false;
        int roofY = BOSS_FLOOR_Y + 18;
        for (BossPillar pillar : build.pillars) {
            if (pillar.broken) continue;
            AABB bounds = new AABB(pillar.x - 1, BOSS_FLOOR_Y + 1, pillar.z - 1,
                    pillar.x + 2, roofY + 1, pillar.z + 2);
            if (!impact.intersects(bounds)) continue;
            pillar.broken = true;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int falling = 0;
            for (int x = pillar.x - 2; x <= pillar.x + 2; x++)
                for (int y = BOSS_FLOOR_Y + 1; y <= roofY; y++)
                    for (int z = pillar.z - 2; z <= pillar.z + 2; z++) {
                        cursor.set(x, y, z);
                        BlockState state = level.getBlockState(cursor);
                        if (state.isAir()) continue;
                        if (falling < 28 && Math.floorMod(x * 11 + y * 5 + z * 17, 3) == 0) {
                            falling++;
                            FallingBlockEntity rubble = FallingBlockEntity.fall(level, cursor.immutable(), state);
                            Vec3 away = new Vec3(x - pillar.x, 0.3D, z - pillar.z);
                            if (away.lengthSqr() < 0.01D) away = new Vec3(1, 0.3D, 0);
                            away = away.normalize().scale(0.22D + level.getRandom().nextDouble() * 0.32D);
                            rubble.setDeltaMovement(away.x, 0.28D + level.getRandom().nextDouble() * 0.36D, away.z);
                        } else level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
                    }
            level.sendParticles(ParticleTypes.EXPLOSION, pillar.x + 0.5D, BOSS_FLOOR_Y + 7.0D,
                    pillar.z + 0.5D, 12, 1.2D, 5.0D, 1.2D, 0.04D);
            level.playSound(null, new BlockPos(pillar.x, BOSS_FLOOR_Y + 2, pillar.z),
                    SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.2F, 0.55F);
            return true;
        }
        return false;
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
            if (along <= 1.5D || along >= length + 2.0D || laneDistance > 4.8D) continue;
            double score = laneDistance * 5.0D + length;
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
        int diameter = PIT_HALF_WIDTH * 2 + 1;
        long phase = level.getGameTime() / 10L;
        for (int sample = 0; sample < 96; sample++) {
            long roll = mix(level.getSeed() ^ phase * 0x9E3779B97F4A7C15L
                    ^ sample * 0xD1B54A32D192ED03L);
            int x = (int)Math.floorMod(roll, diameter) - PIT_HALF_WIDTH;
            int z = (int)Math.floorMod(roll >>> 24, diameter) - PIT_HALF_WIDTH;
            if (x * x + z * z > PIT_HALF_WIDTH * PIT_HALF_WIDTH) continue;
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

    public static void collapseBossRoofRing(ServerLevel level, Vec3 origin, int radius) {
        BossArenaBuild build = bossArenaBuild;
        if (build == null || radius < 0 || radius > PIT_HALF_WIDTH) return;
        build.growth.clear();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int spawned = 0;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            int radial = Mth.floor(Math.sqrt(x * x + z * z));
            if (radial != radius) continue;
            int roofY = bossRoofY(x, z);
            for (int y = roofY - 1; y <= roofY; y++) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.isAir()) continue;
                if (spawned < 24 && Math.floorMod(x * 31 + z * 17 + y * 11, 3) == 0) {
                    FallingBlockEntity rubble = FallingBlockEntity.fall(level, cursor.immutable(), state);
                    Vec3 inward = new Vec3(origin.x - (x + 0.5D), 0.0D, origin.z - (z + 0.5D));
                    if (inward.lengthSqr() < 0.01D) inward = new Vec3(0.15D, 0.0D, -0.1D);
                    inward = inward.normalize();
                    rubble.setDeltaMovement(inward.x * (0.05D + level.getRandom().nextDouble() * 0.13D),
                            -0.18D - level.getRandom().nextDouble() * 0.18D,
                            inward.z * (0.05D + level.getRandom().nextDouble() * 0.13D));
                    spawned++;
                } else level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, BOSS_FLOOR_Y + 16.0D, origin.z,
                Math.max(3, radius), Math.max(1, radius * 0.45D), 2.0D,
                Math.max(1, radius * 0.45D), 0.045D);
    }

    public static boolean buryBossInRubble(ServerLevel level, Vec3 origin) {
        BlockPos center = BlockPos.containing(origin);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int y = 0; y <= 7; y++) {
            int radius = Math.max(1, 5 - y / 2);
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                double shape = x * x + z * z + y * 1.2D;
                if (shape > radius * radius + 2.0D || Math.floorMod(x * 17 + z * 31 + y * 13, 7) == 0)
                    continue;
                BlockPos pos = cursor.set(center.getX() + x, BOSS_FLOOR_Y + 1 + y, center.getZ() + z);
                if (!level.getEntitiesOfClass(ServerPlayer.class, new AABB(pos).inflate(0.08D),
                        ServerPlayer::isAlive).isEmpty()) continue;
                BlockState state = Math.floorMod(x * 3 + z * 5 + y, 5) == 0
                        ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
                        : Asterion.ANCIENT_BRICKS.defaultBlockState();
                level.setBlock(pos, state, 2);
                placed++;
            }
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, origin.x, BOSS_FLOOR_Y + 3.0D, origin.z,
                90, 4.5D, 2.8D, 4.5D, 0.045D);
        return placed >= 60;
    }

    public static boolean isBossBuried(ServerLevel level, Vec3 origin) {
        BlockPos center = BlockPos.containing(origin);
        int solids = 0;
        BlockPos lower = new BlockPos(center.getX() - 4, BOSS_FLOOR_Y + 1, center.getZ() - 4);
        BlockPos upper = new BlockPos(center.getX() + 4, BOSS_FLOOR_Y + 8, center.getZ() + 4);
        for (BlockPos pos : BlockPos.betweenClosed(lower, upper))
            if (!level.getBlockState(pos).isAir()) solids++;
        return solids >= 55;
    }

    public static void explodeBossRubble(ServerLevel level, Vec3 origin) {
        BlockPos center = BlockPos.containing(origin);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int spawned = 0;
        for (int x = -5; x <= 5; x++) for (int y = 1; y <= 8; y++) for (int z = -5; z <= 5; z++) {
            cursor.set(center.getX() + x, BOSS_FLOOR_Y + y, center.getZ() + z);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir()) continue;
            if (spawned < 70 && Math.floorMod(x * 11 + y * 7 + z * 17, 3) == 0) {
                spawned++;
                FallingBlockEntity rubble = FallingBlockEntity.fall(level, cursor.immutable(), state);
                Vec3 away = new Vec3(x, 0.4D + y * 0.05D, z);
                if (away.lengthSqr() < 0.01D) away = new Vec3(1, 0.7D, 0);
                away = away.normalize().scale(0.65D + level.getRandom().nextDouble() * 0.75D);
                rubble.setDeltaMovement(away.x, 0.55D + level.getRandom().nextDouble() * 0.8D, away.z);
            } else level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void zapIntoBossPit(ServerLevel level, ServerPlayer player) {
        Vec3 source = new Vec3(AsterionConfig.INSTANCE.deadSunX,
                AsterionConfig.INSTANCE.deadSunHeight, AsterionConfig.INSTANCE.deadSunZ);
        Vec3 towardCenter = bossArenaCenter().subtract(player.position());
        Vec3 horizontal = new Vec3(towardCenter.x, 0.0D, towardCenter.z);
        if (horizontal.lengthSqr() < 0.01D) horizontal = new Vec3(0.12D, 0.0D, 0.08D);
        horizontal = horizontal.normalize();
        if (!level.noCollision(player)) {
            Vec3 clearLip = bossArenaCenter().subtract(horizontal.scale(PIT_HALF_WIDTH - 7.0D));
            player.teleportTo(clearLip.x, Math.max(FLOOR_Y + 0.8D, player.getY()), clearLip.z);
        }
        Vec3 impulse = new Vec3(horizontal.x * 1.22D, -0.18D, horizontal.z * 1.22D);
        MazeZapPayload payload = new MazeZapPayload(player.getId(), source, impulse, 48);
        if (ServerPlayNetworking.canSend(player, MazeZapPayload.TYPE)) ServerPlayNetworking.send(player, payload);
        player.hurtServer(level, player.damageSources().lightningBolt(), 2.0F);
        player.setDeltaMovement(impulse);
        player.hurtMarked = true;
        player.resetFallDistance();
        electrify(player, 48);
        WARD_FALL_PROTECTION.put(player.getUUID(), 200);
    }

    public static boolean isElectrified(LivingEntity entity) {
        return entity != null && ELECTRIFIED.containsKey(entity.getUUID());
    }

    public static void clearRuntimeState(MinecraftServer server) {
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
        bossArenaPrepared = false;
        bossArenaBuild = null;
        bossFinale = null;
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
        prewarmSeed = Long.MIN_VALUE;
        prewarmIndex = 0;
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

    public static boolean hasReachedMazeCenter(Vec3 position) {
        double radius = PIT_HALF_WIDTH - 2.0D;
        return position.x * position.x + position.z * position.z <= radius * radius
                && position.y > BOSS_FLOOR_Y + 1.0D;
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
                && position.y <= FLOOR_Y - 2.0D;
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
        Asterion.LOGGER.info("Preparing Asterion transition for {} ({} buffered chunks)",
                player.getScoreboardName(), PREWARM_OFFSETS.length - prewarmIndex);
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
            if (!pending.teleported && pending.ticks < 6)
                player.setPos(player.getX(), player.getY() - (0.10D + pending.ticks * 0.012D), player.getZ());
            if (!pending.teleported && prewarmIndex < PREWARM_OFFSETS.length) {
                generateNextPrewarmChunk(pending.maze, pending.destination);
                if (prewarmIndex % 12 == 0 || prewarmIndex == PREWARM_OFFSETS.length)
                    Asterion.LOGGER.info("Asterion transition buffer: {}/{} chunks",
                            prewarmIndex, PREWARM_OFFSETS.length);
                pending.ticks++;
                return;
            }
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
                        skyfallY(), pending.destination.getZ() + 0.5,
                        java.util.Set.of(), yaw, 0, true);
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
                pending.teleported = true;
                Asterion.LOGGER.info("Teleported {} into the Asterion", player.getScoreboardName());
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
        int margin = Math.min(3, Math.max(1, radius / 8));
        int usable = Math.max(1, size - margin * 2);
        int gx = margin + (int) Math.floorMod(roll, usable);
        int gz = margin + (int) Math.floorMod(roll >>> 24, usable);

        int centerCell = size / 2;
        if (Math.abs(gx - centerCell) <= 2 && Math.abs(gz - centerCell) <= 2)
            gx = Math.min(size - margin - 1, gx + 4);

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
        generateMazeChunk(chunk, level.getSeed());
    }

    public static void generateMazeChunk(ChunkAccess chunk, long seed) {
        activeMazeTerrainSeed = seed;
        AsterionConfig config = AsterionConfig.INSTANCE;
        int radius = config.mazeRadiusCells;
        int cell = config.cellSize;
        int thickness = config.wallThickness;
        int limit = radius * cell;
        MazeTopology topology = topology(seed, radius, config.mazeLoopChance, config.mazeLandmarkChance);
        MazeNbtStructures.Layout structures = MazeNbtStructures.emptyLayout();
        ChunkPos chunkPos = chunk.getPos();
        BlockPos marker = new BlockPos(chunkPos.getMinBlockX(), 1, chunkPos.getMinBlockZ());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int startX = chunkPos.getMinBlockX();
        int endX = chunkPos.getMaxBlockX();
        int startZ = chunkPos.getMinBlockZ();
        int endZ = chunkPos.getMaxBlockZ();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {

                if (isPitOpening(x, z)) {
                    placeFloorColumn(chunk, p, seed, x, z, BOSS_FLOOR_Y, config.floorThickness,
                            topology, cell, radius);
                    continue;
                }

                int floorY = mazeFloorY(seed, x, z, cell);
                placeFloorColumn(chunk, p, seed, x, z, floorY, config.floorThickness,
                        topology, cell, radius);

                if (isPitShaftWall(x, z)) {
                    for (int y = BOSS_FLOOR_Y + 1; y <= FLOOR_Y; y++)
                        bufferedSet(chunk, x, y, z, Asterion.ANCIENT_BRICKS.defaultBlockState());
                    continue;
                }

                MazeBiome biome = mazeBiomeAt(seed, x, z, cell);
                boolean wall = isWall(topology, structures, seed, biome,
                        x, z, cell, thickness, radius);
                if (wall) {
                    for (int y = 1; y <= config.wallHeight; y++)
                        bufferedSet(chunk, x, floorY + y, z,
                                patternedWall(seed, x, y, z, biome, cell, radius));
                    placeBiomeWallDetail(chunk, seed, x, z, cell, thickness, config.wallHeight, biome, floorY);
                } else {
                    if (needsElevationSlab(seed, x, z, cell, floorY))
                        bufferedSet(chunk, x, floorY + 1, z,
                                Asterion.ANCIENT_STONE_SLAB.defaultBlockState());
                    if (isArchOpening(topology, x, z, cell, thickness, radius)) {
                        int archY = Math.max(9, config.wallHeight / 3);
                        for (int y = archY; y <= archY + 2; y++)
                            bufferedSet(chunk, x, floorY + y, z,
                                    patternedWall(seed, x, y, z, biome, cell, radius));
                    }
                    if (placeMazeMotifColumn(chunk, seed, x, z, cell, thickness,
                            config.wallHeight, biome, radius, floorY)) continue;
                    placeDecorationColumn(chunk, p, topology, structures, seed, x, z, cell,
                            thickness, radius, config.wallHeight, biome, floorY);
                }
            }
        }

        bufferedSet(chunk, marker.getX(), marker.getY(), marker.getZ(), Blocks.BEDROCK.defaultBlockState());
        for (LevelChunkSection section : chunk.getSections())
            if (!section.hasOnlyAir()) section.recalcBlockCounts();
        Heightmap.primeHeightmaps(chunk, EnumSet.allOf(Heightmap.Types.class));
        chunk.markUnsaved();
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
                                  long seed, MazeBiome biome, int x, int z,
                                  int size, int thickness, int radius) {
        int limit = radius * size;
        if (isCenterArena(x, z, size)) return false;
        if (structures.reserved(x, z)) return false;
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

    private static boolean biomeOpensWall(long seed, MazeBiome biome, int ax, int az,
                                          int bx, int bz, boolean northSouthBoundary) {
        if (biome == MazeBiome.ANCIENT) return false;
        int divisor = switch (biome) {
            case OVERGROWN -> 9;
            case WITHERED -> 17;
            case SCORCHED -> 7;
            case COLLAPSED -> 5;
            default -> Integer.MAX_VALUE;
        };
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
                                              MazeBiome biome, int floorY) {
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

        long supply = mix(seed ^ (long) gx * 0xC2B2AE3D27D4EB4FL
                ^ (long) gz * 0x165667B19E3779F9L);
        if (lx == center + 1 && lz == center - 1 && Math.floorMod(supply, 311) == 0) {
            int barrelY = floorY + 3;
            BlockPos barrelPos = new BlockPos(x, barrelY, z);
            BlockState barrelState = Blocks.BARREL.defaultBlockState();
            chunk.setBlockState(barrelPos, barrelState, 0);
            placeLootBarrel(chunk, barrelPos, MAZE_BARREL_LOOT, supply);
            for (int y = barrelY + 1; y <= DIMENSION_CEILING_Y; y++)
                bufferedSet(chunk, x, y, z, Blocks.IRON_CHAIN.defaultBlockState());
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

        if (topology.hasTrait(topologyX, topologyZ, MazeTopology.GARDEN)) {
            int dx = Math.abs(lx - center), dz = Math.abs(lz - center);
            if (dx == 2 && dz == 2)
                bufferedSet(chunk, x, floorY + 1, z, Blocks.AZALEA.defaultBlockState());
            else if ((dx == 3 && dz == 2) || (dx == 2 && dz == 3))
                bufferedSet(chunk, x, floorY + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
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

    private static BlockState patternedWall(long seed, int x, int y, int z, MazeBiome biome,
                                            int cell, int radius) {
        if (isCenterArena(x, z, cell)) return Asterion.ANCIENT_BRICKS.defaultBlockState();
        long detail = mix(seed ^ (long)Math.floorDiv(x, 3) * 0x9E3779B97F4A7C15L
                ^ (long)Math.floorDiv(z, 3) * 0xD1B54A32D192ED03L
                ^ Math.floorDiv(y, 4) * 0x94D049BB133111EBL);
        if (biome != MazeBiome.ANCIENT && isBiomeBlendBand(x, z, cell)
                && Math.floorMod(detail, 4) != 0)
            biome = MazeBiome.ANCIENT;
        return switch (biome) {
            case OVERGROWN -> Math.floorMod(detail, 9) < 3
                    ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
                    : Math.floorMod(detail, 17) == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : Asterion.ANCIENT_BRICKS.defaultBlockState();
            case WITHERED -> Math.floorMod(detail, 13) == 0
                    ? Blocks.BONE_BLOCK.defaultBlockState()
                    : Math.floorMod(detail, 7) == 0 ? Blocks.TUFF_BRICKS.defaultBlockState()
                    : Asterion.ANCIENT_STONE.defaultBlockState();
            case SCORCHED -> Math.floorMod(detail, 8) < 2
                    ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : Math.floorMod(detail, 19) == 0 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : Asterion.ANCIENT_BRICKS.defaultBlockState();
            case COLLAPSED -> Math.floorMod(detail, 11) == 0
                    ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState()
                    : Math.floorMod(detail, 6) == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                    : Asterion.ANCIENT_STONE.defaultBlockState();
            default -> Math.floorMod(detail, 23) == 0
                    ? Asterion.ANCIENT_STONE.defaultBlockState() : Asterion.ANCIENT_BRICKS.defaultBlockState();
        };
    }

    private static Block patternedFloor(long seed, int x, int z, int depth, MazeTopology topology,
                                        int cell, int radius) {
        if (depth > 0) {
            int foundation = x * 31 ^ z * 17 ^ depth * 13 ^ (int) seed;
            return (foundation & 3) == 0 ? Asterion.ANCIENT_STONE : Asterion.ANCIENT_BRICKS;
        }
        MazeBiome biome = mazeBiomeAt(seed, x, z, cell);
        long biomeDetail = mix(seed ^ (long)x * 0x632BE59BD9B4E019L ^ (long)z * 0x8CB92BA72F3D8DD7L);
        if (biome == MazeBiome.OVERGROWN && Math.floorMod(biomeDetail, 9) < 2)
            return Asterion.ANCIENT_MOSS;
        if (biome == MazeBiome.WITHERED && Math.floorMod(biomeDetail, 13) == 0)
            return Blocks.SOUL_SOIL;
        if (biome == MazeBiome.SCORCHED && Math.floorMod(biomeDetail, 11) == 0)
            return Blocks.POLISHED_BLACKSTONE_BRICKS;
        if (biome == MazeBiome.COLLAPSED && Math.floorMod(biomeDetail, 8) == 0)
            return Blocks.CRACKED_DEEPSLATE_TILES;
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

    private static MazeBiome mazeBiomeAt(long seed, int x, int z, int cell) {
        int regionSize = cell * 12;
        int regionX = Math.floorDiv(x, regionSize);
        int regionZ = Math.floorDiv(z, regionSize);
        long region = mix(seed ^ (long)regionX * 0x9E3779B97F4A7C15L
                ^ (long)regionZ * 0xD1B54A32D192ED03L);
        if (Math.max(Math.abs(x), Math.abs(z)) < cell * 7) return MazeBiome.ANCIENT;
        int localX = Math.floorMod(x, regionSize);
        int localZ = Math.floorMod(z, regionSize);
        int transition = cell + cell / 2;
        if (localX < transition || localZ < transition
                || localX >= regionSize - transition || localZ >= regionSize - transition)
            return MazeBiome.ANCIENT;
        int roll = (int)Math.floorMod(region, 100L);
        if (roll < 60) return MazeBiome.ANCIENT;
        if (roll < 70) return MazeBiome.OVERGROWN;
        if (roll < 80) return MazeBiome.WITHERED;
        if (roll < 90) return MazeBiome.SCORCHED;
        return MazeBiome.COLLAPSED;
    }

    private static boolean isBiomeBlendBand(int x, int z, int cell) {
        int regionSize = cell * 12;
        int localX = Math.floorMod(x, regionSize);
        int localZ = Math.floorMod(z, regionSize);
        int edge = Math.min(Math.min(localX, regionSize - 1 - localX),
                Math.min(localZ, regionSize - 1 - localZ));
        int neutralBand = cell + cell / 2;
        return edge >= neutralBand && edge < neutralBand + cell * 2;
    }

    private static boolean placeMazeMotifColumn(ChunkAccess chunk, long seed, int x, int z,
                                                int cell, int thickness, int wallHeight,
                                                MazeBiome biome, int radius, int floorY) {
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
        int chance = biome == MazeBiome.ANCIENT ? 13 : 3;
        if (Math.floorMod(cellRoll, chance) != 0) return false;

        int motif = biome == MazeBiome.ANCIENT ? (int)Math.floorMod(cellRoll >>> 9, 4L)
                : switch (biome) {
                    case OVERGROWN -> 0;
                    case WITHERED -> 1;
                    case SCORCHED -> 2;
                    case COLLAPSED -> 3;
                    default -> 0;
                };
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
                                             int cell, int thickness, int wallHeight,
                                             MazeBiome biome, int floorY) {
        long detail = mix(seed ^ ((long)x << 32) ^ (z & 0xFFFFFFFFL) ^ 0xE7037ED1A0B428DBL);
        int limit = AsterionConfig.INSTANCE.mazeRadiusCells * cell;
        int lx = Math.floorMod(x + limit, cell);
        int lz = Math.floorMod(z + limit, cell);
        if ((biome == MazeBiome.COLLAPSED || biome == MazeBiome.WITHERED)
                && Math.floorMod(detail, 41) == 0) {
            boolean wallAlongZ = lx < thickness;
            BlockState beam = Blocks.POLISHED_BASALT.defaultBlockState().setValue(
                    RotatedPillarBlock.AXIS, wallAlongZ ? net.minecraft.core.Direction.Axis.Z
                            : net.minecraft.core.Direction.Axis.X);
            int beamY = floorY + 3 + (int)Math.floorMod(detail >>> 12, Math.max(2, wallHeight - 5));
            bufferedSet(chunk, x, beamY, z, beam);
        }
        if (biome == MazeBiome.OVERGROWN && Math.floorMod(detail, 29) == 0) {
            int crownY = floorY + wallHeight;
            bufferedSet(chunk, x, crownY, z, Blocks.AZALEA_LEAVES.defaultBlockState());
            if ((detail & 1L) == 0L && crownY > floorY + 4)
                bufferedSet(chunk, x, crownY - 1, z, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
        }
    }

    private static void placeBiomeFloorDetail(ChunkAccess chunk, long seed, int x, int z,
                                              int lx, int lz, int center, int thickness,
                                              int wallHeight, MazeBiome biome, int floorY) {
        long detail = mix(seed ^ (long)x * 0xDB4F0B9175AE2165L ^ (long)z * 0xBBE0563303A4615FL);
        boolean corridorInterior = lx >= thickness + 1 && lz >= thickness + 1;
        if (!corridorInterior) return;
        if (biome == MazeBiome.OVERGROWN) {
            int cell = AsterionConfig.INSTANCE.cellSize;
            int limit = AsterionConfig.INSTANCE.mazeRadiusCells * cell;
            int gx = Math.floorDiv(x + limit, cell);
            int gz = Math.floorDiv(z + limit, cell);
            long canopy = mix(seed ^ (long)gx * 0xC13FA9A902A6328FL
                    ^ (long)gz * 0x91E10DA5C79E7B1DL);
            boolean canopyCell = Math.floorMod(canopy, 3) == 0;
            boolean alongX = (canopy & 4L) == 0L;
            boolean leafBridge = canopyCell && (alongX ? Math.abs(lz - center) <= 1
                    : Math.abs(lx - center) <= 1);
            if (leafBridge) {
                int roofY = floorY + wallHeight - 2;
                BlockState leaves = Math.floorMod(detail, 7) == 0
                        ? Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState()
                        : Blocks.AZALEA_LEAVES.defaultBlockState();
                bufferedSet(chunk, x, roofY, z, leaves);
                if (Math.floorMod(detail, 17) == 0) {
                    int length = 3 + (int)Math.floorMod(detail >>> 14, 7L);
                    for (int drop = 1; drop < length; drop++)
                        bufferedSet(chunk, x, roofY - drop, z, Blocks.CAVE_VINES_PLANT.defaultBlockState());
                    bufferedSet(chunk, x, roofY - length, z, Blocks.CAVE_VINES.defaultBlockState());
                }
            }
            if (Math.floorMod(detail, 19) == 0)
                bufferedSet(chunk, x, floorY + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
            else if (Math.floorMod(detail, 43) == 0)
                bufferedSet(chunk, x, floorY + 1, z, Blocks.LEAF_LITTER.defaultBlockState());
            else if (Math.floorMod(detail, 71) == 0)
                bufferedSet(chunk, x, floorY + 1, z, Blocks.WILDFLOWERS.defaultBlockState());
            else if (Math.floorMod(detail, 137) == 0
                    && Math.abs(lx - center) > 2 && Math.abs(lz - center) > 2)
                bufferedSet(chunk, x, floorY + 1, z, Blocks.BUSH.defaultBlockState());
            else if (Math.floorMod(detail, 113) == 0 && Math.abs(lx - center) > 1 && Math.abs(lz - center) > 1)
                bufferedSet(chunk, x, floorY + 1, z, Blocks.AZALEA.defaultBlockState());
        } else if (biome == MazeBiome.WITHERED && Math.floorMod(detail, 127) == 0) {
            bufferedSet(chunk, x, floorY + 1, z, Blocks.BONE_BLOCK.defaultBlockState());
        } else if (biome == MazeBiome.SCORCHED && Math.floorMod(detail, 97) == 0) {
            bufferedSet(chunk, x, floorY, z, Blocks.SOUL_SOIL.defaultBlockState());
            bufferedSet(chunk, x, floorY + 1, z, Blocks.SOUL_FIRE.defaultBlockState());
        } else if (biome == MazeBiome.COLLAPSED && Math.floorMod(detail, 53) == 0
                && Math.abs(lx - center) > 2 && Math.abs(lz - center) > 2) {
            bufferedSet(chunk, x, floorY + 1, z, ((detail >>> 8) & 1L) == 0L
                    ? Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState()
                    : Blocks.TUFF_SLAB.defaultBlockState());
        }
    }

    private enum MazeBiome {
        ANCIENT, OVERGROWN, WITHERED, SCORCHED, COLLAPSED
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

    private record BuildBlock(BlockPos pos, BlockState state) { }
    private static final class BossPillar {
        private final int x, z;
        private boolean broken;
        private BossPillar(int x, int z) { this.x = x; this.z = z; }
    }
    private static final class BossArenaBuild {
        private final int pillarCount;
        private final List<BossPillar> pillars = new ArrayList<>();
        private final ArrayDeque<BuildBlock> growth = new ArrayDeque<>();
        private boolean ready;
        private BossArenaBuild(int pillarCount) { this.pillarCount = pillarCount; }
    }
    private static final class BossFinale {
        private final UUID bossId;
        private final Map<UUID, Boolean> previousInvulnerability = new HashMap<>();
        private int ticks;
        private BossFinale(UUID bossId) { this.bossId = bossId; }
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
