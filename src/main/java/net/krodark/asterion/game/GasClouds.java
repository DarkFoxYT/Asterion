package net.krodark.asterion.game;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.effect.GreekFireBurn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import java.util.*;

/** Shared finite gas hazards for spewers, cursed braziers and player weapons. */
public final class GasClouds {
    private static final Map<ServerLevel, List<Cloud>> CLOUDS = new IdentityHashMap<>();
    private GasClouds() { }
    public static void emit(ServerLevel level, Vec3 origin, Vec3 velocity, UUID owner) {
        emit(level, origin, velocity, owner, false, false);
    }
    public static void emitFlamethrower(ServerLevel level, Vec3 origin, Vec3 velocity, UUID owner) {
        emit(level, origin, velocity, owner, true, false);
    }
    public static void emitCompactFlamethrower(ServerLevel level, Vec3 origin, Vec3 velocity, UUID owner) {
        emit(level, origin, velocity, owner, true, true);
    }
    private static void emit(ServerLevel level, Vec3 origin, Vec3 velocity, UUID owner,
                             boolean flamethrower, boolean compact) {
        var clouds = CLOUDS.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (clouds.size() >= 256 || clouds.stream().filter(c -> Objects.equals(c.owner, owner)).count() >= 64) return;
        clouds.add(new Cloud(origin, velocity, owner, flamethrower, compact));
    }
    public static boolean ignite(ServerLevel level, Vec3 origin, UUID owner) {
        boolean ignited = false;
        for (var cloud : CLOUDS.getOrDefault(level, List.of()))
            if (cloud.burn == 0 && Objects.equals(cloud.owner, owner) && cloud.pos.distanceToSqr(origin) < 4 * 4
                    && visible(level, origin, cloud.pos)) { cloud.burn = 60; ignited = true; }
        if (ignited) level.playSound(null, origin.x, origin.y, origin.z,
                net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.PLAYERS,
                .9F, .92F + level.getRandom().nextFloat() * .16F);
        return ignited;
    }
    private static boolean visible(ServerLevel level, Vec3 start, Vec3 end) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }
    public static void tick(MinecraftServer server) {
        for (var entry : CLOUDS.entrySet()) {
            var level = entry.getKey(); var clouds = entry.getValue();
            Set<UUID> hit = new HashSet<>();
            // Delayed propagation makes ignition visibly travel away from the weapon.
            if (level.getGameTime() % 3 == 0) {
                Vec3 spreadSound = null;
                Map<Long,List<Vec3>> burning=new HashMap<>();
                for(var cloud:clouds)if(cloud.burn>0)
                    burning.computeIfAbsent(cell(cloud.pos),ignored->new ArrayList<>()).add(cloud.pos);
                for(var cloud:clouds)if(cloud.burn==0) {
                    int cx=Mth.floor(cloud.pos.x/3),cy=Mth.floor(cloud.pos.y/3),cz=Mth.floor(cloud.pos.z/3);
                    search:for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)for(int dz=-1;dz<=1;dz++)
                        for(Vec3 fire:burning.getOrDefault(cell(cx+dx,cy+dy,cz+dz),List.of()))
                            if(cloud.pos.distanceToSqr(fire)<6.25 && visible(level,fire,cloud.pos)) {
                                cloud.burn=60; spreadSound=cloud.pos; break search;
                            }
                }
                if (spreadSound != null) level.playSound(null, spreadSound.x, spreadSound.y, spreadSound.z,
                        net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS,
                        .45F, 1.05F + level.getRandom().nextFloat() * .2F);
            }
            for (var iterator = clouds.iterator(); iterator.hasNext();) {
                var cloud = iterator.next();
                var block = net.minecraft.core.BlockPos.containing(cloud.pos);
                if (++cloud.age > 200 || cloud.burn == 1 || !level.getChunkSource().hasChunk(block.getX() >> 4, block.getZ() >> 4)
                        || !level.getFluidState(block).isEmpty()) { iterator.remove(); continue; }
                if (cloud.burn > 0) cloud.burn--;
                else {
                    var state = level.getBlockState(block);
                    if (state.is(net.minecraft.tags.BlockTags.FIRE)
                            || (state.is(Asterion.GREEK_FIRE_FLOOR_TORCH)
                            || state.is(Asterion.GREEK_FIRE_WALL_TORCH))
                            && state.getValue(net.krodark.asterion.block.GreekFireTorchBlock.LIT)) cloud.burn = 60;
                }
                Vec3 next = cloud.pos.add(cloud.velocity);
                if(cloud.velocity.lengthSqr()>1.0e-8) {
                    if (visible(level, cloud.pos, next)) cloud.pos = next;
                    else cloud.velocity = Vec3.ZERO;
                }
                cloud.velocity = cloud.velocity.multiply(.975, .97, .975).add(0, -.001, 0);
                if (cloud.age % 4 == 0) level.sendParticles(cloud.flamethrower
                        ? (cloud.burn > 0 ? Asterion.GREEK_FIRE : Asterion.FLAMETHROWER_GAS)
                        : (cloud.burn > 0 ? Asterion.BOMBARDIER_GAS_FIRE : Asterion.BOMBARDIER_STENCH),
                        cloud.pos.x, cloud.pos.y, cloud.pos.z, cloud.compact ? 1 : 3,
                        cloud.compact ? .07 : .38, cloud.compact ? .05 : .25,
                        cloud.compact ? .07 : .38, cloud.compact ? .002 : .005);
                if (cloud.age % (cloud.burn>0 ? 10 : 20) != 0) continue;
                for (var victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(cloud.pos, cloud.pos).inflate(1.2))) {
                    if (!victim.isAlive() || victim.getUUID().equals(cloud.owner) || hit.contains(victim.getUUID())
                            || victim instanceof ServerPlayer player && (player.isCreative() || player.isSpectator())
                            || !visible(level, cloud.pos, victim.getBoundingBox().getCenter())) continue;
                    if (cloud.burn == 0 && victim.isOnFire()) { cloud.burn = 60; continue; }
                    if (cloud.burn == 0) continue;
                    if (cloud.owner != null && level.getEntity(cloud.owner) instanceof ServerPlayer attacker
                            && victim instanceof ServerPlayer player && !attacker.canHarmPlayer(player)) continue;
                    hit.add(victim.getUUID());
                    victim.hurtServer(level, level.damageSources().inFire(), 5);
                    if (cloud.flamethrower) GreekFireBurn.ignite(victim, 4);
                    else victim.igniteForSeconds(4);
                }
            }
        }
        CLOUDS.values().removeIf(List::isEmpty);
    }
    public static void clear() { CLOUDS.clear(); }
    public static void clearOwner(ServerLevel level, UUID owner) {
        var clouds = CLOUDS.get(level);
        if (clouds != null) clouds.removeIf(cloud -> owner.equals(cloud.owner));
    }
    private static long cell(Vec3 pos){return cell(Mth.floor(pos.x/3),Mth.floor(pos.y/3),Mth.floor(pos.z/3));}
    private static long cell(int x,int y,int z){
        return ((long)x&0x1fffffL)<<42|((long)y&0x1fffffL)<<21|((long)z&0x1fffffL);
    }
    private static final class Cloud {
        Vec3 pos, velocity; final UUID owner; final boolean flamethrower, compact; int age, burn;
        Cloud(Vec3 pos, Vec3 velocity, UUID owner, boolean flamethrower, boolean compact) {
            this.pos = pos; this.velocity = velocity; this.owner = owner;
            this.flamethrower = flamethrower; this.compact = compact;
        }
    }
}
