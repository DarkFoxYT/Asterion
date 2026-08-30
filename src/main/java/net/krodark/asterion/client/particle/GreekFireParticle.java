package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/** The same eight-frame green flame is used by braziers and the boss beam. */
public final class GreekFireParticle extends AnimatedEmissiveParticle {
    private GreekFireParticle(ClientLevel level, double x, double y, double z,
                              double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, vx, vy, vz, sprites);
        xd = vx;
        yd = vy + 0.012D;
        zd = vz;
        friction = 0.94F;
        gravity = -0.001F;
        hasPhysics = false;
        lifetime = 16 + random.nextInt(9);
        quadSize = 0.38F + random.nextFloat() * 0.14F;
        // Preserve the supplied green edges and white-hot center without a second tint.
        setAlpha(0.88F);
        setSpriteFromAge(sprites);
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        return new GreekFireParticle(level, x, y, z, vx, vy, vz, sprites, random);
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive()) setAlpha(0.88F * Math.min(1.0F, (lifetime - age) / 5.0F));
    }

    public static Particle createBrazier(ClientLevel level, double x, double y, double z,
                                        double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        GreekFireParticle flame = new GreekFireParticle(level, x, y, z, vx, vy, vz, sprites, random);
        flame.quadSize *= 2.3F;
        flame.lifetime += 8;
        return flame;
    }
}
