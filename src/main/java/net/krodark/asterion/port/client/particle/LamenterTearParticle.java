package net.krodark.asterion.port.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.SpriteSet;


public final class LamenterTearParticle extends TextureSheetParticle {
    public LamenterTearParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        setSprite(sprites.get(0, 1));
        xd = vx; yd = -.025; zd = vz;
        gravity = .22F;
        friction = .98F;
        lifetime = 48;

        quadSize = .075F;
        setSize(.02F, .02F);
        hasPhysics = true;
        setColor(.48F, .72F, .86F);
        setAlpha(.82F);
    }

    @Override public void tick() {
        super.tick();
        if (onGround) { remove(); return; }
        setAlpha(.82F * Math.min(1F, (lifetime - age) / 8F));
    }

    @Override public net.minecraft.client.particle.ParticleRenderType getRenderType() { return net.minecraft.client.particle.ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
}
