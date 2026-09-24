package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.PerformanceGovernor;
import net.krodark.asterion.event.LimboTempest;
import net.krodark.asterion.event.LimboWhirlpool;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.world.FerryHull;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.krodark.asterion.update.underworld.world.UnderworldWaves;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/** Bounded, velocity-driven hull spray, water entry splashes, and submerged bubbles. */
public final class FerryWaterPhysics {
    private record Contact(Vec3 position,double[] depth,double[] shore) { }
    private static final Map<Integer,Contact> CONTACTS=new HashMap<>();
    private static ClientLevel world;
    private static double previousFeet=Double.NaN;
    private static double previousSurface=Double.NaN;
    private FerryWaterPhysics() { }
    public static void initialize() { ClientTickEvents.END_CLIENT_TICK.register(FerryWaterPhysics::tick); }
    private static void tick(Minecraft client) {
        if(client.level!=world) { world=client.level;CONTACTS.clear();previousFeet=Double.NaN;previousSurface=Double.NaN; }
        if(world==null || client.player==null || client.isPaused() || !world.dimension().equals(Asterion.LIMBO_LEVEL))return;
        int quality=PerformanceGovernor.quality(), budget=quality==0?5:quality==1?14:24;
        long time=world.getGameTime();var random=world.getRandom();
        for(var boat:world.getEntitiesOfClass(CharonsFerryEntity.class,client.player.getBoundingBox().inflate(64))) {
            double[] depths=new double[4];Contact old=CONTACTS.get(boat.getId());
            Vec3 velocity=old==null?Vec3.ZERO:boat.position().subtract(old.position);
            if(velocity.lengthSqr()>4)old=null;
            boolean refresh=old==null || time%20==0;
            double[] shore=refresh?new double[4]:old.shore;
            for(int i=0;i<4;i++) {
                double side=(i&1)==0?-1:1;
                Vec3 hull=boat.position().add(FerryHull.world(new Vec3(side*.75,.24,(i&2)==0?-1.6:2.1),
                        boat.getYRot(),boat.rockingPitch(1),boat.rockingRoll(1)));
                if(refresh)shore[i]=net.krodark.asterion.update.underworld.world.WaterShoreline.sample(world,
                        (int)Math.floor(hull.x),(int)Math.floor(hull.z));
                double water=UnderworldTerrain.WATER_Y+8.0/9.0
                        +UnderworldTerrain.waveHeight(hull.x,hull.z,time)*shore[i];
                depths[i]=water-hull.y;
                double entering=old==null?0:Math.max(0,depths[i]-old.depth[i]);
                double energy=Math.min(1,entering*8+velocity.horizontalDistance()*4);
                if(depths[i]<-.04 || depths[i]>.85 || energy<.04)continue;
                if (budget <= 0) continue;
                int count=Math.min(budget,1+(int)(energy*(quality+1)*2));budget-=count;
                double yaw=Math.toRadians(boat.getYRot());
                for(int n=0;n<count;n++) {
                    double vx=velocity.x+side*Math.cos(yaw)*(.02+energy*.08);
                    double vz=velocity.z+side*Math.sin(yaw)*(.02+energy*.08);
                    double x=hull.x+(random.nextDouble()-.5)*.3,z=hull.z+(random.nextDouble()-.5)*.3;
                    world.addParticle(ParticleTypes.SPLASH,x,water+.03,z,vx,.03+energy*.16,vz);
                    if(entering>.025 && quality>0)world.addParticle(ParticleTypes.FALLING_WATER,
                            x,water+.10+energy*.25,z,vx,.07+energy*.14,vz);
                }
            }
            CONTACTS.put(boat.getId(),new Contact(boat.position(),depths,shore));
        }
        CONTACTS.keySet().removeIf(id->world.getEntity(id)==null);
        if (time % 3 == 0) {
            shoreBreakers(client, quality, time);
            whirlpoolSpray(client, quality, time);
        }
        var player=client.player;
        net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics.alignSurface(player, time);
        double water=net.krodark.asterion.update.underworld.world.UnderworldWaterPhysics.surfaceAt(player,time),feet=player.getY();
        if(Double.isFinite(water) && Double.isFinite(previousFeet) && Double.isFinite(previousSurface)
                && previousFeet>previousSurface+.08 && feet<=water+.08 && player.getDeltaMovement().y<-.06
                && CharonsFerryEntity.supporting(player)==null) {
            int count=Math.min(quality==0?4:quality==1?9:18,
                    6+(int)(Math.abs(player.getDeltaMovement().y)*12));
            for(int i=0;i<count;i++) {
                double a=i*Math.PI*2/count;
                world.addParticle(ParticleTypes.SPLASH,player.getX()+Math.cos(a)*.35,water+.05,
                        player.getZ()+Math.sin(a)*.35,Math.cos(a)*.08,.10,Math.sin(a)*.08);
            }
        }
        if(Double.isFinite(water) && player.getEyeY()<water-.15 && time%3==0 && quality>0) {
            world.addParticle(ParticleTypes.BUBBLE,player.getX()+(random.nextDouble()-.5)*1.5,
                    player.getY()+random.nextDouble()*1.6,player.getZ()+(random.nextDouble()-.5)*1.5,
                    player.getDeltaMovement().x*.2,.02,player.getDeltaMovement().z*.2);
        }
        previousFeet=feet;
        previousSurface=water;
    }

    private static void shoreBreakers(Minecraft client, int quality, long time) {
        if (client.player.getZ() < 12) return;
        double storm = LimboTempest.strength(time);
        int attempts = quality == 0 ? 5 : quality == 1 ? 12 : 20;
        if (storm > .25) attempts += quality == 0 ? 2 : 8;
        int emitted = 0, limit = quality == 0 ? 2 : quality == 1 ? 5 : 9;
        var random = world.getRandom();
        for (int i = 0; i < attempts && emitted < limit; i++) {
            int x = client.player.getBlockX() + random.nextInt(49) - 24;
            int z = client.player.getBlockZ() + random.nextInt(49) - 24;
            if (!world.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
            BlockPos water = new BlockPos(x, UnderworldTerrain.WATER_Y, z);
            if (!world.getFluidState(water).is(FluidTags.WATER)
                    || world.getFluidState(water.above()).is(FluidTags.WATER)) continue;
            Direction rock = null;
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos edge = water.relative(side);
                if (world.getBlockState(edge).isSolidRender()
                        || world.getBlockState(edge.above()).isSolidRender()) { rock = side; break; }
            }
            if (rock == null) continue;
            var wave = UnderworldWaves.sample(x + .5, z + .5, time);
            double energy = Math.abs(wave.height()) * .24 + Math.abs(wave.curvature()) * 4 + storm * .6;
            if (energy < .18 || random.nextDouble() > Math.min(.9, energy)) continue;
            double surface = UnderworldTerrain.WATER_Y + 8.0 / 9.0;
            double vx = -rock.getStepX() * (.035 + energy * .09);
            double vz = -rock.getStepZ() * (.035 + energy * .09);
            world.addParticle(ParticleTypes.SPLASH, x + .5, surface + .03, z + .5,
                    vx, .06 + energy * .16, vz);
            if (quality > 0 && (storm > .4 || random.nextBoolean()))
                world.addParticle(storm > .55 ? ParticleTypes.FALLING_WATER : ParticleTypes.BUBBLE_POP,
                        x + .5 + random.nextDouble() * .3 - .15, surface + .12,
                        z + .5 + random.nextDouble() * .3 - .15, vx * .55, .05 + energy * .1, vz * .55);
            emitted++;
        }
    }

    private static void whirlpoolSpray(Minecraft client, int quality, long time) {
        double strength = LimboWhirlpool.strength(time);
        if (strength < .05 || client.player.distanceToSqr(LimboWhirlpool.X,
                client.player.getY(), LimboWhirlpool.Z) > 70 * 70) return;
        var random = world.getRandom();
        int count = quality == 0 ? 1 : quality == 1 ? 3 : 5;
        for (int i = 0; i < count; i++) {
            double radius = 12 + random.nextDouble() * 34;
            double angle = random.nextDouble() * Math.PI * 2;
            double x = LimboWhirlpool.X + Math.cos(angle) * radius;
            double z = LimboWhirlpool.Z + Math.sin(angle) * radius;
            double y = UnderworldTerrain.WATER_Y + 8.0 / 9.0
                    + UnderworldTerrain.waveHeight(x, z, time);
            double speed = (.045 + (46 - radius) * .0015) * strength;
            world.addParticle(i % 3 == 0 ? ParticleTypes.BUBBLE_POP : ParticleTypes.SPLASH,
                    x, y + .06, z, -Math.sin(angle) * speed,
                    .04 + random.nextDouble() * .08 * strength,
                    Math.cos(angle) * speed);
        }
    }
}
