package net.krodark.asterion.update.underworld.client;

import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.world.LimboCascades;
import net.krodark.asterion.update.underworld.world.LimboSeaRegions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

/** Nearby droplets and mist complement GPU ribbons; fixed spawn/lifetime and range budgets. */
public final class LimboCascadeEffects {
    public static final int PARTICLE_CAP = 160;
    public static final int SPAWN_BUDGET = 8;
    private static final ArrayDeque<Long> expires = new ArrayDeque<>();
    private static ClientLevel world;
    private static long lastTick;
    private LimboCascadeEffects() { }
    public static void initialize() { ClientTickEvents.END_CLIENT_TICK.register(LimboCascadeEffects::tick); }
    private static void tick(Minecraft client) {
        if (world != client.level) { world = client.level; expires.clear(); lastTick = Long.MIN_VALUE; }
        if (world == null || client.player == null || client.isPaused()
                || !world.dimension().equals(Asterion.LIMBO_LEVEL)) return;
        long time = world.getGameTime();
        if (time < lastTick) expires.clear();
        lastTick = time;
        while (!expires.isEmpty() && expires.peekFirst() <= time) expires.removeFirst();
        Vec3 camera = client.gameRenderer.getMainCamera().position();
        if (Math.abs(LimboCascades.boundaryDistance(camera.x,camera.z)) > 48 || camera.z < 18) return;
        var random = world.getRandom();
        Vec3 roar = null;
        for (int attempt = 0; attempt < SPAWN_BUDGET && expires.size() < PARTICLE_CAP; attempt++) {
            double x = camera.x + (random.nextDouble() - .5) * 56;
            double z = camera.z + (random.nextDouble() - .5) * 56;
            // Project to the wavy biome contour, then verify actual loaded water at both heights.
            for (int step = 0; step < 4; step++) {
                Vec3 normal = LimboCascades.outward(x,z);
                double distance = LimboCascades.boundaryDistance(x,z);
                x -= normal.x * distance; z -= normal.z * distance;
            }
            Vec3 normal = LimboCascades.outward(x,z);
            int lower = LimboCascades.waterY(x+normal.x*2,z+normal.z*2);
            int upper = LimboCascades.waterY(x-normal.x*2,z-normal.z*2);
            if (upper - lower != LimboCascades.DROP) continue;
            BlockPos high = BlockPos.containing(x-normal.x*2,upper,z-normal.z*2);
            BlockPos low = BlockPos.containing(x+normal.x*2,lower,z+normal.z*2);
            if (!world.getChunkSource().hasChunk(high.getX()>>4,high.getZ()>>4)
                    || !world.getChunkSource().hasChunk(low.getX()>>4,low.getZ()>>4)
                    || !world.getFluidState(high).is(FluidTags.WATER)
                    || !world.getFluidState(low).is(FluidTags.WATER)
                    || world.getFluidState(low.above()).is(FluidTags.WATER)) continue;
            boolean mist = attempt % 3 == 0;
            double along = mist ? 1 : random.nextDouble();
            double y = upper + 8.0/9.0 - (upper-lower)*along + (mist ? .3 : 0);
            x += normal.x * (mist ? 2 + random.nextDouble()*4 : .4);
            z += normal.z * (mist ? 2 + random.nextDouble()*4 : .4);
            if (camera.distanceToSqr(x,y,z) > 48*48 || !world.getBlockState(BlockPos.containing(x,y,z)).isAir()) continue;
            boolean fire = LimboSeaRegions.fire(x,z) > .45;
            var type = fire ? Asterion.LIMBO_EMBER : mist ? ParticleTypes.CLOUD : ParticleTypes.FALLING_WATER;
            var particle = client.particleEngine.createParticle(type,x,y,z,
                    normal.x*.045,mist || fire ? .035 : -.14-along*.2,normal.z*.045);
            if (particle == null) continue;
            particle.setLifetime(mist ? 20 : 16);
            if (mist) {
                var tint = LimboSeaRegions.fog(x,z);
                if (particle instanceof net.minecraft.client.particle.SingleQuadParticle quad)
                    quad.setColor((float)Math.min(1,tint.x*2+.2),(float)Math.min(1,tint.y*2+.2),(float)Math.min(1,tint.z*2+.2));
                particle.scale(.55F);
            } else particle.scale(fire ? .45F : .7F);
            expires.addLast(time+20);
            roar = new Vec3(x,lower+1,z);
        }
        if (roar != null && time % 70 == 0) {
            float volume = (float)Math.max(0,1-camera.distanceTo(roar)/48)*.8F;
            world.playLocalSound(roar.x,roar.y,roar.z,SoundEvents.WATER_AMBIENT,SoundSource.AMBIENT,volume,.55F,false);
        }
    }
}
