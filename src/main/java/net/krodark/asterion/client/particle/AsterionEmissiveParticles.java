package net.krodark.asterion.client.particle;

import com.meekdev.amnetic.client.particle.ParticleMaterial;
import com.meekdev.amnetic.client.particle.Particles;
import java.util.concurrent.ThreadLocalRandom;
import net.krodark.asterion.Asterion;

/** Small HDR cores rendered beneath Minecraft's detailed beetle fire sprites. */
public final class AsterionEmissiveParticles {
    private static ParticleMaterial beetleFire;
    private static ParticleMaterial fireflyCore;

    private AsterionEmissiveParticles() {}

    public static void initialize() {
        if (beetleFire != null) return;
        beetleFire = Particles.material()
                .displayName("Asterion beetle fire core")
                .shader(Asterion.id("particle/beetle_fire_core"))
                .blend(ParticleMaterial.Blend.ADDITIVE)
                .emissive(net.krodark.asterion.client.light.AsterionEmissiveConfig.beetleFireStrength())
                .life(0.52F)
                .size(0.42F, 0.95F)
                .color(1.0F, 0.055F, 0.008F, 0.30F, 0.004F, 0.001F)
                .alpha(0.92F, 0.0F)
                .alphaInOut(0.04F, 0.42F)
                .gravity(-0.08F)
                .drag(0.72F)
                .liveCap(2048)
                .register();
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

    public static void spawnBeetleFire(double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
        if (beetleFire == null) initialize();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Particles.spawn(beetleFire, x, y, z,
                velocityX * 20.0D, velocityY * 20.0D + 0.18D, velocityZ * 20.0D,
                overrides -> overrides
                        .life(0.42F + random.nextFloat() * 0.22F)
                        .size(0.34F + random.nextFloat() * 0.16F,
                                0.78F + random.nextFloat() * 0.35F)
                        .rotation(random.nextFloat() * ((float)Math.PI * 2.0F))
                        .spin((random.nextFloat() - 0.5F) * 1.8F));
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
