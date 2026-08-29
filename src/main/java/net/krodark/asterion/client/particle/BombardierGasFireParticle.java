package net.krodark.asterion.client.particle;

import net.krodark.asterion.client.light.LedAmneticLight;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/** A short-lived ignition-front flame backed by an Amnetic point light. */
public final class BombardierGasFireParticle extends SingleQuadParticle {
    private BombardierGasFireParticle(ClientLevel level, double x, double y, double z,
                                      double velocityX, double velocityY, double velocityZ,
                                      SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites.first());
        this.xd = velocityX;
        this.yd = velocityY + 0.018D + random.nextDouble() * 0.018D;
        this.zd = velocityZ;
        this.friction = 0.94F;
        this.gravity = -0.0015F;
        this.hasPhysics = false;
        this.lifetime = 14 + random.nextInt(9);
        this.quadSize = 0.48F + random.nextFloat() * 0.34F;
        setColor(1.0F, 0.42F, 0.08F);
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
        if (isAlive()) updateLight();
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
        return Layer.OPAQUE;
    }

    private void updateLight() {
        float life = 1.0F - age / (float)Math.max(1, lifetime);
        LedAmneticLight.updateItemGlowLight(this, new net.minecraft.world.phys.Vec3(x, y, z),
                1.0F, 0.23F, 0.035F, 1.35F + life * 1.2F, 3.4F + life * 1.2F, false);
    }
}
