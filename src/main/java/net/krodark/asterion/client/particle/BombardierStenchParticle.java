package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.HashSet;
import java.util.Set;

/** Campfire-style smoke that billows between randomly selected, player-sized diameters. */
public final class BombardierStenchParticle extends SingleQuadParticle {
    private static final Set<BombardierStenchParticle> ACTIVE = new HashSet<>();
    private static final float RED = 0x78 / 255.0F;
    private static final float GREEN = 0x7A / 255.0F;
    private static final float BLUE = 0x4C / 255.0F;
    private final SpriteSet sprites;
    private float targetSize;
    private int sizeChangeTicks;
    private boolean burning;

    private BombardierStenchParticle(ClientLevel level, double x, double y, double z,
                                     double velocityX, double velocityY, double velocityZ,
                                     SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites.first());
        this.sprites = sprites;
        this.xd = velocityX;
        this.yd = velocityY + random.nextFloat() * 0.012F;
        this.zd = velocityZ;
        this.gravity = 0.000003F;
        this.friction = 0.985F;
        this.hasPhysics = false;
        this.lifetime = 90 + random.nextInt(50);
        this.quadSize = 1.8F;
        this.targetSize = randomSize(random);
        this.sizeChangeTicks = 7 + random.nextInt(11);
        setColor(RED, GREEN, BLUE);
        setAlpha(0.9F);
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
        setSpriteFromAge(sprites);

        if (burning) {
            float progress = age / (float)Math.max(1, lifetime);
            if (progress < 0.45F) {
                float blend = progress / 0.45F;
                setColor(lerp(1.0F, 0.48F, blend), lerp(0.10F, 0.018F, blend),
                        lerp(0.015F, 0.008F, blend));
            } else {
                float blend = (progress - 0.45F) / 0.55F;
                setColor(lerp(0.48F, 0.025F, blend), lerp(0.018F, 0.024F, blend),
                        lerp(0.008F, 0.022F, blend));
            }
            setAlpha(progress < 0.78F ? 0.92F : 0.92F * (1.0F - progress) / 0.22F);
            return;
        }

        int fadeTicks = 28;
        if (age > lifetime - fadeTicks)
            setAlpha(0.9F * (lifetime - age) / fadeTicks);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return burning ? 0xF000F0 : super.getLightCoords(partialTick);
    }

    @Override
    public void remove() {
        ACTIVE.remove(this);
        super.remove();
    }

    public static void igniteNearby(ClientLevel level, double x, double y, double z, double radius) {
        double radiusSquared = radius * radius;
        for (BombardierStenchParticle smoke : Set.copyOf(ACTIVE)) {
            if (smoke.level != level || smoke.removed) continue;
            double dx = smoke.x - x;
            double dy = smoke.y - y;
            double dz = smoke.z - z;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) smoke.ignite();
        }
    }

    private void ignite() {
        if (burning) return;
        burning = true;
        age = 0;
        lifetime = 28 + random.nextInt(10);
        targetSize = Math.max(quadSize, 1.45F + random.nextFloat() * 0.9F);
        xd *= 0.45D;
        yd = Math.max(0.012D, yd * 0.55D);
        zd *= 0.45D;
        setColor(1.0F, 0.10F, 0.015F);
        setAlpha(0.92F);
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }

    private static float randomSize(RandomSource random) {
        return 1.15F + random.nextFloat() * 1.45F;
    }
}
