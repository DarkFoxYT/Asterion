package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Campfire-style smoke that billows between randomly selected, player-sized diameters. */
public final class BombardierStenchParticle extends SingleQuadParticle {
    private static final float RED = 0x78 / 255.0F;
    private static final float GREEN = 0x7A / 255.0F;
    private static final float BLUE = 0x4C / 255.0F;
    private final SpriteSet sprites;
    private float targetSize;
    private int sizeChangeTicks;

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

        if (--sizeChangeTicks <= 0) {
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

        int fadeTicks = 28;
        if (age > lifetime - fadeTicks)
            setAlpha(0.9F * (lifetime - age) / fadeTicks);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    private static float randomSize(RandomSource random) {
        return 1.15F + random.nextFloat() * 1.45F;
    }
}
