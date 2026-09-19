package net.krodark.asterion.port.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.util.RandomSource;


public final class DoorSmokeParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float opacity, growth;
    public DoorSmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                             SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, vx, vy, vz);
        setSprite(sprites.get(0, 1));
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
    public static Particle soot(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                SpriteSet sprites, RandomSource random) {
        var smoke = new DoorSmokeParticle(level, x, y, z, vx, vy, vz, sprites, random);
        smoke.setColor(.065F, .07F, .06F);
        smoke.quadSize = .55F + random.nextFloat() * .4F;
        smoke.lifetime = 55 + random.nextInt(25);
        smoke.yd = .035 + random.nextDouble() * .025;
        smoke.gravity = -.002F;
        return smoke;
    }
    @Override public void tick() {
        super.tick();
        if (!isAlive()) return;
        quadSize += growth;
        setSpriteFromAge(sprites);
        setAlpha(opacity * Math.min(1, age / 4F) * Math.min(1, (lifetime - age) / 32F));
    }
    @Override protected int getLightColor(float partialTick) {
        int light = super.getLightColor(partialTick);

        return (light & 0xFFFF0000) | Math.max(light & 0xFFFF, 112);
    }
    @Override public net.minecraft.client.particle.ParticleRenderType getRenderType() { return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
