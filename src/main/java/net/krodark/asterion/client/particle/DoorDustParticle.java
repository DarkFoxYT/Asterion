package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.RandomSource;

/** Low, brown campfire-style billows: retain world lighting and settle into the floor. */
public final class DoorDustParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float opacity;
    public DoorDustParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.sprites = sprites;
        xd = vx; yd = vy + .008; zd = vz;
        hasPhysics = true; friction = .94F; gravity = .009F;
        lifetime = 32 + random.nextInt(30);
        quadSize = .22F + random.nextFloat() * .3F;
        opacity = .32F + random.nextFloat() * .2F;
        float shade = .85F + random.nextFloat() * .15F;
        setColor(.34F * shade, .25F * shade, .16F * shade);
        setAlpha(0);
        setSpriteFromAge(sprites);
    }
    @Override public void tick() {
        super.tick();
        if (!isAlive()) return;
        quadSize += .007F;
        setSpriteFromAge(sprites);
        setAlpha(opacity * Math.min(1, age / 3F) * Math.min(1, (lifetime - age) / 18F));
    }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
