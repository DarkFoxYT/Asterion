package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurAxeEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** A mono, attenuated loop following the thrown axe, never the listener. */
public final class AxeFlightAudio {
    private static final java.util.Map<MinotaurAxeEntity, Flight> playing = new java.util.HashMap<>();
    private AxeFlightAudio() {}

    public static void tick(Minecraft client) {
        playing.entrySet().removeIf(entry -> {
            if (entry.getKey().isRemoved() || entry.getKey().level() != client.level || client.player == null
                    || entry.getKey().distanceToSqr(client.player) > 48 * 48) {
                entry.getValue().finish();
                return true;
            }
            return false;
        });
        if (client.level == null || client.player == null) return;
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof MinotaurAxeEntity axe) || axe.isSword() || axe.throwerId() < 0
                    || axe.distanceToSqr(client.player) > 40 * 40) continue;
            Flight old = playing.get(axe);
            if (old != null && !old.isStopped()) continue;
            // A stopped axe stays silent. A retrieved/rethrown axe may start again.
            if (old != null && axe.position().distanceToSqr(old.previous) < .0025) continue;
            var loop = new Flight(axe);
            playing.put(axe, loop);
            client.getSoundManager().play(loop);
        }
    }

    private static final class Flight extends AbstractTickableSoundInstance {
        private final MinotaurAxeEntity axe;
        private Vec3 previous;
        private int quietTicks;
        Flight(MinotaurAxeEntity axe) {
            super(Asterion.MINOTAUR_AXE_FLIGHT_LOOP, SoundSource.HOSTILE, RandomSource.create());
            this.axe = axe;
            previous = axe.position();
            looping = true;
            delay = 0;
            relative = false;
            attenuation = Attenuation.LINEAR;
            volume = .65F;
            pitch = 1F;
            updatePosition();
        }
        void finish() { stop(); }
        private void updatePosition() {
            x = axe.getX(); y = axe.getY() + axe.modelCenterY() * axe.modelScale(); z = axe.getZ();
        }
        @Override public void tick() {
            if (axe.isRemoved() || axe.level() != Minecraft.getInstance().level) { stop(); return; }
            updatePosition();
            quietTicks = axe.position().distanceToSqr(previous) < .0025 ? quietTicks + 1 : 0;
            previous = axe.position();
            volume = .65F * Math.max(0F, 1F - Math.max(0, quietTicks - 2) / 4F);
            if (quietTicks >= 6) stop();
        }
    }
}
