package net.krodark.labyrinth.client.event;

import net.krodark.labyrinth.Labyrinth;
import net.krodark.labyrinth.event.DeadSunEventSystem;
import net.krodark.labyrinth.network.DeadSunEventPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class DeadSunClientEvents {
    private static final Map<Identifier, Factory> FACTORIES = new HashMap<>();
    private static ActiveEffect active;
    private static DeadSunEventPayload pending;
    private static Identifier activeId;
    private static long activeSeed;
    private static int eclipseIntroTicks;

    static {
        register(DeadSunEventSystem.RUMBLE, RumbleEffect::new);
        register(DeadSunEventSystem.ECLIPSE, EclipseEffect::new);
    }

    private DeadSunClientEvents() {
    }

    public static void register(Identifier id, Factory factory) {
        if (FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalArgumentException("Duplicate client Dead Sun event: " + id);
        }
    }

    public static void receive(DeadSunEventPayload payload) {
        Minecraft client = Minecraft.getInstance();
        Factory factory = FACTORIES.get(payload.eventId());
        if (factory == null) {
            Labyrinth.LOGGER.warn("Ignoring unknown Dead Sun event {}", payload.eventId());
            return;
        }
        pending = payload;
        if (client.level == null || !client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) return;
        applyPending(client, factory);
    }

    private static void applyPending(Minecraft client, Factory factory) {
        if (pending == null || client.level == null) return;
        boolean newEvent = activeId == null || !activeId.equals(pending.eventId()) || activeSeed != pending.seed();
        activeId = pending.eventId();
        activeSeed = pending.seed();
        if (newEvent && activeId.equals(DeadSunEventSystem.ECLIPSE)) eclipseIntroTicks = 0;
        if (newEvent) Labyrinth.LOGGER.info("Client activated Dead Sun event {} (elapsed {}/{})",
                pending.eventId(), pending.elapsedTicks(), pending.durationTicks());
        active = factory.create(pending.seed(), client.level.getGameTime() - pending.elapsedTicks(),
                pending.durationTicks(), pending.intensity());
        pending = null;
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            active = null;
            return;
        }
        if (!client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) return;
        if (pending != null) {
            Factory factory = FACTORIES.get(pending.eventId());
            if (factory != null) applyPending(client, factory);
        }
        if (isEclipseActive()) eclipseIntroTicks++;
        if (active != null && client.level.getGameTime() > active.endTick()) {
            active = null;
            activeId = null;
        }
    }

    public static Sample sample(float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (active == null || client.level == null) {
            return Sample.NONE;
        }
        return active.sample(client.level.getGameTime() + partialTick);
    }

    public static Vec3 sunOffset() {
        Minecraft client = Minecraft.getInstance();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return sample(partial).sunOffset;
    }

    public static float eclipseStrength() {
        Minecraft client = Minecraft.getInstance();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return sample(partial).eclipseStrength;
    }

    public static boolean isEclipseActive() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)
                && activeId != null && activeId.equals(DeadSunEventSystem.ECLIPSE)
                && active != null && eclipseStrength() > 0.0001F;
    }

    public static int eclipseIntroTicks() {
        return eclipseIntroTicks;
    }

    @FunctionalInterface
    public interface Factory {
        ActiveEffect create(long seed, long startTick, int durationTicks, float intensity);
    }

    public interface ActiveEffect {
        long endTick();
        Sample sample(double gameTick);
    }

    public record Sample(Vec3 cameraOffset, float yawDegrees, float pitchDegrees, Vec3 sunOffset,
                         float eclipseStrength) {
        public static final Sample NONE = new Sample(Vec3.ZERO, 0.0F, 0.0F, Vec3.ZERO, 0.0F);
    }

    private static final class RumbleEffect implements ActiveEffect {
        private final long seed;
        private final long startTick;
        private final int duration;
        private final float intensity;

        private RumbleEffect(long seed, long startTick, int duration, float intensity) {
            this.seed = seed;
            this.startTick = startTick;
            this.duration = Math.max(1, duration);
            this.intensity = intensity;
        }

        @Override
        public long endTick() {
            return startTick + duration;
        }

        @Override
        public Sample sample(double gameTick) {
            double elapsed = gameTick - startTick;
            double progress = Mth.clamp(elapsed / duration, 0.0D, 1.0D);
            double envelope = smoothstep(Math.min(progress / 0.16D, 1.0D))
                    * smoothstep(Math.min((1.0D - progress) / 0.22D, 1.0D));
            double strength = intensity * envelope;

            double cameraX = noise(seed + 11, elapsed * 0.72D) * 0.045D * strength;
            double cameraY = noise(seed + 29, elapsed * 0.83D) * 0.032D * strength;
            double cameraZ = noise(seed + 47, elapsed * 0.67D) * 0.045D * strength;
            float yaw = (float) (noise(seed + 71, elapsed * 0.58D) * 0.62D * strength);
            float pitch = (float) (noise(seed + 97, elapsed * 0.63D) * 0.46D * strength);

            double sunX = noise(seed + 131, elapsed * 0.19D) * 5.5D * strength;
            double sunY = noise(seed + 163, elapsed * 0.23D) * 3.2D * strength;
            double sunZ = noise(seed + 197, elapsed * 0.17D) * 5.5D * strength;
            return new Sample(new Vec3(cameraX, cameraY, cameraZ), yaw, pitch,
                    new Vec3(sunX, sunY, sunZ), 0.0F);
        }

        private static double smoothstep(double value) {
            return value * value * (3.0D - 2.0D * value);
        }

        private static double noise(long seed, double position) {
            long left = (long) Math.floor(position);
            double fraction = position - Math.floor(position);
            double blend = smoothstep(fraction);
            return Mth.lerp(blend, hash(seed, left), hash(seed, left + 1));
        }

        private static double hash(long seed, long index) {
            long value = seed ^ index * 0x9E3779B97F4A7C15L;
            value ^= value >>> 30;
            value *= 0xBF58476D1CE4E5B9L;
            value ^= value >>> 27;
            value *= 0x94D049BB133111EBL;
            value ^= value >>> 31;
            return ((value >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
        }
    }

    private static final class EclipseEffect implements ActiveEffect {
        private final long seed;
        private final long startTick;
        private final int duration;
        private final float intensity;

        private EclipseEffect(long seed, long startTick, int duration, float intensity) {
            this.seed = seed;
            this.startTick = startTick;
            this.duration = Math.max(1, duration);
            this.intensity = intensity;
        }
        @Override public long endTick() { return startTick + duration; }
        @Override public Sample sample(double gameTick) {
            double elapsed = Math.max(0.0D, gameTick - startTick);
            double remaining = Math.max(0.0D, startTick + duration - gameTick);
            double in = Mth.clamp(elapsed / 60.0D, 0.0, 1.0);
            double out = Mth.clamp(remaining / 50.0D, 0.0, 1.0);
            float envelope = (float) (in * in * (3.0 - 2.0 * in) * out * out * (3.0 - 2.0 * out));
            double phase = elapsed * 0.025D + (seed & 1023L) * 0.013D;
            Vec3 sunOffset = new Vec3(
                    Math.sin(phase) * 1.8D,
                    -7.0D + Math.sin(phase * 0.63D) * 0.9D,
                    Math.cos(phase * 0.81D) * 1.4D).scale(envelope);
            double rumble = intensity * envelope;
            Vec3 cameraOffset = new Vec3(
                    RumbleEffect.noise(seed + 211, elapsed * 0.24D) * 0.018D,
                    RumbleEffect.noise(seed + 223, elapsed * 0.31D) * 0.012D,
                    RumbleEffect.noise(seed + 239, elapsed * 0.21D) * 0.018D).scale(rumble);
            float yaw = (float) (RumbleEffect.noise(seed + 251, elapsed * 0.18D) * 0.11D * rumble);
            float pitch = (float) (RumbleEffect.noise(seed + 269, elapsed * 0.23D) * 0.08D * rumble);
            return new Sample(cameraOffset, yaw, pitch, sunOffset, intensity * envelope);
        }
    }
}
