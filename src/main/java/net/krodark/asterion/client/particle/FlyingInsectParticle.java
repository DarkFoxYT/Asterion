package net.krodark.asterion.client.particle;

import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** A long-lived, independently wandering insect with a four-frame wing loop. */
public final class FlyingInsectParticle extends SingleQuadParticle {
    private static final int FRAMES = 4;
    private static final int TICKS_PER_FRAME = 2;
    private final SpriteSet sprites;
    private final boolean firefly;
    private float heading;
    private float targetHeading;
    private float targetVertical;
    private float speed;
    private int turnTicks;
    private int lightScanTicks;
    private Vec3 lightTarget;
    private final float phase;

    private FlyingInsectParticle(ClientLevel level, double x, double y, double z,
                                 double velocityX, double velocityY, double velocityZ,
                                 SpriteSet sprites, RandomSource random, boolean firefly) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites.first());
        this.sprites = sprites;
        this.firefly = firefly;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.heading = horizontalHeading(velocityX, velocityZ, random);
        this.targetHeading = heading;
        this.targetVertical = (float)Mth.clamp(velocityY, -0.025D, 0.025D);
        this.speed = initialSpeed(velocityX, velocityZ, random);
        this.turnTicks = 1;
        this.xd = Mth.cos(heading) * speed;
        this.yd = targetVertical;
        this.zd = Mth.sin(heading) * speed;
        this.hasPhysics = true;
        setSize(0.08F, 0.08F);
        this.friction = 1.0F;
        this.gravity = 0.0F;
        this.lifetime = 360 + random.nextInt(240);
        this.quadSize = firefly ? 0.105F : 0.085F;
        setAlpha(1.0F);
        setSprite(sprites.get(0, FRAMES));
        if (firefly) updateGlow();
    }

    public static Particle createFly(ClientLevel level, double x, double y, double z,
                                     double velocityX, double velocityY, double velocityZ,
                                     SpriteSet sprites, RandomSource random) {
        return new FlyingInsectParticle(level, x, y, z, velocityX, velocityY, velocityZ,
                sprites, random, false);
    }

    public static Particle createFirefly(ClientLevel level, double x, double y, double z,
                                         double velocityX, double velocityY, double velocityZ,
                                         SpriteSet sprites, RandomSource random) {
        return new FlyingInsectParticle(level, x, y, z, velocityX, velocityY, velocityZ,
                sprites, random, true);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }

        if (--turnTicks <= 0) chooseTurn();
        if (!firefly) steerTowardLight();
        float turn = Mth.wrapDegrees((targetHeading - heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        heading += Mth.clamp(turn, -0.13F, 0.13F);
        float bob = Mth.sin(age * 0.31F + phase) * 0.006F;
        xd = Mth.lerp(0.18D, xd, Mth.cos(heading) * speed);
        zd = Mth.lerp(0.18D, zd, Mth.sin(heading) * speed);
        yd = Mth.lerp(0.14D, yd, targetVertical + bob);
        avoidNearbyBlock();
        double intendedX = xd;
        double intendedY = yd;
        double intendedZ = zd;
        double beforeX = x;
        double beforeY = y;
        double beforeZ = z;
        move(intendedX, intendedY, intendedZ);
        if (Math.abs((x - beforeX) - intendedX) > 1.0E-5D
                || Math.abs((y - beforeY) - intendedY) > 1.0E-5D
                || Math.abs((z - beforeZ) - intendedZ) > 1.0E-5D)
            bounceFromCollision(intendedX, intendedY, intendedZ);

        roll = Mth.lerp(0.24F, roll, Mth.clamp(-turn * 0.75F, -0.38F, 0.38F));
        setSprite(sprites.get((age / TICKS_PER_FRAME) % FRAMES, FRAMES));
        int fadeTicks = 30;
        if (age > lifetime - fadeTicks) setAlpha((lifetime - age) / (float)fadeTicks);
        if (firefly) updateGlow();
    }

    private void chooseTurn() {
        targetHeading = heading + (random.nextFloat() - 0.5F) * 2.8F;
        // Occasionally perform a pronounced turn-around instead of only drifting.
        if (random.nextFloat() < 0.18F)
            targetHeading = heading + Mth.PI + (random.nextFloat() - 0.5F) * 0.65F;
        targetVertical = (random.nextFloat() - 0.5F) * 0.034F;
        speed = 0.018F + random.nextFloat() * 0.026F;
        turnTicks = 9 + random.nextInt(22);
    }

    private void steerTowardLight() {
        if (--lightScanTicks <= 0) {
            lightTarget = LedAmneticLight.nearestAttractor(new Vec3(x, y, z), 14.0D);
            lightScanTicks = 4 + random.nextInt(3);
        }
        if (lightTarget == null) return;
        double dx = lightTarget.x - x;
        double dy = lightTarget.y - y;
        double dz = lightTarget.z - z;
        double horizontalSquared = dx * dx + dz * dz;
        if (horizontalSquared < 0.05D && Math.abs(dy) < 0.35D) {
            // Orbit instead of sitting motionless inside the light source.
            targetHeading = heading + 0.65F;
            targetVertical = Mth.clamp((float)dy * 0.025F, -0.012F, 0.012F);
            return;
        }
        if (horizontalSquared > 1.0E-5D)
            targetHeading = (float)Math.atan2(dz, dx);
        targetVertical = Mth.clamp((float)dy * 0.018F, -0.028F, 0.028F);
        speed = Mth.lerp(0.18F, speed, 0.046F);
        turnTicks = Math.max(turnTicks, 5);
    }

    private void avoidNearbyBlock() {
        Vec3 forward = new Vec3(xd, yd, zd);
        if (forward.lengthSqr() < 1.0E-7D) return;
        Vec3 probe = forward.normalize().scale(0.34D);
        if (level.noCollision(getBoundingBox().move(probe))) return;

        // Prefer a free vertical escape, then bank sideways around the obstacle.
        boolean aboveFree = level.noCollision(getBoundingBox().move(0.0D, 0.28D, 0.0D));
        boolean belowFree = level.noCollision(getBoundingBox().move(0.0D, -0.28D, 0.0D));
        if (aboveFree || belowFree)
            targetVertical = aboveFree && (!belowFree || random.nextBoolean()) ? 0.032F : -0.032F;
        targetHeading = heading + (random.nextBoolean() ? 1.0F : -1.0F)
                * (Mth.HALF_PI + random.nextFloat() * 0.55F);
        turnTicks = 8 + random.nextInt(8);
    }

    private void bounceFromCollision(double attemptedX, double attemptedY, double attemptedZ) {
        if (Math.abs((x - xo) - attemptedX) > 1.0E-5D) xd = -attemptedX * 0.35D;
        if (Math.abs((y - yo) - attemptedY) > 1.0E-5D) yd = -attemptedY * 0.35D;
        if (Math.abs((z - zo) - attemptedZ) > 1.0E-5D) zd = -attemptedZ * 0.35D;
        targetHeading = (float)Math.atan2(zd, xd) + (random.nextFloat() - 0.5F) * 0.8F;
        targetVertical = Mth.clamp((float)yd, -0.034F, 0.034F);
        turnTicks = 7 + random.nextInt(9);
    }

    private void updateGlow() {
        float pulse = 0.62F + 0.38F * Mth.sin(age * 0.17F + phase) * Mth.sin(age * 0.17F + phase);
        setColor(1.0F, 0.94F, 0.72F);
        AsterionConfig config = AsterionConfig.INSTANCE;
        if (config.dynamicLightsEnabled)
            LedAmneticLight.updateItemGlowLight(this, new Vec3(x, y, z),
                    1.0F, 0.68F, 0.12F, 0.8F + pulse * 1.25F, 2.2F + pulse * 1.6F, false);
        int coreInterval = config.ambientParticleQuality == 0 ? 4
                : config.ambientParticleQuality == 1 ? 2 : 1;
        if (age % coreInterval == 0)
            AsterionEmissiveParticles.spawnFireflyCore(x, y, z, xd, yd, zd, pulse);
    }

    @Override public void remove() {
        if (firefly) LedAmneticLight.removeItemGlowLight(this);
        super.remove();
    }

    @Override protected int getLightCoords(float partialTick) {
        return firefly ? 0xF000F0 : super.getLightCoords(partialTick);
    }

    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }

    private static float horizontalHeading(double x, double z, RandomSource random) {
        return x * x + z * z > 1.0E-6D ? (float)Math.atan2(z, x) : random.nextFloat() * Mth.TWO_PI;
    }

    private static float initialSpeed(double x, double z, RandomSource random) {
        double supplied = Math.sqrt(x * x + z * z);
        return supplied > 1.0E-4D ? (float)Mth.clamp(supplied, 0.012D, 0.06D)
                : 0.02F + random.nextFloat() * 0.022F;
    }
}
