package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A single streamed background loop for the whole dimension, independent of biome music. */
public final class MazeAmbience {
    private static Loop loop;
    private static int ticks, started;
    private MazeAmbience() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MazeAmbience::tick);
    }

    private static void tick(Minecraft client) {
        if (client.level == null || client.player == null) {
            if (loop != null) client.getSoundManager().stop(loop);
            loop = null;
            return;
        }
        if (client.isPaused()) return;
        ticks++;
        boolean active = client.level.dimension().equals(Asterion.ASTERION_LEVEL)
                && client.player.isAlive()
                && client.options.getSoundSourceVolume(SoundSource.AMBIENT) > 0
                && client.options.getSoundSourceVolume(SoundSource.MASTER) > 0;
        if (loop != null) {
            loop.target = active ? .10F : 0;
            // Sound reloads and dimension changes can stop the engine's voice externally.
            if (ticks - started > 40 && !client.getSoundManager().isActive(loop)) loop = null;
        }
        if (loop == null && active) {
            loop = new Loop();
            started = ticks;
            client.getSoundManager().play(loop);
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private float target = .10F;
        private Loop() {
            super(SoundEvent.createVariableRangeEvent(Asterion.id("maze_ambience")),
                    SoundSource.AMBIENT, RandomSource.create());
            relative = true;
            attenuation = Attenuation.NONE;
            looping = true;
            delay = 0; // Let the streaming engine wrap the audio, without a scheduled replay gap.
            volume = .001F;
        }
        @Override public void tick() {
            volume += Math.clamp(target - volume, -.0025F, .0025F);
            if (target == 0 && volume <= .001F) stop();
        }
    }
}
