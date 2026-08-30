package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** A dense, short-lived rubble cloud which conceals debris as it detaches. */
public final class RumbleSmokeParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float baseAlpha;

    private RumbleSmokeParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites,
                                RandomSource random) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy + 0.006D + random.nextDouble() * 0.012D;
        this.zd = vz;
        this.friction = 0.965F;
        this.gravity = 0.00001F;
        this.hasPhysics = false;
        this.lifetime = 44 + random.nextInt(30);
        this.quadSize = 1.15F + random.nextFloat() * 0.85F;
        this.baseAlpha = 0.66F + random.nextFloat() * 0.16F;
        float shade = 0.88F + random.nextFloat() * 0.18F;
        setColor(0.42F * shade, 0.37F * shade, 0.31F * shade);
        setAlpha(baseAlpha);
        setSpriteFromAge(sprites);
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, SpriteSet sprites,
                                  RandomSource random) {
        return new RumbleSmokeParticle(level, x, y, z, vx, vy, vz, sprites, random);
    }

    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        xd += (random.nextFloat() - 0.5F) * 0.0012F;
        zd += (random.nextFloat() - 0.5F) * 0.0012F;
        move(xd, yd, zd);
        xd *= friction; yd *= friction; zd *= friction;
        quadSize = Mth.lerp(0.055F, quadSize, 2.35F);
        float fade = 1.0F - Mth.clamp((age - lifetime * 0.48F) / (lifetime * 0.52F), 0.0F, 1.0F);
        setAlpha(baseAlpha * fade);
        setSpriteFromAge(sprites);
    }

    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
