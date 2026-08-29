package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Fine, heavy-looking dust shaken loose from the maze's ancient masonry. */
public final class AncientWallDustParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float baseAlpha;
    private float rollVelocity;

    private AncientWallDustParticle(ClientLevel level, double x, double y, double z,
                                    double velocityX, double velocityY, double velocityZ,
                                    SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites.first());
        this.sprites = sprites;
        this.xd = velocityX;
        this.yd = velocityY - 0.008D - random.nextDouble() * 0.012D;
        this.zd = velocityZ;
        this.gravity = 0.055F + random.nextFloat() * 0.055F;
        this.friction = 0.965F;
        this.hasPhysics = true;
        this.lifetime = 48 + random.nextInt(54);
        this.quadSize = 0.055F + random.nextFloat() * random.nextFloat() * 0.19F;
        this.baseAlpha = 0.44F + random.nextFloat() * 0.34F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.rollVelocity = (random.nextFloat() - 0.5F) * 0.055F;

        float shade = 0.78F + random.nextFloat() * 0.28F;
        setColor((0.38F + random.nextFloat() * 0.10F) * shade,
                (0.34F + random.nextFloat() * 0.085F) * shade,
                (0.29F + random.nextFloat() * 0.075F) * shade);
        setAlpha(baseAlpha);
        setSpriteFromAge(sprites);
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  SpriteSet sprites, RandomSource random) {
        return new AncientWallDustParticle(level, x, y, z,
                velocityX, velocityY, velocityZ, sprites, random);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        oRoll = roll;
        roll += rollVelocity;
        rollVelocity *= 0.985F;
        xd += (random.nextDouble() - 0.5D) * 0.00055D;
        zd += (random.nextDouble() - 0.5D) * 0.00055D;
        setSpriteFromAge(sprites);

        float life = age / (float) Math.max(1, lifetime);
        float appear = Mth.clamp(age / 5.0F, 0.0F, 1.0F);
        float fade = Mth.clamp((1.0F - life) / 0.30F, 0.0F, 1.0F);
        if (onGround) {
            fade *= 0.72F;
            quadSize *= 1.012F;
            rollVelocity *= 0.72F;
        }
        setAlpha(baseAlpha * appear * fade);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }
}
