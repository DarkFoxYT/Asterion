package net.krodark.asterion.client.particle;

import com.meekdev.amnetic.client.particle.ParticleMaterial;
import com.meekdev.amnetic.client.particle.Particles;
import net.krodark.asterion.Asterion;

/** Short-lived emissive cores for fireflies. Animated fire uses the shared atlas batch. */
public final class AsterionEmissiveParticles {
    private static ParticleMaterial fireflyCore;

    private AsterionEmissiveParticles() {}

    public static void initialize() {
        if (fireflyCore != null) return;
        fireflyCore = Particles.material()
                .displayName("Asterion firefly core")
                .shader(Asterion.id("particle/firefly_core"))
                .blend(ParticleMaterial.Blend.ADDITIVE)
                .emissive(3.2F)
                .life(0.11F)
                .size(0.10F, 0.055F)
                .color(1.0F, 0.72F, 0.12F, 0.95F, 0.34F, 0.025F)
                .alpha(0.88F, 0.0F)
                .alphaInOut(0.02F, 0.7F)
                .gravity(0.0F)
                .drag(0.82F)
                .liveCap(1024)
                .register();
    }

    public static void spawnFireflyCore(double x, double y, double z,
                                        double velocityX, double velocityY, double velocityZ,
                                        float pulse) {
        if (fireflyCore == null) initialize();
        Particles.spawn(fireflyCore, x, y, z,
                velocityX * 8.0D, velocityY * 8.0D, velocityZ * 8.0D,
                overrides -> overrides
                        .life(0.08F + pulse * 0.055F)
                        .size(0.065F + pulse * 0.045F, 0.035F)
                        .alpha(0.52F + pulse * 0.38F, 0.0F));
    }
}
