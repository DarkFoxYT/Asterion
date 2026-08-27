package net.krodark.labyrinth.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.Command;
import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.network.DeadSunEventPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.krodark.labyrinth.entity.MinotaurEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class DeadSunEventSystem {
    public static final Identifier RUMBLE = Labyrinth.id("rumble");
    public static final Identifier ECLIPSE = Labyrinth.id("eclipse");
    private static final int MIN_INTERVAL = 20 * 35;
    private static final int MAX_INTERVAL = 20 * 95;
    private static final Map<Identifier, Definition> DEFINITIONS = new LinkedHashMap<>();
    private static final Map<MinecraftServer, SchedulerState> STATES = new WeakHashMap<>();

    static {
        register(new Definition() {
            @Override public Identifier id() { return RUMBLE; }
            @Override public int weight() { return 10; }
            @Override public int minDurationTicks() { return 70; }
            @Override public int maxDurationTicks() { return 135; }
            @Override public float intensity(RandomSource random) { return 0.62F + random.nextFloat() * 0.38F; }
        });
        register(new Definition() {
            @Override public Identifier id() { return ECLIPSE; }
            @Override public int weight() { return 4; }
            @Override public int minDurationTicks() { return 20 * 60; }
            @Override public int maxDurationTicks() { return 20 * 60 * 10; }
            @Override public float intensity(RandomSource random) { return 0.82F + random.nextFloat() * 0.18F; }

            @Override public void onStart(ServerLevel level, long seed, int durationTicks, float intensity) {
                level.players().forEach(player -> player.sendSystemMessage(
                        Component.translatable("event.labyrinth.eclipse.begins")));
            }

            @Override public void onTick(ServerLevel level, int elapsedTicks) {
                // Let the fog settle before the silhouette begins appearing.
                if (elapsedTicks < 60 || (elapsedTicks % 20) != 0) return;
                java.util.List<MinotaurEntity> hunters = new java.util.ArrayList<>(eclipseMinotaurs(level));
                for (var player : level.players()) {
                    if (!player.isAlive() || player.isCreative() || player.isSpectator()) continue;
                    boolean assigned = hunters.stream().anyMatch(minotaur -> minotaur.isAssignedTo(player));
                    if (!assigned && claimHunter(level, player.getUUID())) {
                        MinotaurEntity hunter = MinotaurEntity.spawnHunter(level, player);
                        if (hunter == null) releaseHunterClaim(level, player.getUUID());
                        else hunters.add(hunter);
                    }
                }
            }

            @Override public void onEnd(ServerLevel level) {
                eclipseMinotaurs(level).forEach(MinotaurEntity::endEclipse);
                level.players().forEach(player -> player.sendSystemMessage(
                        Component.translatable("event.labyrinth.eclipse.ends")));
            }
        });
    }

    private DeadSunEventSystem() {
    }

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, environment) -> {
            var root = Commands.literal("labyrinthevent")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(command -> showStatus(command.getSource()));
            var start = Commands.literal("start");
            for (Definition definition : DEFINITIONS.values()) {
                String name = definition.id().getPath();
                start.then(Commands.literal(name).executes(command -> {
                    ServerLevel level = command.getSource().getServer().getLevel(Labyrinth.LABYRINTH_LEVEL);
                    if (level == null) {
                        command.getSource().sendFailure(Component.literal("The Labyrinth dimension is not loaded."));
                        return 0;
                    }
                    if (level.players().isEmpty()) {
                        command.getSource().sendFailure(Component.literal(
                                "No players are inside the Labyrinth. Enter the dimension before testing this event."));
                        return 0;
                    }
                    trigger(level, definition.id());
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
                command.getSource().sendSuccess(() -> Component.literal("Available Labyrinth events: " + names), false);
                return Command.SINGLE_SUCCESS;
            }));
            root.then(Commands.literal("status").executes(command -> showStatus(command.getSource())));
            root.then(Commands.literal("stop").executes(command -> {
                ServerLevel level = command.getSource().getServer().getLevel(Labyrinth.LABYRINTH_LEVEL);
                if (level == null || !stop(level)) {
                    command.getSource().sendFailure(Component.literal("No Labyrinth event is active."));
                    return 0;
                }
                command.getSource().sendSuccess(() -> Component.literal("Stopped the active Labyrinth event."), true);
                return Command.SINGLE_SUCCESS;
            }));
            dispatcher.register(root);
        });
    }

    private static int showStatus(net.minecraft.commands.CommandSourceStack source) {
        SchedulerState state = STATES.get(source.getServer());
        if (state == null || state.active == null) {
            source.sendSuccess(() -> Component.literal("No Labyrinth event is active."), false);
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
        DeadSunEventPayload stopPayload = new DeadSunEventPayload(event.definition.id(), event.seed, 1, 1, 0.0F);
        level.players().forEach(player -> {
            if (ServerPlayNetworking.canSend(player, DeadSunEventPayload.TYPE))
                ServerPlayNetworking.send(player, stopPayload);
        });
        state.active = null;
        state.nextEventTick = scheduleNext(level.getRandom(), level.getGameTime());
        return true;
    }

    public static boolean isEclipseActive(ServerLevel level) {
        SchedulerState state = STATES.get(level.getServer());
        return state != null && state.active != null && state.active.definition.id().equals(ECLIPSE);
    }

    /** Called by a successfully escaped or repelled hunter. */
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

    public static void register(Definition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null)
            throw new IllegalArgumentException("Duplicate Dead Sun event: " + definition.id());
    }

    public static boolean trigger(ServerLevel level, Identifier eventId) {
        Definition definition = DEFINITIONS.get(eventId);
        if (definition == null) return false;
        SchedulerState state = STATES.computeIfAbsent(level.getServer(), ignored -> new SchedulerState());
        if (state.active != null) state.active.definition.onEnd(level);
        start(level, state, definition);
        return true;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(Labyrinth.LABYRINTH_LEVEL);
        if (level == null) return;
        SchedulerState state = STATES.computeIfAbsent(server, ignored -> new SchedulerState());
        long now = level.getGameTime();

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
                state.active.definition.onEnd(level);
                state.active = null;
                state.nextEventTick = scheduleNext(level.getRandom(), now);
            }
            return;
        }

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
        int totalWeight = DEFINITIONS.values().stream().mapToInt(definition -> Math.max(1, definition.weight())).sum();
        int roll = random.nextInt(totalWeight);
        Definition selected = DEFINITIONS.values().iterator().next();
        for (Definition definition : DEFINITIONS.values()) {
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
        Labyrinth.LOGGER.info("Dead Sun event {} began for {} ticks", selected.id(), duration);
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

    private static long scheduleNext(RandomSource random, long now) {
        return now + random.nextIntBetweenInclusive(MIN_INTERVAL, MAX_INTERVAL);
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
        private int elapsed;

        private ActiveEvent(Definition definition, int durationTicks, long seed, float intensity) {
            this.definition = definition;
            this.durationTicks = durationTicks;
            this.seed = seed;
            this.intensity = intensity;
        }
    }
}
