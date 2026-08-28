package net.krodark.asterion.client.event;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.network.DeadSunEventPayload;
import net.krodark.asterion.network.MazeShiftPayload;
import net.krodark.asterion.network.DeadSunStrikePayload;
import net.krodark.asterion.network.MazeZapPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class DeadSunClientEvents {
    private static final Map<Identifier, Factory> FACTORIES = new HashMap<>();
    private static ActiveEffect active;
    private static Sample outroStart;
    private static long outroStartTick;
    private static int outroDuration;
    private static DeadSunEventPayload pending;
    private static Identifier activeId;
    private static long activeSeed;
    private static int eclipseIntroTicks;
    private static final List<LocalRumble> LOCAL_RUMBLES = new ArrayList<>();
    private static final List<StrikeWarning> STRIKE_WARNINGS = new ArrayList<>();
    private static int wardDarknessTicks;
    private static int wardDarknessDuration;

    static {
        register(DeadSunEventSystem.RUMBLE, RumbleEffect::new);
        register(DeadSunEventSystem.ECLIPSE, EclipseEffect::new);
        register(DeadSunEventSystem.SHIFTING, NeutralEffect::new);
        register(DeadSunEventSystem.DEAD_SUN_BARRAGE, NeutralEffect::new);
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
            Asterion.LOGGER.warn("Ignoring unknown Dead Sun event {}", payload.eventId());
            return;
        }
        pending = payload;
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        applyPending(client, factory);
    }

    public static void receiveShift(MazeShiftPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        LOCAL_RUMBLES.add(new LocalRumble(Vec3.atCenterOf(payload.center()), client.level.getGameTime(),
                Math.max(1, payload.durationTicks()), payload.radius(), payload.intensity(),
                payload.center().asLong()));
    }

    public static void receiveStrike(DeadSunStrikePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        STRIKE_WARNINGS.add(new StrikeWarning(payload.target(), Math.max(1, payload.warningTicks()),
                payload.radius(), payload.seed()));
    }

    public static void receiveWardZap(MazeZapPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || payload.targetEntityId() != client.player.getId()) return;
        wardDarknessDuration = Math.max(40, payload.durationTicks() + 24);
        wardDarknessTicks = wardDarknessDuration;
    }

    private static void applyPending(Minecraft client, Factory factory) {
        if (pending == null || client.level == null) return;
        double now = client.level.getGameTime();
        if (pending.intensity() <= 0.0001F) {
            Sample current = active == null ? Sample.NONE : active.sample(now);
            if (!isEmpty(current)) beginOutro(current, client.level.getGameTime(), 40);
            else clearOutro();
            active = null;
            activeId = null;
            activeSeed = 0L;
            eclipseIntroTicks = 0;
            pending = null;
            return;
        }
        boolean newEvent = activeId == null || !activeId.equals(pending.eventId()) || activeSeed != pending.seed();
        if (newEvent && active != null) {
            Sample current = active.sample(now);
            if (!isEmpty(current)) beginOutro(current, client.level.getGameTime(), 32);
        }
        activeId = pending.eventId();
        activeSeed = pending.seed();
        if (newEvent && activeId.equals(DeadSunEventSystem.ECLIPSE)) eclipseIntroTicks = 0;
        if (newEvent) Asterion.LOGGER.info("Client activated Dead Sun event {} (elapsed {}/{})",
                pending.eventId(), pending.elapsedTicks(), pending.durationTicks());
        active = factory.create(pending.seed(), client.level.getGameTime() - pending.elapsedTicks(),
                pending.durationTicks(), pending.intensity());
        pending = null;
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            active = null;
            clearOutro();
            LOCAL_RUMBLES.clear();
            STRIKE_WARNINGS.clear();
            wardDarknessTicks = 0;
            return;
        }
        if (!client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            clearTransientEffects();
            return;
        }
        if (pending != null) {
            Factory factory = FACTORIES.get(pending.eventId());
            if (factory != null) applyPending(client, factory);
        }
        if (isEclipseActive()) eclipseIntroTicks++;
        if (wardDarknessTicks > 0) wardDarknessTicks--;
        tickStrikeWarnings(client);
        LOCAL_RUMBLES.removeIf(rumble -> client.level.getGameTime() > rumble.startTick + rumble.duration);
        if (active != null && client.level.getGameTime() > active.endTick()) {
            Sample ending = active.sample(active.endTick());
            if (!isEmpty(ending)) beginOutro(ending, client.level.getGameTime(), 40);
            active = null;
            activeId = null;
        }
        if (outroStart != null && client.level.getGameTime() > outroStartTick + outroDuration)
            clearOutro();
    }

    public static Sample sample(float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL))
            return Sample.NONE;
        double now = client.level.getGameTime() + partialTick;
        Sample base = active == null ? Sample.NONE : active.sample(now);
        if (outroStart != null) base = combine(base, sampleOutro(now));
        if (client.player == null || LOCAL_RUMBLES.isEmpty()) return base;
        Vec3 offset = base.cameraOffset;
        float yaw = base.yawDegrees, pitch = base.pitchDegrees;
        for (LocalRumble rumble : LOCAL_RUMBLES) {
            double distance = client.player.position().distanceTo(rumble.center);
            double proximity = 1.0D - Mth.clamp(distance / Math.max(1.0F, rumble.radius), 0.0D, 1.0D);
            double elapsed = now - rumble.startTick;
            double life = Mth.clamp(elapsed / rumble.duration, 0.0D, 1.0D);
            double envelope = Math.sin(life * Math.PI) * proximity * proximity * rumble.intensity;
            offset = offset.add(RumbleEffect.noise(rumble.seed + 3, elapsed * 0.92D) * 0.075D * envelope,
                    RumbleEffect.noise(rumble.seed + 7, elapsed * 1.08D) * 0.050D * envelope,
                    RumbleEffect.noise(rumble.seed + 11, elapsed * 0.87D) * 0.075D * envelope);
            yaw += (float) (RumbleEffect.noise(rumble.seed + 17, elapsed * 0.76D) * 0.9D * envelope);
            pitch += (float) (RumbleEffect.noise(rumble.seed + 23, elapsed * 0.81D) * 0.7D * envelope);
        }
        return new Sample(offset, yaw, pitch, base.sunOffset, base.eclipseStrength);
    }

    public static void clearTransientEffects() {
        active = null;
        activeId = null;
        clearOutro();
        pending = null;
        LOCAL_RUMBLES.clear();
        STRIKE_WARNINGS.clear();
        wardDarknessTicks = 0;
        eclipseIntroTicks = 0;
    }

    private static void tickStrikeWarnings(Minecraft client) {
        Iterator<StrikeWarning> iterator = STRIKE_WARNINGS.iterator();
        while (iterator.hasNext()) {
            StrikeWarning warning = iterator.next();
            int elapsed = warning.totalTicks - warning.remainingTicks;
            double pulse = 0.82D + Math.sin(elapsed * 0.55D) * 0.12D;
            int points = 28;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0D * i / points + elapsed * 0.025D;
                double radius = warning.radius * pulse;
                client.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                        warning.target.getX() + 0.5D + Math.cos(angle) * radius,
                        warning.target.getY() + 0.16D,
                        warning.target.getZ() + 0.5D + Math.sin(angle) * radius,
                        0.0D, 0.012D, 0.0D);
            }
            if ((warning.remainingTicks & 3) == 0)
                client.level.addParticle(ParticleTypes.END_ROD,
                        warning.target.getX() + 0.5D, warning.target.getY() + 0.18D,
                        warning.target.getZ() + 0.5D, 0.0D, 0.025D, 0.0D);
            if (--warning.remainingTicks <= 0) {
                LOCAL_RUMBLES.add(new LocalRumble(Vec3.atCenterOf(warning.target),
                        client.level.getGameTime(), 18, 18.0F, 1.0F, warning.seed));
                iterator.remove();
            }
        }
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

    public static float darknessStrength() {
        float eclipse = eclipseStrength();
        if (wardDarknessTicks <= 0 || wardDarknessDuration <= 0) return eclipse;
        int elapsed = wardDarknessDuration - wardDarknessTicks;
        float fadeIn = Mth.clamp(elapsed / 8.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(wardDarknessTicks / 16.0F, 0.0F, 1.0F);
        float ward = fadeIn * fadeIn * (3.0F - 2.0F * fadeIn)
                * fadeOut * fadeOut * (3.0F - 2.0F * fadeOut);
        return Math.max(eclipse, ward);
    }

    public static boolean isEclipseActive() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Asterion.ASTERION_LEVEL)
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

    private static Sample combine(Sample a, Sample b) {
        return new Sample(a.cameraOffset.add(b.cameraOffset), a.yawDegrees + b.yawDegrees,
                a.pitchDegrees + b.pitchDegrees, a.sunOffset.add(b.sunOffset),
                Math.max(a.eclipseStrength, b.eclipseStrength));
    }

    private static boolean isEmpty(Sample sample) {
        return sample.eclipseStrength <= 0.0001F
                && sample.cameraOffset.lengthSqr() <= 1.0E-8D
                && sample.sunOffset.lengthSqr() <= 1.0E-8D
                && Math.abs(sample.yawDegrees) <= 0.0001F
                && Math.abs(sample.pitchDegrees) <= 0.0001F;
    }

    private static void beginOutro(Sample start, long startTick, int duration) {
        outroStart = start;
        outroStartTick = startTick;
        outroDuration = Math.max(1, duration);
    }

    private static Sample sampleOutro(double gameTick) {
        float progress = (float)Mth.clamp((gameTick - outroStartTick) / outroDuration, 0.0D, 1.0D);
        float remaining = 1.0F - progress * progress * (3.0F - 2.0F * progress);
        return new Sample(outroStart.cameraOffset.scale(remaining),
                outroStart.yawDegrees * remaining, outroStart.pitchDegrees * remaining,
                outroStart.sunOffset.scale(remaining), outroStart.eclipseStrength * remaining);
    }

    private static void clearOutro() {
        outroStart = null;
        outroStartTick = 0L;
        outroDuration = 0;
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

    private static final class NeutralEffect implements ActiveEffect {
        private final long endTick;
        private NeutralEffect(long seed, long startTick, int duration, float intensity) {
            this.endTick = startTick + Math.max(1, duration);
        }
        @Override public long endTick() { return endTick; }
        @Override public Sample sample(double gameTick) { return Sample.NONE; }
    }

    private record LocalRumble(Vec3 center, long startTick, int duration, float radius,
                               float intensity, long seed) { }

    private static final class StrikeWarning {
        private final net.minecraft.core.BlockPos target;
        private final int totalTicks;
        private int remainingTicks;
        private final float radius;
        private final long seed;
        private StrikeWarning(net.minecraft.core.BlockPos target, int ticks, float radius, long seed) {
            this.target = target;
            this.totalTicks = ticks;
            this.remainingTicks = ticks;
            this.radius = radius;
            this.seed = seed;
        }
    }
}
