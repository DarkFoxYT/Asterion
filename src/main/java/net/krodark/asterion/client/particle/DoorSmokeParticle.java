package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.RandomSource;

/** Expanding warm dust billows; translucent, world-lit and buoyant rather than floor grit. */
public final class DoorSmokeParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float opacity, growth;
    public DoorSmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, vx, vy, vz, sprites.first());
        this.sprites = sprites;
        xd = vx; yd = vy; zd = vz;
        hasPhysics = true; friction = .96F; gravity = -.006F;
        lifetime = 55 + random.nextInt(35);
        quadSize = 1.45F + random.nextFloat() * .85F;
        growth = .012F + random.nextFloat() * .012F;
        opacity = .48F + random.nextFloat() * .18F;
        float shade = .8F + random.nextFloat() * .2F;
        setColor(.62F * shade, .56F * shade, .46F * shade);
        setAlpha(0); setSpriteFromAge(sprites);
    }
    @Override public void tick() {
        super.tick();
        if (!isAlive()) return;
        quadSize += growth;
        setSpriteFromAge(sprites);
        setAlpha(opacity * Math.min(1, age / 4F) * Math.min(1, (lifetime - age) / 32F));
    }
    @Override protected int getLightCoords(float partialTick) {
        int light = super.getLightCoords(partialTick);
        // A little bounced light keeps the cloud readable in the unlit staging room.
        return (light & 0xFFFF0000) | Math.max(light & 0xFFFF, 112);
    }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
