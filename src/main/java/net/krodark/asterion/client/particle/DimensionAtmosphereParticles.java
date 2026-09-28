package net.krodark.asterion.client.particle;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/** Keeps shader-shaped haze suspended throughout the visible air. */
public final class DimensionAtmosphereParticles {
    private DimensionAtmosphereParticles() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(DimensionAtmosphereParticles::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null || client.isPaused()
                || client.player.isUnderWater()) return;
        boolean limbo = client.level.dimension().equals(Asterion.LIMBO_LEVEL);
        boolean labyrinth = client.level.dimension().equals(Asterion.ASTERION_LEVEL);
        if (!limbo && !labyrinth) return;

        AsterionConfig config = AsterionConfig.INSTANCE;
        if (config.ambientParticleQuality <= 0) return;
        float strength = limbo ? config.limboHazeStrength : config.labyrinthHazeStrength;
        if (strength <= 0F) return;
        RandomSource random = client.player.getRandom();
        double viewRadius = Math.max(32D,
                (client.options.getEffectiveRenderDistance() - 1) * 16D);
        float quality = config.ambientParticleQuality == 1 ? .55F : 1F;
        if (random.nextFloat() < strength * quality * .4F) {
            spawnAroundPlayer(client, random, 12D, Math.min(52D, viewRadius));
        }
        if (viewRadius > 52D && random.nextFloat() < strength * quality * .8F) {
            spawnAroundPlayer(client, random, 52D, viewRadius);
        }
    }

    private static void spawnAroundPlayer(Minecraft client, RandomSource random,
                                          double minRadius, double maxRadius) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2D;
            double radius = Math.sqrt(minRadius * minRadius
                    + random.nextDouble() * (maxRadius * maxRadius - minRadius * minRadius));
            double x = client.player.getX() + Math.cos(angle) * radius;
            double z = client.player.getZ() + Math.sin(angle) * radius;
            double y = client.player.getY() - 5D + random.nextDouble() * 11D;
            BlockPos position = BlockPos.containing(x, y, z);
            if (!client.level.hasChunk(position.getX() >> 4, position.getZ() >> 4)
                    || !client.level.getBlockState(position).isAir()
                    || !client.level.getFluidState(position).isEmpty()) continue;
            double vx = (random.nextDouble() - .5D) * .014D;
            double vy = (random.nextDouble() - .5D) * .003D;
            double vz = (random.nextDouble() - .5D) * .014D;
            client.level.addParticle(Asterion.GROUND_FOG, x, y, z, vx, vy, vz);
            return;
        }
    }
}
