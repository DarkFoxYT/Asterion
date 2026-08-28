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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.phys.AABB;

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

public final class DeadSunEventSystem {
    public static final Identifier RUMBLE = Asterion.id("rumble");
    public static final Identifier ECLIPSE = Asterion.id("eclipse");
    public static final Identifier SHIFTING = Asterion.id("shifting");
    public static final Identifier DEAD_SUN_BARRAGE = Asterion.id("dead_sun_barrage");
    private static final int MIN_INTERVAL = 20 * 35;
    private static final int MAX_INTERVAL = 20 * 95;
    private static final Map<Identifier, Definition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, SchedulerState> STATES = new WeakHashMap<>();
    private static final ArrayDeque<WallLayer> SHIFT_ANIMATION = new ArrayDeque<>();
    private static final List<PendingStrike> PENDING_STRIKES = new ArrayList<>();

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
            @Override public int weight() { return 6; }
            @Override public int minDurationTicks() { return 20 * 22; }
            @Override public int maxDurationTicks() { return 20 * 38; }
            @Override public float intensity(RandomSource random) { return 0.8F + random.nextFloat() * 0.2F; }
            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                tickPendingStrikes(level);
                if (elapsedTicks >= 20 && elapsedTicks % 42 == 0) scheduleStrikes(level);
            }
            @Override public void onEnd(ServerLevel level) {
                PENDING_STRIKES.clear();
            }
        });
        register(new Definition() {
            @Override public Identifier id() { return ECLIPSE; }
            @Override public int weight() { return 4; }
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
                                "That event cannot start while the Eclipse is active."));
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

    public static boolean isEclipseActive(ServerLevel level) {
        SchedulerState state = STATES.get(level.getServer());
        return state != null && state.active != null && state.active.definition.id().equals(ECLIPSE);
    }

    public static void clearRuntimeState(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.ASTERION_LEVEL);
        if (level != null) finishWallShift(level);
        PENDING_STRIKES.clear();
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
            return 20 * (12 + (int)Math.floorMod(mixed, 34L));
        });
        return elapsedTicks >= reveal;
    }

    public static void register(Definition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null)
            throw new IllegalArgumentException("Duplicate Dead Sun event: " + definition.id());
    }

    public static boolean trigger(ServerLevel level, Identifier eventId) {
        Definition definition = DEFINITIONS.get(eventId);
        if (definition == null || WorldGenerator.isBossEncounterActive(level)) return false;
        SchedulerState state = STATES.computeIfAbsent(level.getServer(), ignored -> new SchedulerState());
        if (definition.id().equals(SHIFTING) && isEclipseActive(level)) return false;
        if (state.active != null) state.active.definition.onEnd(level);
        start(level, state, definition);
        return true;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Asterion.ASTERION_LEVEL);
        if (level == null) return;
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
                .filter(definition -> !definition.id().equals(SHIFTING) || !isEclipseActive(level)).toList();
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
        RandomSource random = level.getRandom();
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) continue;
            BlockPos origin = player.blockPosition();
            for (int attempt = 0; attempt < 10; attempt++) {
                int x = origin.getX() + random.nextIntBetweenInclusive(-13, 13);
                int z = origin.getZ() + random.nextIntBetweenInclusive(-13, 13);
                int y = 49 + Math.max(8, net.krodark.asterion.AsterionConfig.INSTANCE.wallHeight - 1
                        - random.nextInt(3));
                BlockPos wall = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(wall);
                if (!isMazeWall(state) || !level.getBlockState(wall.below()).isCollisionShapeFullBlock(level, wall.below()))
                    continue;
                level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, state),
                        x + 0.5D, y + 0.25D, z + 0.5D,
                        9, 0.42D, 0.2D, 0.42D, 0.025D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x + 0.5D, y + 0.4D, z + 0.5D,
                        3, 0.35D, 0.18D, 0.35D, 0.012D);
                if (random.nextFloat() < 0.32F && level.getBlockState(wall.above()).isAir()) {
                    FallingBlockEntity rubble = FallingBlockEntity.fall(level, wall, state);
                    rubble.time = 1;
                    rubble.dropItem = false;
                }
                if (random.nextFloat() < 0.45F)
                    level.playSound(null, wall, SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS,
                            0.65F, 0.62F + random.nextFloat() * 0.18F);
                break;
            }
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

    private static void scheduleStrikes(ServerLevel level) {
        RandomSource random = level.getRandom();
        for (var player : level.players()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
            int count = 2 + random.nextInt(3);
            for (int strikeIndex = 0; strikeIndex < count; strikeIndex++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double distance = 4.0D + random.nextDouble() * 14.0D;
                int x = net.minecraft.util.Mth.floor(player.getX() + Math.cos(angle) * distance);
                int z = net.minecraft.util.Mth.floor(player.getZ() + Math.sin(angle) * distance);
                BlockPos target = net.krodark.asterion.WorldGenerator.findDeadSunStrikeTarget(
                        level, x, z, net.minecraft.util.Mth.floor(player.getY()));
                int warning = 30 + random.nextInt(10);
                float radius = 3.4F + random.nextFloat() * 2.2F;
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
        default void onStart(ServerLevel level, long seed, int durationTicks, float intensity) { }
        default void onTick(ServerLevel level, int elapsedTicks) { }
        default void onEnd(ServerLevel level) { }
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
