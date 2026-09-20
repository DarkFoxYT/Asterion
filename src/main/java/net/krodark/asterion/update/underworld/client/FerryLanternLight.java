package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.light.LedAmneticLight;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.core.particles.ParticleTypes;

/** Temporary bow fixture: one shadowed Amnetic light until the final lantern model lands. */
public final class FerryLanternLight {
    private FerryLanternLight() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null
                    || !client.level.dimension().equals(Asterion.LIMBO_LEVEL)) return;
            for (CharonsFerryEntity ferry : client.level.getEntitiesOfClass(CharonsFerryEntity.class,
                    client.player.getBoundingBox().inflate(96))) {
                var position = ferry.deckPoint(0, -2.75).add(0, 1.15, 0);
                LedAmneticLight.updateItemGlowLight(ferry, position,
                        1.0F, .93F, .78F, 3.4F, 18.0F, true);
                if (client.level.getGameTime() % 4 == 0)
                    client.level.addParticle(ParticleTypes.SMALL_FLAME,
                            position.x, position.y, position.z, 0, .002, 0);
            }
        });
    }
}
