package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.network.DeadSunEventPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Restores eclipse/rumble presentation state consumed by camera and Veil. */
public final class PortDeadSunEvents {
    public record Sample(Vec3 cameraOffset, float yaw, float pitch, Vec3 sunOffset, float eclipse) {
        public static final Sample NONE = new Sample(Vec3.ZERO, 0, 0, Vec3.ZERO, 0);
    }
    private record Active(ResourceLocation id, long seed, long start, int duration, float intensity) {}
    private record Rumble(Vec3 center, long start, int duration, float radius, float strength, long seed) {}
    private static final class Warning {
        final net.minecraft.core.BlockPos target;
        final int total;
        int remaining;
        final float radius;
        Warning(DeadSunStrikePayload payload) {
            target = payload.target(); total = remaining = Math.max(1, payload.warningTicks()); radius = payload.radius();
        }
    }

    private static Active active;
    private static final List<Rumble> rumbles = new ArrayList<>();
    private static final List<Warning> warnings = new ArrayList<>();

    private PortDeadSunEvents() {}

    public static void receive(DeadSunEventPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.intensity() <= 0.0001F || client.level == null) { active = null; return; }
        active = new Active(payload.eventId(), payload.seed(),
                client.level.getGameTime() - payload.elapsedTicks(), Math.max(1, payload.durationTicks()),
                payload.intensity());
        if (payload.eventId().equals(DeadSunEventSystem.ECLIPSE) && payload.elapsedTicks() <= 40)
            client.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    Asterion.ECLIPSE_EVENT_SOUND, 1.0F, 1.0F));
    }

    public static void receive(MazeShiftPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        rumbles.add(new Rumble(Vec3.atCenterOf(payload.center()), client.level.getGameTime(),
                Math.max(1, payload.durationTicks()), payload.radius(), payload.intensity(), payload.center().asLong()));
        PortPhysicsDebris.spawnRumble(Vec3.atCenterOf(payload.center()), payload.radius(), payload.intensity(),
                payload.center().asLong());
    }

    public static void receive(DeadSunStrikePayload payload) { warnings.add(new Warning(payload)); }

    public static void tick(Minecraft client) {
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            active = null; rumbles.clear(); warnings.clear(); return;
        }
        long now = client.level.getGameTime();
        if (active != null && now > active.start + active.duration) active = null;
        rumbles.removeIf(rumble -> now > rumble.start + rumble.duration);
        for (int index = warnings.size() - 1; index >= 0; index--) {
            Warning warning = warnings.get(index);
            int elapsed = warning.total - warning.remaining;
            double pulse = 0.86D + Math.sin(elapsed * 0.55D) * 0.10D;
            for (int point = 0; point < 24; point++) {
                double angle = Mth.TWO_PI * point / 24.0D + elapsed * 0.025D;
                client.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        warning.target.getX() + 0.5D + Math.cos(angle) * warning.radius * pulse,
                        warning.target.getY() + 0.16D,
                        warning.target.getZ() + 0.5D + Math.sin(angle) * warning.radius * pulse,
                        0, 0.012D, 0);
            }
            if (--warning.remaining <= 0) {
                rumbles.add(new Rumble(Vec3.atCenterOf(warning.target), now, 18, 18, 1, warning.target.asLong()));
                PortPhysicsDebris.spawnRumble(Vec3.atCenterOf(warning.target), 18, 1, warning.target.asLong());
                warnings.remove(index);
            }
        }
    }

    public static Sample sample(Vec3 listener, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return Sample.NONE;
        double now = client.level.getGameTime() + partial;
        Vec3 camera = Vec3.ZERO, sun = Vec3.ZERO;
        float yaw = 0, pitch = 0, eclipse = 0;
        if (active != null) {
            double elapsed = Math.max(0, now - active.start);
            double remaining = Math.max(0, active.start + active.duration - now);
            if (active.id.equals(DeadSunEventSystem.ECLIPSE)) {
                float in = (float)Mth.clamp(elapsed / 60.0D, 0, 1);
                float out = (float)Mth.clamp(remaining / 50.0D, 0, 1);
                eclipse = active.intensity * smooth(in) * smooth(out);
                double phase = elapsed * 0.025D + (active.seed & 1023L) * 0.013D;
                sun = new Vec3(Math.sin(phase) * 1.8D, -7.0D + Math.sin(phase * 0.63D) * 0.9D,
                        Math.cos(phase * 0.81D) * 1.4D).scale(eclipse);
            } else if (active.id.equals(DeadSunEventSystem.RUMBLE)) {
                float envelope = smooth((float)Math.min(elapsed / (active.duration * 0.16D), 1))
                        * smooth((float)Math.min(remaining / (active.duration * 0.22D), 1)) * active.intensity;
                camera = noiseVector(active.seed, elapsed, 0.045D).scale(envelope);
                yaw += (float)(noise(active.seed + 71, elapsed * 0.58D) * 0.62D * envelope);
                pitch += (float)(noise(active.seed + 97, elapsed * 0.63D) * 0.46D * envelope);
                sun = noiseVector(active.seed + 131, elapsed * 0.22D, 5.5D).scale(envelope);
            }
        }
        for (Rumble rumble : rumbles) {
            double proximity = 1.0D - Mth.clamp(listener.distanceTo(rumble.center) / Math.max(1, rumble.radius), 0, 1);
            double elapsed = now - rumble.start;
            double envelope = Math.sin(Mth.clamp(elapsed / rumble.duration, 0, 1) * Math.PI)
                    * proximity * proximity * rumble.strength;
            camera = camera.add(noiseVector(rumble.seed, elapsed, 0.07D).scale(envelope));
            yaw += noise(rumble.seed + 17, elapsed * 0.76D) * 0.9D * envelope;
            pitch += noise(rumble.seed + 23, elapsed * 0.81D) * 0.7D * envelope;
        }
        return new Sample(camera, yaw, pitch, sun, eclipse);
    }

    public static Vec3 sunOffset() {
        Minecraft client = Minecraft.getInstance();
        Vec3 listener = client.player == null ? Vec3.ZERO : client.player.position();
        return sample(listener, 1).sunOffset;
    }

    public static float eclipse() {
        Minecraft client = Minecraft.getInstance();
        Vec3 listener = client.player == null ? Vec3.ZERO : client.player.position();
        return sample(listener, 1).eclipse;
    }

    private static float smooth(float value) { return value * value * (3 - 2 * value); }
    private static Vec3 noiseVector(long seed, double elapsed, double scale) {
        return new Vec3(noise(seed + 3, elapsed * 0.92D), noise(seed + 7, elapsed * 1.08D),
                noise(seed + 11, elapsed * 0.87D)).scale(scale);
    }
    private static float noise(long seed, double position) {
        long left = (long)Math.floor(position);
        double fraction = position - Math.floor(position);
        return (float)Mth.lerp(smooth((float)fraction), hash(seed, left), hash(seed, left + 1));
    }
    private static double hash(long seed, long index) {
        long value = seed ^ index * 0x9E3779B97F4A7C15L;
        value ^= value >>> 30; value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27; value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2.0D - 1.0D;
    }
}
