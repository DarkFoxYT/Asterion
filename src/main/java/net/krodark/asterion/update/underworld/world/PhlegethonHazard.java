package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative fire sea contact, using the rendered wave surface. */
public final class PhlegethonHazard {
    private static final Map<UUID, Long> burningPlayers = new HashMap<>();
    private PhlegethonHazard() { }
    public static void clear() { burningPlayers.clear(); }
    public static boolean recentlyBurned(ServerPlayer player) {
        Long until = burningPlayers.get(player.getUUID());
        return until != null && until >= player.level().getGameTime();
    }
    public static void forget(UUID id) { burningPlayers.remove(id); }

    public static boolean touches(Entity entity) {
        if (!entity.level().dimension().equals(Asterion.LIMBO_LEVEL)
                || entity instanceof CharonsFerryEntity || entity.isSpectator()
                || LimboSeaRegions.fire(entity.getX(), entity.getZ()) < .4
                || UnderworldWaterPhysics.sheltered(entity)) return false;
        var box = entity.getBoundingBox();
        for (int i = 0; i < 5; i++) {
            double x = i == 4 ? entity.getX() : (i % 2 == 0 ? box.minX + .05 : box.maxX - .05);
            double z = i == 4 ? entity.getZ() : (i < 2 ? box.minZ + .05 : box.maxZ - .05);
            if (LimboSeaRegions.fire(x, z) < .5) continue;
            var pos = BlockPos.containing(x, entity.getY(), z);
            if (entity.level().getFluidState(pos).is(FluidTags.WATER)) return true;
            double surface = UnderworldWaterPhysics.surfaceAt(entity.level(), new Vec3(x, entity.getY(), z), entity.level().getGameTime());
            if (Double.isFinite(surface) && box.minY < surface && box.maxY > surface - 5) return true;
        }
        return false;
    }
    public static void tick(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive() || !touches(entity)) return;
        if (entity instanceof ServerPlayer player && player.getAbilities().instabuild) return;
        if (entity.tickCount % 100 == 0) burningPlayers.entrySet().removeIf(entry -> entry.getValue() < level.getGameTime());
        entity.setRemainingFireTicks(200);
        entity.setSharedFlagOnFire(true);
        if (entity instanceof ServerPlayer player) burningPlayers.put(player.getUUID(), level.getGameTime() + 220);
        if (entity instanceof AbstractBoat) {
            entity.ejectPassengers();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    entity.getX(), entity.getY() + .4, entity.getZ(), 12, .6, .3, .6, .03);
            entity.discard();
        } else if (entity.tickCount % 10 == 0) {
            // Even fire-resistant underworld creatures cannot swim through this sea.
            entity.hurtServer(level, entity.fireImmune() ? entity.damageSources().generic() : entity.damageSources().lava(), 4);
        }
    }
}
