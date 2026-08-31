package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public final class BombardierGasFireParticle extends AnimatedEmissiveParticle {
    private static final float[] STOPS = {0.0F, 0.10F, 0.22F, 0.42F, 0.64F, 1.0F};
    private static final int[] COLORS = {0x1F6BFF, 0xFFFFFF, 0xFFEB29, 0xFF5C05, 0xB30602, 0x060606};

    private static final int[] GREEK_COLORS = {0x1F6BFF, 0xFFFFFF, 0xD8FFAD, 0x80FF39, 0x36CC40, 0x123C25};
    private final boolean greekFire;

    private BombardierGasFireParticle(ClientLevel level, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet sprites, RandomSource random, boolean greekFire) {
        super(level, x, y, z, vx, vy, vz, sprites);
        this.greekFire = greekFire;
        xd = vx;
        yd = vy + 0.018D + random.nextDouble() * 0.018D;
        zd = vz;
        friction = 0.94F;
        gravity = -0.0015F;
        hasPhysics = false;
        lifetime = 24 + random.nextInt(12);
        quadSize = 0.62F + random.nextFloat() * 0.42F;
        BombardierStenchParticle.igniteNearby(level, x, y, z, 2.25D, sprites, greekFire);
        setSpriteFromAge(sprites);
        applyFireColor(this, 0.0F, greekFire);
        setAlpha(0.88F);
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        return new BombardierGasFireParticle(level, x, y, z, vx, vy, vz, sprites, random, false);
    }

    public static Particle createFlamethrower(ClientLevel level, double x, double y, double z,
                                            double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        return new BombardierGasFireParticle(level, x, y, z, vx, vy, vz, sprites, random, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive()) {
            quadSize += 0.006F;
            float progress = age / (float)lifetime;
            applyFireColor(this, progress, greekFire);
            setAlpha(0.88F * Math.min(1.0F, (1.0F - progress) / 0.22F));
        }
    }

    /** Shared by ignited gas clouds; no color arrays are allocated per particle tick. */
    static void applyFireColor(SingleQuadParticle particle, float progress) {
        applyFireColor(particle, progress, false);
    }
    static void applyFireColor(SingleQuadParticle particle, float progress, boolean greekFire) {
        int[] palette = greekFire ? GREEK_COLORS : COLORS;
        progress = Mth.clamp(progress, 0.0F, 1.0F);
        int stop = 0;
        while (stop < STOPS.length - 2 && progress > STOPS[stop + 1]) stop++;
        float blend = (progress - STOPS[stop]) / (STOPS[stop + 1] - STOPS[stop]);
        int start = palette[stop], end = palette[stop + 1];
        particle.setColor(Mth.lerp(blend, (start >> 16) & 255, (end >> 16) & 255) / 255.0F,
                Mth.lerp(blend, (start >> 8) & 255, (end >> 8) & 255) / 255.0F,
                Mth.lerp(blend, start & 255, end & 255) / 255.0F);
    }
}
