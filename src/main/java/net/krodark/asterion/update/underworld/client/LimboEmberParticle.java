package net.krodark.asterion.update.underworld.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/** A small hot cinder, with cooling colour, flicker and turbulent upward drift. */
public final class LimboEmberParticle extends SingleQuadParticle {
    private final float seed;
    public LimboEmberParticle(ClientLevel level,double x,double y,double z,
                             double vx,double vy,double vz,SpriteSet sprites) {
        super(level,x,y,z,0,0,0,sprites.first());
        xd=vx;yd=vy;zd=vz;hasPhysics=true;friction=.975F;gravity=.004F;
        lifetime=32+random.nextInt(17);seed=random.nextFloat()*30;
        quadSize=.07F+random.nextFloat()*.07F;
        roll=random.nextFloat()*6.28F;setSize(.015F,.015F);
        setColor(1F,.85F,.28F);
    }
    @Override public void tick() {
        super.tick();
        float life=1F-age/(float)lifetime;
        xd+=Math.sin(age*.27+seed)*.0018;
        zd+=Math.cos(age*.23+seed)*.0012;
        yd+=.0015;
        alpha=Math.clamp(life*2.8F,0F,1F)*(.78F+.22F*(float)Math.sin(age*.8+seed));
        setColor(1F,.15F+life*.7F,.015F+life*.23F);
        oRoll=roll;roll+=.06F*(float)Math.sin(seed);
        if(onGround)remove();
    }
    @Override public float getQuadSize(float partial) {
        float life=Math.clamp(1F-(age+partial)/lifetime,0F,1F);
        return quadSize*(.35F+.65F*life);
    }
    @Override protected int getLightCoords(float partial) { return 0xF000F0; }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
}
