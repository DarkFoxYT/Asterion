package net.krodark.asterion.update.underworld.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics;

/** Vanilla rain sprites with actual wind velocity and impact at the displaced sea. */
public final class LimboRainParticle extends SingleQuadParticle {
    private double surface = Double.NaN;
    private static ClientLevel sampledLevel;
    private static long sampledTick;
    private static final java.util.Map<Long,Double> surfaces=new java.util.HashMap<>();
    public static void clearCache() { sampledLevel=null;surfaces.clear(); }
    public LimboRainParticle(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites) {
        super(level,x,y,z,0,0,0,sprites.first());
        xd=vx; yd=vy < -.1 ? vy : -.85; zd=vz;
        gravity=.025F; friction=.995F; lifetime=38; hasPhysics=true;
        quadSize=.055F; setSize(.02F,.02F); setColor(.65F,.72F,.78F);
    }
    @Override public void tick() {
        double oldY=y;
        super.tick();
        if(age%4==1 && y < UnderworldTerrain.WATER_Y+7) {
            // Only sample the expensive shoreline attenuation close to impact.
            long tick=level.getGameTime();
            if(sampledLevel!=level || tick-sampledTick>=4 || tick<sampledTick) {
                surfaces.clear();sampledLevel=level;sampledTick=tick;
            }
            int cellX=Math.floorDiv((int)Math.floor(x),2)*2,cellZ=Math.floorDiv((int)Math.floor(z),2)*2;
            long key=BlockPos.asLong(cellX,0,cellZ);
            surface=surfaces.computeIfAbsent(key,ignored -> UnderworldWaterPhysics.surfaceAt(level,
                    new net.minecraft.world.phys.Vec3(cellX+1,y,cellZ+1),tick));
        }
        if(Double.isFinite(surface) && oldY>=surface && y<=surface) {
            if(random.nextInt(5)==0) level.addParticle(ParticleTypes.SPLASH,x,surface+.015,z,0,.03,0);
            remove();
        } else if(onGround) remove();
    }
    @Override protected Layer getLayer() { return Layer.TRANSLUCENT; }
    @Override public void extract(net.minecraft.client.renderer.state.level.QuadParticleRenderState state,
                                  net.minecraft.client.Camera camera,float partialTick) {
        super.extract(state,camera,partialTick);
        // A short motion trail, aligned with the actual wind rather than camera vertical.
        var rotation=new org.joml.Quaternionf(camera.rotation());
        float px=(float)(xo+(x-xo)*partialTick-camera.position().x);
        float py=(float)(yo+(y-yo)*partialTick-camera.position().y);
        float pz=(float)(zo+(z-zo)*partialTick-camera.position().z);
        for(int i=1;i<=3;i++) extractRotatedQuad(state,rotation,
                px-(float)xd*i*.055F,py-(float)yd*i*.055F,pz-(float)zd*i*.055F,partialTick);
    }
}
