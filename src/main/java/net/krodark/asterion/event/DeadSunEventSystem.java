package net.krodark.asterion.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import com.mojang.brigadier.Command;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.network.DeadSunEventPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.DazePayload;
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayDeque;
import java.util.HashMap;

public final class DeadSunEventSystem {
    public static final Identifier RUMBLE = Asterion.id("rumble");
    public static final Identifier FLOOD = Asterion.id("flood");
    public static final Identifier ECLIPSE = Asterion.id("eclipse");
    public static final Identifier SHIFTING = Asterion.id("shifting");
    public static final Identifier DEAD_SUN_BARRAGE = Asterion.id("dead_sun_barrage");
    public static final Identifier POISON_GEYSERS = Asterion.id("poison_geysers");
    public static final Identifier CRIMSON_FIREFLIES = Asterion.id("crimson_fireflies");
    private static final int MIN_INTERVAL = 20 * 35;
    private static final int MAX_INTERVAL = 20 * 95;
    private static final Map<Identifier, Definition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, SchedulerState> STATES = new WeakHashMap<>();
    private static final ArrayDeque<WallLayer> SHIFT_ANIMATION = new ArrayDeque<>();
    private static final List<PendingStrike> PENDING_STRIKES = new ArrayList<>();
    private static final List<PoisonGeyser> POISON_GEYSER_HAZARDS = new ArrayList<>();
    private static final List<CrimsonFirefly> CRIMSON_FIREFLY_SWARM = new ArrayList<>();

    static {
        register(new Definition() {
            @Override public Identifier id() { return RUMBLE; }
            @Override public int weight() { return 10; }
            @Override public int minDurationTicks() { return 70; }
            @Override public int maxDurationTicks() { return 135; }
            @Override public float intensity(RandomSource random) { return 0.62F + random.nextFloat() * 0.38F; }
            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                if (elapsedTicks > 8 && elapsedTicks % 7 == 0) tickRumbleDebris(level);
            }
        });
        register(new Definition() {
            @Override public Identifier id() { return SHIFTING; }
            @Override public int weight() { return 7; }
            @Override public int minDurationTicks() { return 20 * 38; }
            @Override public int maxDurationTicks() { return 20 * 52; }
            @Override public float intensity(RandomSource random) { return 0.78F + random.nextFloat() * 0.22F; }
            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                if (isEclipseActive(level)) { finishWallShift(level); return; }
                if (elapsedTicks == 20) beginWallShift(level);
                tickWallShift(level);
            }
            @Override public void onEnd(ServerLevel level) {
                finishWallShift(level);
            }
        });
        register(new Definition() {
            @Override public Identifier id() { return DEAD_SUN_BARRAGE; }
            @Override public int weight() { return 1; }
            @Override public int minDurationTicks() { return 20 * 18; }
            @Override public int maxDurationTicks() { return 20 * 26; }
            @Override public float intensity(RandomSource random) { return 0.8F + random.nextFloat() * 0.2F; }
            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                tickPendingStrikes(level);
                if (elapsedTicks >= 30 && elapsedTicks % 72 == 0) scheduleStrikes(level);
            }
            @Override public void onEnd(ServerLevel level) {
                PENDING_STRIKES.clear();
            }
        });
        register(new Definition() {
            @Override public Identifier id() { return POISON_GEYSERS; }
            @Override public int weight() { return 5; }
            @Override public int minDurationTicks() { return 20 * 18; }
            @Override public int maxDurationTicks() { return 20 * 30; }
            @Override public float intensity(RandomSource random) { return 0.72F + random.nextFloat() * 0.24F; }
            @Override public boolean eligible(ServerLevel level) { return hasCrimsonPlayer(level); }
            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                if (elapsedTicks >= 18 && elapsedTicks % 58 == 0) schedulePoisonGeysers(level);
                tickPoisonGeysers(level);
            }
            @Override public void onEnd(ServerLevel level) { POISON_GEYSER_HAZARDS.clear(); }
        });
        register(new Definition() {
            @Override public Identifier id() { return CRIMSON_FIREFLIES; }
            @Override public int weight() { return 4; }
            @Override public int minDurationTicks() { return 20 * 16; }
            @Override public int maxDurationTicks() { return 20 * 24; }
            @Override public float intensity(RandomSource random) { return 0.68F + random.nextFloat() * 0.27F; }
            @Override public boolean eligible(ServerLevel level) { return hasCrimsonPlayer(level); }
            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                if (elapsedTicks > 22 && elapsedTicks % 10 == 0) spawnCrimsonFireflies(level);
                tickCrimsonFireflies(level);
            }
            @Override public void onEnd(ServerLevel level) { CRIMSON_FIREFLY_SWARM.clear(); }
        });
        register(new Definition() {
            @Override public Identifier id() { return FLOOD; }
            @Override public int weight() { return 1; }
            @Override public int minDurationTicks() { return 20 * 120; }
            @Override public int maxDurationTicks() { return 20 * 180; }
            @Override public float intensity(RandomSource random) { return .35F; }
            @Override public void onStart(ServerLevel level, long seed, int duration, float intensity) {
                CatacombFloodState.start(level, duration);
            }
            @Override public void onTick(ServerLevel level, int elapsed) {
                if (elapsed % 20 != 0 || CatacombFloodState.get(level).riseSteps() >= CatacombFloodState.MAX_RISE) return;
                ActiveEvent event = STATES.get(level.getServer()).active;
                // Large/multiplayer loaded areas still get a complete rise and forty seconds at high tide.
                if (event.durationTicks < elapsed + 800) {
                    event.durationTicks = elapsed + 800;
                    CatacombFloodState.ensureRemainingTicks(level, 800);
                }
            }
            @Override public void onEnd(ServerLevel level) { CatacombFloodState.setActive(level, false); }
        });
        register(new Definition() {
            @Override public Identifier id() { return ECLIPSE; }
            @Override public int weight() { return 1; }
            @Override public int minDurationTicks() { return 20 * 60; }
            @Override public int maxDurationTicks() { return 20 * 60 * 10; }
            @Override public float intensity(RandomSource random) { return 0.82F + random.nextFloat() * 0.18F; }

            @Override public void onStart(ServerLevel level, long seed, int durationTicks, float intensity) {
            }

            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                if ((elapsedTicks % 20) != 0) return;
                java.util.List<MinotaurEntity> hunters = new java.util.ArrayList<>(eclipseMinotaurs(level));
                for (var player : level.players()) {
                    if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
                    if (!hunterRevealReady(level, player.getUUID(), elapsedTicks)) continue;
                    MinotaurEntity assignedHunter = hunters.stream()
                            .filter(minotaur -> minotaur.isAssignedTo(player)).findFirst().orElse(null);
                    if (assignedHunter != null && assignedHunter.isRoaming())
                        assignedHunter.beginHunting(player);
                    boolean assigned = assignedHunter != null;
                    if (!assigned && claimHunter(level, player.getUUID())) {
                        MinotaurEntity hunter = MinotaurEntity.spawnHunter(level, player);
                        if (hunter == null) releaseHunterClaim(level, player.getUUID());
                        else hunters.add(hunter);
                    }
                }
            }

            @Override public void onEnd(ServerLevel level) {
                eclipseMinotaurs(level).forEach(MinotaurEntity::endEclipse);
            }
        });
    }

    private DeadSunEventSystem() {
    }

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            var root = Commands.literal("asterionevent")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(command -> showStatus(command.getSource()));
            var start = Commands.literal("start");
            for (Definition definition : DEFINITIONS.values()) {
                String name = definition.id().getPath();
                start.then(Commands.literal(name).executes(command -> {
                    ServerLevel level = command.getSource().getServer().getLevel(Asterion.ASTERION_LEVEL);
                    if (level == null) {
                        command.getSource().sendFailure(Component.literal("The Asterion dimension is not loaded."));
                        return 0;
                    }
                    if (level.players().isEmpty()) {
                        command.getSource().sendFailure(Component.literal(
                                "No players are inside the Asterion. Enter the dimension before testing this event."));
                        return 0;
                    }
                    if (!trigger(level, definition.id())) {
                        command.getSource().sendFailure(Component.literal(
                                "That event cannot start here right now; biome events require an eligible player."));
                        return 0;
                    }
                    SchedulerState state = STATES.get(level.getServer());
                    ActiveEvent active = state == null ? null : state.active;
                    command.getSource().sendSuccess(() -> Component.literal(
                            "Started " + definition.id() + " for " + active.durationTicks
                                    + " ticks (seed=" + active.seed + ", intensity=" + active.intensity + ")."), true);
                    return Command.SINGLE_SUCCESS;
                }));
            }
            root.then(start);
            root.then(Commands.literal("list").executes(command -> {
                String names = String.join(", ", DEFINITIONS.keySet().stream().map(Identifier::getPath).toList());
                command.getSource().sendSuccess(() -> Component.literal("Available Asterion events: " + names), false);
                return Command.SINGLE_SUCCESS;
            }));
            root.then(Commands.literal("status").executes(command -> showStatus(command.getSource())));
            root.then(Commands.literal("stop").executes(command -> {
                ServerLevel level = command.getSource().getServer().getLevel(Asterion.ASTERION_LEVEL);
                if (level == null || !stop(level)) {
                    command.getSource().sendFailure(Component.literal("No Asterion event is active."));
                    return 0;
                }
                command.getSource().sendSuccess(() -> Component.literal("Stopped the active Asterion event."), true);
                return Command.SINGLE_SUCCESS;
            }));
            dispatcher.register(root);
        });
    }

    private static int showStatus(net.minecraft.commands.CommandSourceStack source) {
        SchedulerState state = STATES.get(source.getServer());
        if (state == null || state.active == null) {
            source.sendSuccess(() -> Component.literal("No Asterion event is active."), false);
            return Command.SINGLE_SUCCESS;
        }
        ActiveEvent event = state.active;
        source.sendSuccess(() -> Component.literal("Active: " + event.definition.id()
                + " | elapsed=" + event.elapsed + "/" + event.durationTicks
                + " | remaining=" + Math.max(0, event.durationTicks - event.elapsed)
                + " | seed=" + event.seed + " | intensity=" + event.intensity), false);
        return Command.SINGLE_SUCCESS;
    }

    public static boolean stop(ServerLevel level) {
        SchedulerState state = STATES.get(level.getServer());
        if (state == null || state.active == null) return false;
        ActiveEvent event = state.active;
        event.definition.onEnd(level);
        syncStopped(level, event.definition.id(), event.seed);
        state.active = null;
        state.nextEventTick = scheduleNext(level.getRandom(), level.getGameTime());
        return true;
    }

    public static boolean isEclipseActive(ServerLevel level) { return isActive(level, ECLIPSE); }

    public static boolean isActive(ServerLevel level, Identifier event) {
        SchedulerState state = STATES.get(level.getServer());
        return state != null && state.active != null && state.active.definition.id().equals(event);
    }

    public static void clearRuntimeState(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.ASTERION_LEVEL);
        if (level != null) { stop(level); finishWallShift(level); }
        PENDING_STRIKES.clear();
        POISON_GEYSER_HAZARDS.clear();
        CRIMSON_FIREFLY_SWARM.clear();
        STATES.remove(server);
    }

    public static void finishEclipse(ServerLevel level) {
        if (isEclipseActive(level)) stop(level);
    }

    private static java.util.List<MinotaurEntity> eclipseMinotaurs(ServerLevel level) {
        return level.getEntitiesOfClass(MinotaurEntity.class,
                new net.minecraft.world.phys.AABB(-4096, level.getMinY(), -4096,
                        4096, level.getMaxY(), 4096));
    }

    private static boolean claimHunter(ServerLevel level, UUID playerId) {
        SchedulerState state = STATES.get(level.getServer());
        return state != null && state.active != null && state.active.hunterPlayers.add(playerId);
    }

    private static void releaseHunterClaim(ServerLevel level, UUID playerId) {
        SchedulerState state = STATES.get(level.getServer());
        if (state != null && state.active != null) state.active.hunterPlayers.remove(playerId);
    }

    private static boolean hunterRevealReady(ServerLevel level, UUID playerId, int elapsedTicks) {
        SchedulerState state = STATES.get(level.getServer());
        if (state == null || state.active == null) return false;
        int reveal = state.active.hunterRevealTicks.computeIfAbsent(playerId, id -> {
            long mixed = state.active.seed ^ id.getMostSignificantBits()
                    ^ Long.rotateLeft(id.getLeastSignificantBits(), 29);
            mixed ^= mixed >>> 30;
            mixed *= 0xbf58476d1ce4e5b9L;
            mixed ^= mixed >>> 27;
            // The eclipse should become dangerous promptly; the longer delay made a substantial
            // portion of short encounters look as though the hunter never activated.
            return 20 * (3 + (int)Math.floorMod(mixed, 5L));
        });
        return elapsedTicks >= reveal;
    }

    public static void register(Definition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null)
            throw new IllegalArgumentException("Duplicate Dead Sun event: " + definition.id());
    }

    public static boolean trigger(ServerLevel level, Identifier eventId) {
        Definition definition = DEFINITIONS.get(eventId);
        if (definition == null || WorldGenerator.isBossEncounterActive(level)
                || !definition.eligible(level)) return false;
        SchedulerState state = STATES.computeIfAbsent(level.getServer(), ignored -> new SchedulerState());
        if (definition.id().equals(SHIFTING) && isEclipseActive(level)) return false;
        if (state.active != null) state.active.definition.onEnd(level);
        start(level, state, definition);
        return true;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.ASTERION_LEVEL);
        if (level == null) return;
        CatacombFloodState.tick(level);
        RareMazeEvents.get(level);
        SchedulerState state = STATES.computeIfAbsent(server, ignored -> new SchedulerState());
        long now = level.getGameTime();

        if (WorldGenerator.isBossEncounterActive(level)) {
            if (state.active != null) stop(level);
            finishWallShift(level);
            PENDING_STRIKES.clear();
            state.nextEventTick = Math.max(state.nextEventTick, now + MIN_INTERVAL);
            return;
        }

        if (state.active != null) {
            if (state.active.definition.id().equals(ECLIPSE)
                    && (state.active.elapsed % 20) == 0
                    && eclipseMinotaurs(level).stream().anyMatch(MinotaurEntity::isChasing)
                    && state.active.durationTicks - state.active.elapsed < 100) {
                state.active.durationTicks = state.active.elapsed + 200;
                state.active.notifiedPlayers.clear();
            }
            syncNewPlayers(level, state.active);
            if ((state.active.elapsed % 100) == 0) syncAllPlayers(level, state.active);
            state.active.definition.onTick(level, state.active.elapsed++);
            if (state.active.elapsed >= state.active.durationTicks) {
                ActiveEvent finished = state.active;
                finished.definition.onEnd(level);
                syncStopped(level, finished.definition.id(), finished.seed);
                state.active = null;
                state.nextEventTick = scheduleNext(level.getRandom(), now);
            }
            return;
        }

        if ((now % 20L) == 0L) syncStopped(level, ECLIPSE, 0L);

        if (level.players().isEmpty()) {
            state.nextEventTick = -1L;
            return;
        }
        if (state.nextEventTick < 0L) state.nextEventTick = scheduleNext(level.getRandom(), now);
        if (now < state.nextEventTick) return;
        startRandom(level, state);
    }

    private static void startRandom(ServerLevel level, SchedulerState state) {
        RandomSource random = level.getRandom();
        var eligible = DEFINITIONS.values().stream()
                .filter(definition -> definition.eligible(level))
                .filter(definition -> RareMazeEvents.get(level).ready(definition.id(), level.getGameTime()))
                .filter(definition -> !definition.id().equals(FLOOD) || level.players().stream().anyMatch(player ->
                        player.isAlive() && !player.isSpectator() && player.getY() >= 3
                                && player.getY() <= net.krodark.asterion.worldgen.CatacombLayout.ROOF_Y))
                .filter(definition -> !definition.id().equals(SHIFTING) || !isEclipseActive(level)).toList();
        if (eligible.isEmpty()) {
            state.nextEventTick = scheduleNext(random, level.getGameTime());
            return;
        }
        int totalWeight = eligible.stream().mapToInt(definition -> Math.max(1, definition.weight())).sum();
        int roll = random.nextInt(totalWeight);
        Definition selected = eligible.getFirst();
        for (Definition definition : eligible) {
            roll -= Math.max(1, definition.weight());
            if (roll < 0) {
                selected = definition;
                break;
            }
        }
        start(level, state, selected);
    }

    private static void start(ServerLevel level, SchedulerState state, Definition selected) {
        RandomSource random = level.getRandom();
        int duration = random.nextIntBetweenInclusive(selected.minDurationTicks(), selected.maxDurationTicks());
        long seed = random.nextLong();
        float intensity = selected.intensity(random);
        state.active = new ActiveEvent(selected, duration, seed, intensity);
        RareMazeEvents.get(level).schedule(level, selected.id(), duration);
        selected.onStart(level, seed, duration, intensity);
        Asterion.LOGGER.info("Dead Sun event {} began for {} ticks", selected.id(), duration);
        syncNewPlayers(level, state.active);
    }

    private static void syncNewPlayers(ServerLevel level, ActiveEvent event) {
        DeadSunEventPayload payload = new DeadSunEventPayload(event.definition.id(), event.seed,
                event.durationTicks, event.elapsed, event.intensity);
        level.players().forEach(player -> {
            if (!event.notifiedPlayers.add(player.getUUID())) return;
            if (ServerPlayNetworking.canSend(player, DeadSunEventPayload.TYPE))
                ServerPlayNetworking.send(player, payload);
        });
    }

    private static void syncAllPlayers(ServerLevel level, ActiveEvent event) {
        DeadSunEventPayload payload = new DeadSunEventPayload(event.definition.id(), event.seed,
                event.durationTicks, event.elapsed, event.intensity);
        level.players().forEach(player -> {
            event.notifiedPlayers.add(player.getUUID());
            if (ServerPlayNetworking.canSend(player, DeadSunEventPayload.TYPE))
                ServerPlayNetworking.send(player, payload);
        });
    }

    private static void syncStopped(ServerLevel level, Identifier eventId, long seed) {
        DeadSunEventPayload payload = new DeadSunEventPayload(eventId, seed, 1, 1, 0.0F);
        level.players().forEach(player -> {
            if (ServerPlayNetworking.canSend(player, DeadSunEventPayload.TYPE))
                ServerPlayNetworking.send(player, payload);
        });
    }

    private static void beginWallShift(ServerLevel level) {
        SHIFT_ANIMATION.clear();
        var candidates = level.players().stream()
                .filter(player -> player.isAlive() && !player.isSpectator()).toList();
        if (candidates.isEmpty()) return;
        Set<BlockPos> usedPassages = new HashSet<>();
        for (int shift = 0; shift < 12; shift++) {
            var player = candidates.get(shift % candidates.size());
            BlockPos origin = player.blockPosition().offset(
                    level.getRandom().nextIntBetweenInclusive(-72, 72), 0,
                    level.getRandom().nextIntBetweenInclusive(-72, 72));
            BlockPos opened = findWallNear(level, origin);
            BlockPos closed = findOpenCorridorNear(level, origin, opened);
            if (opened == null || closed == null || !usedPassages.add(closed)) continue;
            queueWallTransfer(level, opened, closed);
            broadcastShiftRumble(level, opened, shift == 0 ? 1.0F : 0.62F);
        }
    }

    private static void tickWallShift(ServerLevel level) {
        WallLayer layer = SHIFT_ANIMATION.pollFirst();
        if (layer == null) return;
        applyWallLayer(level, layer);
        if ((SHIFT_ANIMATION.size() % 3) == 0) {
            BlockPos soundAt = layer.soundAt;
            level.playSound(null, soundAt, SoundEvents.DEEPSLATE_BREAK,
                    SoundSource.BLOCKS, 1.45F, 0.58F);
            if ((SHIFT_ANIMATION.size() % 6) == 0)
                broadcastShiftRumble(level, soundAt, 0.72F);
        }
    }

    private static void applyWallLayer(ServerLevel level, WallLayer layer) {
        layer.remove.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
        layer.place.forEach(placement -> level.setBlock(placement.pos, placement.state, 3));
    }

    private static void finishWallShift(ServerLevel level) {
        WallLayer layer;
        while ((layer = SHIFT_ANIMATION.pollFirst()) != null) applyWallLayer(level, layer);
    }

    private static void broadcastShiftRumble(ServerLevel level, BlockPos center, float intensity) {
        level.playSound(null, center, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 2.2F, 0.48F);
        MazeShiftPayload payload = new MazeShiftPayload(center, 24.0F, intensity, 30);
        level.players().forEach(viewer -> {
            if (viewer.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D,
                    center.getZ() + 0.5D) <= 32.0D * 32.0D
                    && ServerPlayNetworking.canSend(viewer, MazeShiftPayload.TYPE))
                ServerPlayNetworking.send(viewer, payload);
        });
    }

    private static void tickRumbleDebris(ServerLevel level) {
        // Geometry and dust are bounded client-side rigid bodies; never destroy or place maze blocks.
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            var source = RumbleSources.find(level, player.position(), new java.util.Random(level.getRandom().nextLong()));
            if (source != null && level.getRandom().nextFloat() < .45F)
                level.playSound(null, source.block(), SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS,
                        .55F, .7F + level.getRandom().nextFloat() * .15F);
        }
    }

    private static BlockPos findWallNear(ServerLevel level, BlockPos origin) {
        RandomSource random = level.getRandom();
        int y = 49;
        for (int attempt = 0; attempt < 80; attempt++) {
            int dx = random.nextIntBetweenInclusive(-16, 16);
            int dz = random.nextIntBetweenInclusive(-16, 16);
            if (dx * dx + dz * dz < 36) continue;
            BlockPos candidate = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
            if (!isMazeWall(level.getBlockState(candidate))) continue;
            boolean lineX = isMazeWall(level.getBlockState(candidate.east()))
                    && isMazeWall(level.getBlockState(candidate.west()));
            boolean lineZ = isMazeWall(level.getBlockState(candidate.north()))
                    && isMazeWall(level.getBlockState(candidate.south()));
            if (lineX || lineZ) return candidate;
        }
        return null;
    }

    private static BlockPos findOpenCorridorNear(ServerLevel level, BlockPos origin, BlockPos awayFrom) {
        RandomSource random = level.getRandom();
        int y = 49;
        int cell = net.krodark.asterion.AsterionConfig.INSTANCE.cellSize;
        int thickness = net.krodark.asterion.AsterionConfig.INSTANCE.wallThickness;
        int limit = net.krodark.asterion.AsterionConfig.INSTANCE.mazeRadiusCells * cell;
        int centerOffset = thickness + (cell - thickness) / 2;
        for (int attempt = 0; attempt < 100; attempt++) {
            int dx = random.nextIntBetweenInclusive(-15, 15);
            int dz = random.nextIntBetweenInclusive(-15, 15);
            if (dx * dx + dz * dz < 49) continue;
            int rawX = origin.getX() + dx, rawZ = origin.getZ() + dz;
            int gx = Math.floorDiv(rawX + limit, cell), gz = Math.floorDiv(rawZ + limit, cell);
            BlockPos candidate = new BlockPos(-limit + gx * cell + centerOffset, y,
                    -limit + gz * cell + centerOffset);
            if (awayFrom != null && candidate.distSqr(awayFrom) < 36.0D) continue;
            if (net.krodark.asterion.WorldGenerator.isCenterAccessProtected(candidate)) continue;
            if (!level.getBlockState(candidate).isAir() || !level.getBlockState(candidate.above(4)).isAir()) continue;
            int half = (cell - thickness) / 2;
            AABB barrier = new AABB(candidate.getX() - half, y, candidate.getZ() - half,
                    candidate.getX() + half + 1, y + 6, candidate.getZ() + half + 1);
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, barrier).isEmpty()) continue;
            return candidate;
        }
        return null;
    }

    private static void queueWallTransfer(ServerLevel level, BlockPos source, BlockPos destination) {
        int height = net.krodark.asterion.AsterionConfig.INSTANCE.wallHeight;
        int corridorWidth = net.krodark.asterion.AsterionConfig.INSTANCE.cellSize
                - net.krodark.asterion.AsterionConfig.INSTANCE.wallThickness;

        boolean lineX = isMazeWall(level.getBlockState(source.east()))
                && isMazeWall(level.getBlockState(source.west()));
        int probe = corridorWidth / 2 + 1;
        boolean barrierAlongX = isMazeWall(level.getBlockState(destination.east(probe)))
                || isMazeWall(level.getBlockState(destination.west(probe)));
        int half = corridorWidth / 2;

        for (int dy = 0; dy < height; dy++) {
            List<BlockPos> remove = new ArrayList<>();
            List<Placement> place = new ArrayList<>();
            for (int lateral = -1; lateral <= 1; lateral++)
                for (int depth = -net.krodark.asterion.AsterionConfig.INSTANCE.wallThickness + 1;
                     depth < net.krodark.asterion.AsterionConfig.INSTANCE.wallThickness; depth++) {
                    BlockPos pos = lineX ? source.offset(lateral, dy, depth)
                            : source.offset(depth, dy, lateral);
                    if (isMazeWall(level.getBlockState(pos))) remove.add(pos.immutable());
                }
            for (int lateral = -half; lateral <= half; lateral++)
                for (int depth = -1;
                     depth < net.krodark.asterion.AsterionConfig.INSTANCE.wallThickness - 1; depth++) {
                    BlockPos pos = barrierAlongX ? destination.offset(lateral, dy, depth)
                            : destination.offset(depth, dy, lateral);
                    if (level.getBlockState(pos).isAir())
                        place.add(new Placement(pos.immutable(), Asterion.ANCIENT_BRICKS.defaultBlockState()));
                }
            if (!remove.isEmpty() || !place.isEmpty())
                SHIFT_ANIMATION.addLast(new WallLayer(remove, place, destination.above(dy)));
        }
    }

    private static boolean isMazeWall(BlockState state) {
        return state.is(Asterion.ANCIENT_BRICKS) || state.is(Asterion.ANCIENT_STONE);
    }

    private record Placement(BlockPos pos, BlockState state) { }
    private record WallLayer(List<BlockPos> remove, List<Placement> place, BlockPos soundAt) { }

    private static boolean hasCrimsonPlayer(ServerLevel level) {
        return level.players().stream().anyMatch(player -> player.isAlive() && !player.isSpectator()
                && WorldGenerator.isCrimsonMarshlandsAt(player.getX(), player.getZ()));
    }

    private static void schedulePoisonGeysers(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !WorldGenerator.isCrimsonMarshlandsAt(player.getX(), player.getZ())) continue;
            int count = 1 + (random.nextFloat() < 0.42F ? 1 : 0);
            for (int index = 0; index < count; index++) {
                BlockPos vent = findGeyserVent(level, player, random);
                if (vent == null || POISON_GEYSER_HAZARDS.stream()
                        .anyMatch(existing -> existing.vent.distSqr(vent) < 25.0D)) continue;
                POISON_GEYSER_HAZARDS.add(new PoisonGeyser(vent, 34 + random.nextInt(10),
                        52 + random.nextInt(20), 2.15F + random.nextFloat() * 0.55F,
                        10.0F + random.nextFloat() * 10.0F));
            }
        }
    }

    private static BlockPos findGeyserVent(ServerLevel level, ServerPlayer player, RandomSource random) {
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + random.nextDouble() * 12.0D;
            int x = net.minecraft.util.Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = net.minecraft.util.Mth.floor(player.getZ() + Math.sin(angle) * distance);
            if (!WorldGenerator.isCrimsonMarshlandsAt(x, z)) continue;
            for (int dy = 3; dy >= -5; dy--) {
                BlockPos water = new BlockPos(x, player.getBlockY() + dy, z);
                if (!level.getFluidState(water).is(net.minecraft.tags.FluidTags.WATER)
                        || level.getFluidState(water.above()).is(net.minecraft.tags.FluidTags.WATER)) continue;
                BlockPos surface = water.above();
                if (level.getBlockState(surface).isAir()) return surface;
            }
        }
        return null;
    }

    private static void tickPoisonGeysers(ServerLevel level) {
        Iterator<PoisonGeyser> iterator = POISON_GEYSER_HAZARDS.iterator();
        while (iterator.hasNext()) {
            PoisonGeyser geyser = iterator.next();
            Vec3 center = Vec3.atBottomCenterOf(geyser.vent);
            if (!level.getFluidState(geyser.vent.below()).is(net.minecraft.tags.FluidTags.WATER)) {
                iterator.remove();
                continue;
            }
            if (geyser.warningTicks-- > 0) {
                double pulse = 0.25D + (1.0D - geyser.warningTicks / 44.0D) * 0.45D;
                level.sendParticles(new DustParticleOptions(0x83B84A, 0.9F),
                        center.x, center.y + 0.08D, center.z, 5,
                        pulse, 0.025D, pulse, 0.01D);
                level.sendParticles(new DustParticleOptions(0xB2DFC7, 0.54F),
                        center.x, center.y + 0.14D, center.z, 3,
                        pulse * 0.62D, 0.035D, pulse * 0.62D, 0.006D);
                if ((geyser.warningTicks % 11) == 0)
                    level.playSound(null, geyser.vent, SoundEvents.LAVA_EXTINGUISH,
                            SoundSource.BLOCKS, 0.32F, 1.55F);
                continue;
            }
            if (geyser.activeTicks-- <= 0) {
                iterator.remove();
                continue;
            }

            if (!geyser.erupted) {
                geyser.erupted = true;
                level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.25D, center.z,
                        22, geyser.radius * 0.34D, 0.28D, geyser.radius * 0.34D, 0.12D);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                        center.x, center.y + 0.5D, center.z, 28,
                        geyser.radius * 0.42D, 0.9D, geyser.radius * 0.42D, 0.075D);
                level.playSound(null, geyser.vent, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 1.4F, 0.52F);
            }

            int layers = Math.max(5, net.minecraft.util.Mth.ceil(geyser.columnHeight / 2.4F));
            for (int layer = 0; layer <= layers; layer++) {
                double progress = layer / (double)layers;
                double y = center.y + 0.2D + progress * geyser.columnHeight;
                double spread = 0.10D + geyser.radius * (0.17D - progress * 0.09D);
                level.sendParticles(new DustParticleOptions(geyserGradientColor(progress),
                                (float)(0.82D - progress * 0.28D)),
                        center.x, y, center.z, 2, spread, 0.24D, spread, 0.012D);
                if ((layer & 1) == 0) {
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            center.x, y, center.z, 1, spread * 0.62D, 0.16D,
                            spread * 0.62D, 0.065D);
                    level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                            center.x, y, center.z, 2, spread, 0.32D, spread, 0.018D);
                }
            }
            if (geyser.activeTicks == 68 || geyser.activeTicks % 18 == 0)
                level.playSound(null, geyser.vent, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.BLOCKS, 0.9F, 0.62F + level.getRandom().nextFloat() * 0.12F);

            long now = level.getGameTime();
            AABB cloud = new AABB(center.x - geyser.radius, center.y - 0.2D,
                    center.z - geyser.radius, center.x + geyser.radius,
                    center.y + geyser.columnHeight, center.z + geyser.radius);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, cloud)) {
                if (!player.isAlive() || player.isCreative() || player.isSpectator()
                        || !WorldGenerator.isCrimsonMarshlandsAt(player.getX(), player.getZ())) continue;
                double horizontal = new Vec3(player.getX() - center.x, 0.0D,
                        player.getZ() - center.z).length();
                if (horizontal > geyser.radius) continue;
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 55, 0, false, true));
                long lastDamage = geyser.lastDamage.getOrDefault(player.getUUID(), Long.MIN_VALUE);
                if (now - lastDamage >= 20L) {
                    player.hurtServer(level, player.damageSources().magic(), 1.5F);
                    geyser.lastDamage.put(player.getUUID(), now);
                }
                if (horizontal <= geyser.radius * 0.58D
                        && geyser.knockedDown.add(player.getUUID()))
                    knockDownFromGeyser(level, player, center);
            }
        }
    }

    private static void knockDownFromGeyser(ServerLevel level, ServerPlayer player, Vec3 center) {
        Vec3 away = player.position().subtract(center);
        away = away.horizontalDistanceSqr() < 1.0E-5D ? new Vec3(0.35D, 0.0D, 0.1D)
                : new Vec3(away.x, 0.0D, away.z).normalize();
        Vec3 impulse = away.scale(0.72D).add(0.0D, 0.64D, 0.0D);
        player.setDeltaMovement(impulse);
        player.hurtMarked = true;
        player.resetFallDistance();
        RagdollServerNetworking.markRagdolled(player, 58);
        if (ServerPlayNetworking.canSend(player, RagdollImpulsePayload.TYPE))
            ServerPlayNetworking.send(player, new RagdollImpulsePayload(center, impulse, 1.2F));
        if (ServerPlayNetworking.canSend(player, DazePayload.TYPE))
            ServerPlayNetworking.send(player, new DazePayload(34, 2));
    }

    private static int geyserGradientColor(double progress) {
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        int red = (int)Math.round(0x43 + (0xB8 - 0x43) * t);
        int green = (int)Math.round(0x78 + (0xE5 - 0x78) * t);
        int blue = (int)Math.round(0x58 + (0xCF - 0x58) * t);
        return red << 16 | green << 8 | blue;
    }

    private static void spawnCrimsonFireflies(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || !WorldGenerator.isCrimsonMarshlandsAt(player.getX(), player.getZ())) continue;
            long owned = CRIMSON_FIREFLY_SWARM.stream().filter(firefly ->
                    firefly.target.equals(player.getUUID())).count();
            if (owned >= 14) continue;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 5.5D + random.nextDouble() * 4.5D;
            Vec3 position = player.position().add(Math.cos(angle) * distance,
                    0.6D + random.nextDouble() * 2.8D, Math.sin(angle) * distance);
            CRIMSON_FIREFLY_SWARM.add(new CrimsonFirefly(player.getUUID(), position,
                    145 + random.nextInt(80), random.nextFloat() * 6.28F));
        }
    }

    private static void tickCrimsonFireflies(ServerLevel level) {
        Iterator<CrimsonFirefly> iterator = CRIMSON_FIREFLY_SWARM.iterator();
        while (iterator.hasNext()) {
            CrimsonFirefly firefly = iterator.next();
            var entity = level.getEntity(firefly.target);
            if (!(entity instanceof ServerPlayer player) || !player.isAlive() || player.isSpectator()
                    || !WorldGenerator.isCrimsonMarshlandsAt(player.getX(), player.getZ())
                    || firefly.life-- <= 0) {
                iterator.remove();
                continue;
            }
            Vec3 target = player.getEyePosition().add(0.0D,
                    Math.sin((level.getGameTime() + firefly.phase) * 0.17D) * 0.28D - 0.25D, 0.0D);
            Vec3 delta = target.subtract(firefly.position);
            double distance = delta.length();
            if (distance < 0.72D) {
                player.igniteForTicks(16);
                player.hurtServer(level, player.damageSources().inFire(), 1.0F);
                level.sendParticles(ParticleTypes.FLAME, firefly.position.x, firefly.position.y,
                        firefly.position.z, 8, 0.18D, 0.18D, 0.18D, 0.035D);
                level.sendParticles(ParticleTypes.SMOKE, firefly.position.x, firefly.position.y,
                        firefly.position.z, 5, 0.12D, 0.12D, 0.12D, 0.025D);
                level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE,
                        SoundSource.PLAYERS, 0.32F, 1.65F);
                iterator.remove();
                continue;
            }
            double speed = 0.085D + Math.min(0.11D, firefly.age++ * 0.0011D);
            Vec3 velocity = delta.normalize().scale(speed);
            Vec3 next = firefly.position.add(velocity);
            BlockPos nextBlock = BlockPos.containing(next);
            if (level.getBlockState(nextBlock).isCollisionShapeFullBlock(level, nextBlock)) {
                level.sendParticles(ParticleTypes.SMOKE, firefly.position.x, firefly.position.y,
                        firefly.position.z, 2, 0.05D, 0.05D, 0.05D, 0.01D);
                iterator.remove();
                continue;
            }
            firefly.position = next;
            level.sendParticles(Asterion.HOSTILE_FIREFLY, next.x, next.y, next.z,
                    0, velocity.x, velocity.y, velocity.z, 1.0D);
            if ((firefly.age & 3) == 0)
                level.sendParticles(new DustParticleOptions(0xFF170D, 0.62F),
                        next.x, next.y, next.z, 1, 0.025D, 0.025D, 0.025D, 0.0D);
        }
    }

    private static void scheduleStrikes(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (var player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
            int count = random.nextFloat() < 0.18F ? 2 : 1;
            for (int strikeIndex = 0; strikeIndex < count; strikeIndex++) {
                int warning = 44 + random.nextInt(12);
                Vec3 movement = player.getDeltaMovement();
                double prediction = Math.min(10.0D, warning * 0.28D);
                double offsetX = movement.x * prediction;
                double offsetZ = movement.z * prediction;
                if (strikeIndex > 0) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    offsetX += Math.cos(angle) * (4.5D + random.nextDouble() * 3.0D);
                    offsetZ += Math.sin(angle) * (4.5D + random.nextDouble() * 3.0D);
                } else {
                    offsetX += random.nextDouble() * 1.4D - 0.7D;
                    offsetZ += random.nextDouble() * 1.4D - 0.7D;
                }
                int x = net.minecraft.util.Mth.floor(player.getX() + offsetX);
                int z = net.minecraft.util.Mth.floor(player.getZ() + offsetZ);
                BlockPos target = net.krodark.asterion.WorldGenerator.findDeadSunStrikeTarget(
                        level, x, z, net.minecraft.util.Mth.floor(player.getY()));
                float radius = 3.0F + random.nextFloat() * 1.15F;
                if (PENDING_STRIKES.stream().anyMatch(existing ->
                        existing.target.distSqr(target) < Math.pow(existing.radius + radius + 2.0F, 2.0D))) continue;
                long seed = random.nextLong();
                PENDING_STRIKES.add(new PendingStrike(target, warning, radius, seed));
                DeadSunStrikePayload payload = new DeadSunStrikePayload(target, warning, radius, seed);
                level.players().forEach(viewer -> {
                    if (viewer.distanceToSqr(target.getCenter()) <= 72.0D * 72.0D
                            && ServerPlayNetworking.canSend(viewer, DeadSunStrikePayload.TYPE))
                        ServerPlayNetworking.send(viewer, payload);
                });
            }
        }
    }

    private static void tickPendingStrikes(ServerLevel level) {
        Iterator<PendingStrike> iterator = PENDING_STRIKES.iterator();
        while (iterator.hasNext()) {
            PendingStrike strike = iterator.next();
            if (--strike.ticks > 0) continue;
            net.krodark.asterion.WorldGenerator.applyDeadSunBarrageImpact(
                    level, strike.target, strike.radius);
            iterator.remove();
        }
    }

    private static long scheduleNext(RandomSource random, long now) {
        return now + random.nextIntBetweenInclusive(MIN_INTERVAL, MAX_INTERVAL);
    }

    private static final class PendingStrike {
        private final BlockPos target;
        private int ticks;
        private final float radius;
        @SuppressWarnings("unused") private final long seed;
        private PendingStrike(BlockPos target, int ticks, float radius, long seed) {
            this.target = target;
            this.ticks = ticks;
            this.radius = radius;
            this.seed = seed;
        }
    }

    public interface Definition {
        Identifier id();
        int weight();
        int minDurationTicks();
        int maxDurationTicks();
        float intensity(RandomSource random);
        default boolean eligible(ServerLevel level) { return true; }
        default void onStart(ServerLevel level, long seed, int durationTicks, float intensity) { }
        default void onTick(ServerLevel level, int elapsedTicks) { }
        default void onEnd(ServerLevel level) { }
    }

    private static final class PoisonGeyser {
        private final BlockPos vent;
        private int warningTicks;
        private int activeTicks;
        private final float radius;
        private final float columnHeight;
        private final Set<UUID> knockedDown = new HashSet<>();
        private final Map<UUID, Long> lastDamage = new HashMap<>();
        private boolean erupted;
        private PoisonGeyser(BlockPos vent, int warningTicks, int activeTicks, float radius,
                             float columnHeight) {
            this.vent = vent;
            this.warningTicks = warningTicks;
            this.activeTicks = activeTicks;
            this.radius = radius;
            this.columnHeight = columnHeight;
        }
    }

    private static final class CrimsonFirefly {
        private final UUID target;
        private Vec3 position;
        private int life;
        private final float phase;
        private int age;
        private CrimsonFirefly(UUID target, Vec3 position, int life, float phase) {
            this.target = target;
            this.position = position;
            this.life = life;
            this.phase = phase;
        }
    }

    private static final class SchedulerState {
        private long nextEventTick = -1L;
        private ActiveEvent active;
    }

    private static final class ActiveEvent {
        private final Definition definition;
        private int durationTicks;
        private final long seed;
        private final float intensity;
        private final Set<UUID> notifiedPlayers = new HashSet<>();
        private final Set<UUID> hunterPlayers = new HashSet<>();
        private final Map<UUID, Integer> hunterRevealTicks = new LinkedHashMap<>();
        private int elapsed;

        private ActiveEvent(Definition definition, int durationTicks, long seed, float intensity) {
            this.definition = definition;
            this.durationTicks = durationTicks;
            this.seed = seed;
            this.intensity = intensity;
        }
    }
}
