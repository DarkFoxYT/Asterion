package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.PerformanceGovernor;
import net.krodark.asterion.update.underworld.world.LimboSeaRegions;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayDeque;

/** Nearby windblown embers and ash; the sea itself remains entirely shader-driven. */
public final class LimboFireAtmosphere {
    private static final ArrayDeque<Long> expires=new ArrayDeque<>();
    private static ClientLevel trackedLevel;
    private static long previousTick;
    private LimboFireAtmosphere() { }
    public static void initialize() {
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(
                Asterion.LIMBO_EMBER,sprites->(type,level,x,y,z,vx,vy,vz,random)->
                        new LimboEmberParticle(level,x,y,z,vx,vy,vz,sprites));
        ClientTickEvents.END_CLIENT_TICK.register(LimboFireAtmosphere::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{expires.clear();trackedLevel=null;});
    }
    private static void tick(Minecraft client) {
        var level=client.level;
        if(level!=trackedLevel){expires.clear();trackedLevel=level;previousTick=0;}
        if(level==null||client.player==null||!level.dimension().equals(Asterion.LIMBO_LEVEL)||client.isPaused())return;
        long time=level.getGameTime();
        if(time<previousTick)expires.clear();
        previousTick=time;
        while(!expires.isEmpty()&&expires.peekFirst()<=time)expires.removeFirst();
        if(time%2!=0)return;
        if(client.gameRenderer.getMainCamera().getFluidInCamera()!=net.minecraft.world.level.material.FogType.NONE)return;
        var camera=client.gameRenderer.getMainCamera().position();
        if(camera.y<UnderworldTerrain.WATER_Y||camera.y>UnderworldTerrain.WATER_Y+36)return;
        int quality=PerformanceGovernor.quality();
        int cap=quality==0?40:quality==1?96:160;
        int budget=quality==0?1:quality==1?3:5;
        var random=level.getRandom();
        for(int i=0;i<budget&&expires.size()<cap;i++) {
            double angle=random.nextDouble()*Math.PI*2,radius=Math.sqrt(random.nextDouble())*(quality==0?12:24);
            double x=camera.x+Math.cos(angle)*radius,z=camera.z+Math.sin(angle)*radius;
            if(random.nextDouble()>LimboSeaRegions.fire(x,z))continue;
            var pos=BlockPos.containing(x,UnderworldTerrain.WATER_Y,z);
            if(!level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4)
                    ||!level.getBlockState(pos).is(Blocks.WATER)
                    ||level.getFluidState(pos.above()).is(FluidTags.WATER)
                    ||!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty())continue;
            double surface=UnderworldTerrain.WATER_Y+8.0/9.0+UnderworldTerrain.waveHeight(x,z,time);
            boolean ash=random.nextInt(3)==0;
            double y=surface+.3+random.nextDouble()*(ash?6:2.2);
            if(!level.getBlockState(BlockPos.containing(x,y,z)).isAir())continue;
            double gust=.06+.025*Math.sin(time*.035+z*.07);
            var particle=client.particleEngine.createParticle(ash?ParticleTypes.ASH:Asterion.LIMBO_EMBER,
                    x,y,z,gust,.018+random.nextDouble()*.055,gust*.34);
            if(particle==null)continue;
            particle.scale(ash?.55F:.22F+random.nextFloat()*.2F);
            particle.setLifetime(32+random.nextInt(17));
            expires.addLast(time+48);
        }
    }
}
