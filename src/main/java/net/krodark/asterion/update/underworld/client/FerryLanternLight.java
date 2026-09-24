package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Warm, local deck lighting; avoid a sea-sized light and expensive shadows on low quality. */
public final class FerryLanternLight {
    private FerryLanternLight() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null
                    || !client.level.dimension().equals(Asterion.LIMBO_LEVEL)) return;
            for (CharonsFerryEntity ferry : client.level.getEntitiesOfClass(CharonsFerryEntity.class,
                    client.player.getBoundingBox().inflate(96))) {
                var position = position(ferry);
                LedAmneticLight.updateItemGlowLight(ferry, position,
                        1.0F, .86F, .65F, 1.8F, 8.0F,
                        net.krodark.asterion.client.PerformanceGovernor.quality() > 0);
                if (client.level.getGameTime() % 4 == 0)
                    client.level.addParticle(ParticleTypes.SMALL_FLAME,
                            position.x, position.y, position.z, 0, .002, 0);
            }
        });
    }
    public static Vec3 position(CharonsFerryEntity ferry) {
        return ferry.position().add(net.krodark.asterion.update.underworld.world.FerryHull.world(
                new Vec3(0, net.krodark.asterion.update.underworld.world.FerryHull.DECK + 1.15, -2.75),
                ferry.getYRot(), ferry.rockingPitch(1), ferry.rockingRoll(1)));
    }
}
