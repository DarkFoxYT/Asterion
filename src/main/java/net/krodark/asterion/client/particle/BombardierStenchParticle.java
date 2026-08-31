package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.Collections;
import java.util.WeakHashMap;
import java.util.Set;

/** Original green smoke that switches to custom emissive sprites when ignited. */
public final class BombardierStenchParticle extends AnimatedEmissiveParticle {
    private static final Set<BombardierStenchParticle> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private float targetSize;
    private int sizeChangeTicks;
    private boolean burning;
    private SpriteSet fireSprites;

    private BombardierStenchParticle(ClientLevel level, double x, double y, double z,
                                     double velocityX, double velocityY, double velocityZ,
                                     SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites);
        this.xd = velocityX;
        this.yd = velocityY + random.nextFloat() * 0.012F;
        this.zd = velocityZ;
        this.gravity = 0.000003F;
        this.friction = 0.985F;
        this.hasPhysics = false;
        this.lifetime = 90 + random.nextInt(50);
        this.quadSize = 0.9F;
        this.targetSize = randomSize(random);
        this.sizeChangeTicks = 7 + random.nextInt(11);
        setColor(0.35F, 1.0F, 0.20F);
        setAlpha(0.72F);
        setSpriteFromAge(sprites);
        ACTIVE.add(this);
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  SpriteSet sprites, RandomSource random) {
        return new BombardierStenchParticle(level, x, y, z,
                velocityX, velocityY, velocityZ, sprites, random);
    }

    @Override
    public void tick() {
        markTicked();
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }

        if (!burning && --sizeChangeTicks <= 0) {
            targetSize = randomSize(random);
            sizeChangeTicks = 7 + random.nextInt(13);
        }
        quadSize = Mth.lerp(0.12F, quadSize, targetSize);
        xd += (random.nextFloat() - 0.5F) * 0.00045F;
        zd += (random.nextFloat() - 0.5F) * 0.00045F;
        yd -= gravity;
        move(xd, yd, zd);
        xd *= friction;
        yd *= friction;
        zd *= friction;
        updateSprite();

        if (burning) {
            float progress = age / (float)Math.max(1, lifetime);
            BombardierGasFireParticle.applyFireColor(this, progress);
            setAlpha(progress < 0.78F ? 0.92F : 0.92F * (1.0F - progress) / 0.22F);
            return;
        }

        int fadeTicks = 28;
        if (age > lifetime - fadeTicks)
            setAlpha(0.72F * (lifetime - age) / fadeTicks);
    }

    @Override
    public void remove() {
        ACTIVE.remove(this);
        super.remove();
    }

    public static void igniteNearby(ClientLevel level, double x, double y, double z, double radius,
                                    SpriteSet fireSprites) {
        double radiusSquared = radius * radius;
        for (BombardierStenchParticle smoke : ACTIVE) {
            if (smoke.level != level || smoke.removed || smoke.burning) continue;
            double dx = smoke.x - x;
            double dy = smoke.y - y;
            double dz = smoke.z - z;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) smoke.ignite(fireSprites);
        }
    }

    @Override
    protected boolean isEmissive() { return burning; }

    private void ignite(SpriteSet fireSprites) {
        if (burning) return;
        this.fireSprites = fireSprites;
        burning = true;
        age = 0;
        lifetime = 28 + random.nextInt(10);
        targetSize = Math.max(quadSize, 1.0F + random.nextFloat() * 0.4F);
        xd *= 0.45D;
        yd = Math.max(0.012D, yd * 0.55D);
        zd *= 0.45D;
        setColor(0.12F, 0.42F, 1.0F);
        setAlpha(0.92F);
        updateSprite();
    }

    private void updateSprite() {
        if (burning) setSpriteFromAge(fireSprites);
        // Preserve all twelve original smoke frames; custom fire has eight frames.
        else if (isAlive()) setSprite(sprites.get(Math.min(11, age * 12 / Math.max(1, lifetime)), 11));
    }

    private static float randomSize(RandomSource random) {
        return 0.8F + random.nextFloat() * 0.55F;
    }
}
