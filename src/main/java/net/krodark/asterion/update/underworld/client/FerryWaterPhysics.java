package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.client.PerformanceGovernor;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

/** Budgeted, particle-engine simulated runoff for the ferry's emergence. */
public final class FerryWaterPhysics {
    private FerryWaterPhysics() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(FerryWaterPhysics::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null || client.isPaused()
                || !client.level.dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)) return;
        AABB search = client.player.getBoundingBox().inflate(72.0);
        int quality = PerformanceGovernor.quality();
        RandomSource random = client.level.getRandom();
        for (CharonsFerryEntity ferry : client.level.getEntitiesOfClass(CharonsFerryEntity.class, search,
                CharonsFerryEntity::emerging)) {
            double waterDistance = ferry.getY() - UnderworldTerrain.WATER_Y;
            if (waterDistance < -3.4 || waterDistance > 1.0) continue;
            int count = quality == 0 ? (ferry.tickCount & 1) : quality == 1 ? 3 : 6;
            double yaw = Math.toRadians(ferry.getYRot());
            for (int i = 0; i < count; i++) {
                double localX = (random.nextDouble() - .5) * 4.6;
                double localZ = (random.nextDouble() - .5) * 6.2;
                double x = ferry.getX() + localX * Math.cos(yaw) - localZ * Math.sin(yaw);
                double z = ferry.getZ() + localX * Math.sin(yaw) + localZ * Math.cos(yaw);
                double y = Math.max(UnderworldTerrain.WATER_Y + .08,
                        ferry.deckY() + .25 + random.nextDouble() * 2.4);
                double outward = Math.signum(localX) * (.025 + random.nextDouble() * .045);
                double vx = outward * Math.cos(yaw) + (random.nextDouble() - .5) * .025;
                double vz = outward * Math.sin(yaw) + (random.nextDouble() - .5) * .025;
                // Minecraft's particle engine supplies gravity, drag and block/fluid collision.
                client.level.addParticle(ParticleTypes.FALLING_WATER, x, y, z,
                        vx, -.035 - random.nextDouble() * .055, vz);
                if ((i & 1) == 0 && waterDistance > -.8)
                    client.level.addParticle(ParticleTypes.SPLASH, x, UnderworldTerrain.WATER_Y + .12, z,
                            vx * 1.8, .06 + random.nextDouble() * .08, vz * 1.8);
            }
        }
    }
}
