package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The supplied Limbo piece begins at the river reveal and carries through the crossing. */
public final class LimboMusic {
    private static ClientLevel trackedLevel;
    private static Voice voice;
    private static boolean revealed;
    private static int ticks;

    private LimboMusic() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(LimboMusic::tick);
    }

    private static void tick(Minecraft client) {
        ticks++;
        if (trackedLevel != client.level) {
            stop(client);
            trackedLevel = client.level;
            revealed = false;
        }
        boolean limbo = client.level != null && client.player != null
                && client.level.dimension().equals(Asterion.LIMBO_LEVEL);
        if (!limbo) { stop(client); return; }
        if (client.player.getZ() > 8.0) revealed = true;
        float target = revealed ? .52F * AsterionConfig.INSTANCE.musicVolumePercent / 100F : 0F;
        if (voice != null && ticks - voice.started > 40 && !client.getSoundManager().isActive(voice))
            voice = null;
        if (voice == null && target > .001F) {
            voice = new Voice(target, ticks);
            client.getSoundManager().play(voice);
        } else if (voice != null) {
            voice.target = target;
        }
    }

    public static boolean ownsMusic() {
        Minecraft client = Minecraft.getInstance();
        return client.level != null && client.level.dimension().equals(Asterion.LIMBO_LEVEL);
    }

    private static void stop(Minecraft client) {
        if (voice != null) client.getSoundManager().stop(voice);
        voice = null;
    }

    private static final class Voice extends AbstractTickableSoundInstance {
        private float target;
        private final int started;
        private Voice(float target, int started) {
            super(SoundEvent.createVariableRangeEvent(Asterion.id("limbo_ambience")),
                    SoundSource.MUSIC, RandomSource.create());
            this.target = target;
            this.started = started;
            volume = .001F;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
        }
        @Override public void tick() {
            volume += Math.clamp(target - volume, -.010F, .004F);
        }
    }
}
