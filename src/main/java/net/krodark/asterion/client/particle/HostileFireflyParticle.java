package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

/** Short-lived red firefly trail driven by the authoritative server swarm. */
public final class HostileFireflyParticle extends SingleQuadParticle {
    private static final int FRAMES = 4;
    private final SpriteSet sprites;

    private HostileFireflyParticle(ClientLevel level, double x, double y, double z,
                                   double velocityX, double velocityY, double velocityZ,
                                   SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, velocityX, velocityY, velocityZ, sprites.first());
        this.sprites = sprites;
        this.xd = velocityX;
        this.yd = velocityY;
        this.zd = velocityZ;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.friction = 0.96F;
        this.lifetime = 10 + random.nextInt(5);
        this.quadSize = 0.12F;
        setColor(1.0F, 0.045F, 0.025F);
        setSprite(sprites.get(random.nextInt(FRAMES), FRAMES));
    }

    public static Particle create(ClientLevel level, double x, double y, double z,
                                  double velocityX, double velocityY, double velocityZ,
                                  SpriteSet sprites, RandomSource random) {
        return new HostileFireflyParticle(level, x, y, z,
                velocityX, velocityY, velocityZ, sprites, random);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        setColor(1.0F, 0.025F + random.nextFloat() * 0.05F, 0.015F);
        setSprite(sprites.get((age / 2) % FRAMES, FRAMES));
        if (age > lifetime - 4) setAlpha((lifetime - age) / 4.0F);
    }

    @Override protected int getLightCoords(float partialTick) { return 0xF000F0; }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
