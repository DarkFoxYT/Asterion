package net.krodark.asterion.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/** Fine, unlit water droplets small enough to keep the two tracks of each eye distinct. */
public final class LamenterTearParticle extends SingleQuadParticle {
    public LamenterTearParticle(ClientLevel level, double x, double y, double z,
                               double vx, double vz, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0, sprites.first());
        xd = vx; yd = -.025; zd = vz;
        gravity = .22F;
        friction = .98F;
        lifetime = 48;
        // drip_fall's visible mark is only 2 of its 8 pixels wide (a quarter of the quad).
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

    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
