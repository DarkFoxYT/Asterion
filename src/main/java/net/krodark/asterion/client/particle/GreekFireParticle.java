package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;

/** The same eight-frame green flame is used by braziers and the boss beam. */
public final class GreekFireParticle extends AnimatedEmissiveParticle {
    private boolean brazierFlame;
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
        quadSize = 1.0F + random.nextFloat() * 0.35F;
        // Preserve the supplied green edges and white-hot center without a second tint.
        setColor(1.08F, 1.04F, .80F);
        setAlpha(0.88F);
        setSpriteFromAge(sprites);
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        GreekFireParticle flame = new GreekFireParticle(level, x, y, z, vx, vy, vz, sprites, random);
        // A lit player gas cloud is Greek fire: convert its existing smoke to the
        // same dedicated flame sprites so no ordinary gas-fire frames remain.
        BombardierStenchParticle.igniteNearby(level, x, y, z, 2.25D, sprites, true);
        return flame;
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive()) {
            float ignition = Mth.clamp(age / Math.max(1F, lifetime * .24F), 0F, 1F);
            setColor(Mth.lerp(ignition,1.08F,1F),Mth.lerp(ignition,1.04F,1F),
                    Mth.lerp(ignition,.80F,1F));
            setAlpha((brazierFlame ? .78F : .88F)
                    * (brazierFlame ? Math.min(1F, age / 3F) : 1F)
                    * Math.min(1.0F, (lifetime - age) / (brazierFlame ? 8F : 5F)));
        }
    }

    @Override public float getQuadSize(float partialTick) {
        float size = super.getQuadSize(partialTick);
        // Narrow as the flame rises rather than expanding into an explosive cloud.
        return brazierFlame ? size * (1F - .45F * Math.min(1F, (age + partialTick) / lifetime)) : size;
    }

    public static Particle createBrazier(ClientLevel level, double x, double y, double z,
                                        double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        GreekFireParticle flame = new GreekFireParticle(level, x, y, z, vx, vy, vz, sprites, random);
        flame.brazierFlame = true;
        flame.quadSize = .58F + random.nextFloat() * .16F;
        flame.lifetime = 28 + random.nextInt(9);
        flame.friction = .99F;
        flame.yd = .105D + random.nextDouble() * .025D;
        flame.xd = vx * .15D;
        flame.zd = vz * .15D;
        flame.setAlpha(0);
        return flame;
    }

    public static Particle createBelch(ClientLevel level, double x, double y, double z,
                                      double vx, double vy, double vz, SpriteSet sprites, RandomSource random) {
        GreekFireParticle flame = new GreekFireParticle(level, x, y, z, vx, vy, vz, sprites, random);
        flame.quadSize = 2.4F + random.nextFloat() * .8F;
        flame.lifetime = 24 + random.nextInt(12);
        flame.yd = .07 + random.nextDouble() * .05;
        BombardierStenchParticle.igniteNearby(level, x, y, z, 3.5D, sprites, true);
        return flame;
    }
}
