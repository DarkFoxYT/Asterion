package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Sparse world particles complement the depth-aware volumetric atmosphere. */
public final class PortAtmosphere {
    private PortAtmosphere() {}

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                || !net.krodark.asterion.AsterionConfig.INSTANCE.dustyAirEnabled
                || client.level.getGameTime() % 3L != 0L) return;
        RandomSource random = client.level.random;
        for (int i = 0; i < 2; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 8.0D + random.nextDouble() * 24.0D;
            double x = client.player.getX() + Math.cos(angle) * distance;
            double y = client.player.getEyeY() - 3.0D + random.nextDouble() * 7.0D;
            double z = client.player.getZ() + Math.sin(angle) * distance;
            client.level.addParticle(i == 0 ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH,
                    x, y, z, (random.nextDouble() - .5D) * .006D,
                    .002D + random.nextDouble() * .006D, (random.nextDouble() - .5D) * .006D);
        }
    }
}
