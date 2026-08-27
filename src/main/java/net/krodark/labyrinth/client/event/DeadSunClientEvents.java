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

/** Client registry and sampler for visual Dead Sun events. */
public final class DeadSunClientEvents {
    private static final Map<Identifier, Factory> FACTORIES = new HashMap<>();
    private static ActiveEffect active;

    static {
        register(DeadSunEventSystem.RUMBLE, RumbleEffect::new);
    }

    private DeadSunClientEvents() {
    }

    public static void register(Identifier id, Factory factory) {
        if (FACTORIES.putIfAbsent(id, factory) != null)
            throw new IllegalArgumentException("Duplicate client Dead Sun event: " + id);
    }

    public static void receive(DeadSunEventPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Factory factory = FACTORIES.get(payload.eventId());
        if (factory == null) {
            Labyrinth.LOGGER.warn("Ignoring unknown Dead Sun event {}", payload.eventId());
            return;
        }
        active = factory.create(payload.seed(), client.level.getGameTime() - payload.elapsedTicks(),
                payload.durationTicks(), payload.intensity());
    }

    public static void tick(Minecraft client) {
        if (client.level == null || !client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) {
            active = null;
            return;
        }
        if (active != null && client.level.getGameTime() > active.endTick()) active = null;
    }

    public static Sample sample(float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (active == null || client.level == null) return Sample.NONE;
        return active.sample(client.level.getGameTime() + partialTick);
    }

    public static Vec3 sunOffset() {
        Minecraft client = Minecraft.getInstance();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return sample(partial).sunOffset;
    }

    @FunctionalInterface
    public interface Factory {
        ActiveEffect create(long seed, long startTick, int durationTicks, float intensity);
    }

    public interface ActiveEffect {
        long endTick();
        Sample sample(double gameTick);
    }

    public record Sample(Vec3 cameraOffset, float yawDegrees, float pitchDegrees, Vec3 sunOffset) {
        public static final Sample NONE = new Sample(Vec3.ZERO, 0.0F, 0.0F, Vec3.ZERO);
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

            // The Sun moves more slowly and farther than the camera, like something enormous stirring.
            double sunX = noise(seed + 131, elapsed * 0.19D) * 5.5D * strength;
            double sunY = noise(seed + 163, elapsed * 0.23D) * 3.2D * strength;
            double sunZ = noise(seed + 197, elapsed * 0.17D) * 5.5D * strength;
            return new Sample(new Vec3(cameraX, cameraY, cameraZ), yaw, pitch,
                    new Vec3(sunX, sunY, sunZ));
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
}
