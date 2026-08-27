package net.krodark.labyrinth.event;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.network.DeadSunEventPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Weighted, server-authoritative event scheduler for the Dead Sun. Add future events with
 * {@link #register(Definition)}; scheduling, duration, networking, and lifecycle are shared.
 */
public final class DeadSunEventSystem {
    public static final Identifier RUMBLE = Labyrinth.id("rumble");
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
    }

    private DeadSunEventSystem() {
    }

    public static void register(Definition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null)
            throw new IllegalArgumentException("Duplicate Dead Sun event: " + definition.id());
    }

    /** Starts a registered event immediately; useful for future encounters, commands, or boss states. */
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
            syncNewPlayers(level, state.active);
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
        private final int durationTicks;
        private final long seed;
        private final float intensity;
        private final Set<UUID> notifiedPlayers = new HashSet<>();
        private int elapsed;

        private ActiveEvent(Definition definition, int durationTicks, long seed, float intensity) {
            this.definition = definition;
            this.durationTicks = durationTicks;
            this.seed = seed;
            this.intensity = intensity;
        }
    }
}
