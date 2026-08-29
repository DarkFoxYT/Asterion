package net.krodark.asterion.client.particle;

import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class BombardierGasFireParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private BombardierGasFireParticle(ClientLevel level, double x, double y, double z,
                                      double velocityX, double velocityY, double velocityZ,
                                      SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites.first());
        this.sprites = sprites;
        this.xd = velocityX;
        this.yd = velocityY + 0.018D + random.nextDouble() * 0.018D;
        this.zd = velocityZ;
        this.friction = 0.94F;
        this.gravity = -0.0015F;
        this.hasPhysics = false;
        this.lifetime = 24 + random.nextInt(12);
        this.quadSize = 0.62F + random.nextFloat() * 0.42F;
        BombardierStenchParticle.igniteNearby(level, x, y, z, 2.25D);
        AsterionEmissiveParticles.spawnBeetleFire(x, y, z,
                velocityX, velocityY, velocityZ);
        setSpriteFromAge(sprites);
        updateAppearance();
        updateLight();
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  SpriteSet sprites, RandomSource random) {
        return new BombardierGasFireParticle(level, x, y, z,
                velocityX, velocityY, velocityZ, sprites, random);
    }

    @Override
    public void tick() {
        super.tick();
        if (isAlive()) {
            quadSize += 0.006F;
            setSpriteFromAge(sprites);
            updateAppearance();
            updateLight();
        }
    }

    @Override
    public void remove() {
        LedAmneticLight.removeItemGlowLight(this);
        super.remove();
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    private void updateLight() {
        float progress = progress();
        float remaining = 1.0F - progress;
        float[] color = fireGradient(progress);
        LedAmneticLight.updateItemGlowLight(this, new Vec3(x, y, z),
                color[0], color[1], color[2],
                1.0F + remaining * 6.25F, 3.2F + remaining * 4.3F, false);
    }

    private void updateAppearance() {
        float progress = progress();
        float[] color = fireGradient(progress);
        setColor(color[0], color[1], color[2]);
        setAlpha(progress < 0.78F ? 0.88F : 0.88F * (1.0F - progress) / 0.22F);
    }

    private float progress() {
        return Mth.clamp(age / (float)Math.max(1, lifetime), 0.0F, 1.0F);
    }

    private static float[] fireGradient(float progress) {
        if (progress < 0.10F) return blend(0.12F, 0.42F, 1.0F, 1.0F, 1.0F, 1.0F, progress / 0.10F);
        if (progress < 0.22F) return blend(1.0F, 1.0F, 1.0F, 1.0F, 0.92F, 0.16F,
                (progress - 0.10F) / 0.12F);
        if (progress < 0.42F) return blend(1.0F, 0.92F, 0.16F, 1.0F, 0.36F, 0.018F,
                (progress - 0.22F) / 0.20F);
        if (progress < 0.64F) return blend(1.0F, 0.36F, 0.018F, 0.70F, 0.025F, 0.006F,
                (progress - 0.42F) / 0.22F);
        return blend(0.70F, 0.025F, 0.006F, 0.025F, 0.024F, 0.022F,
                (progress - 0.64F) / 0.36F);
    }

    private static float[] blend(float r0, float g0, float b0, float r1, float g1, float b1, float amount) {
        return new float[] {Mth.lerp(amount, r0, r1), Mth.lerp(amount, g0, g1), Mth.lerp(amount, b0, b1)};
    }
}
