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
        float remaining = 1.0F - progress();
        LedAmneticLight.updateItemGlowLight(this, new Vec3(x, y, z),
                1.0F, 0.23F, 0.035F,
                0.12F + remaining * 2.35F, 2.2F + remaining * 2.4F, false);
    }

    private void updateAppearance() {
        float progress = progress();
        if (progress < 0.38F) {
            float blend = progress / 0.38F;
            setColor(Mth.lerp(blend, 1.0F, 0.92F), Mth.lerp(blend, 0.72F, 0.16F),
                    Mth.lerp(blend, 0.12F, 0.025F));
        } else {
            float blend = (progress - 0.38F) / 0.62F;
            setColor(Mth.lerp(blend, 0.92F, 0.035F), Mth.lerp(blend, 0.16F, 0.032F),
                    Mth.lerp(blend, 0.025F, 0.028F));
        }
        setAlpha(progress < 0.78F ? 0.88F : 0.88F * (1.0F - progress) / 0.22F);
    }

    private float progress() {
        return Mth.clamp(age / (float)Math.max(1, lifetime), 0.0F, 1.0F);
    }
}
