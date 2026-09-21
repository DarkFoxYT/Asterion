package net.krodark.asterion.client.audio;

import net.krodark.asterion.game.MinotaurSounds;
import net.krodark.asterion.network.MinotaurGlobalSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class MinotaurSoundPlayback {
    private MinotaurSoundPlayback() { }
    public static void play(MinotaurGlobalSoundPayload payload) {
        SoundEvent event = MinotaurSounds.globalSound(payload.sound());
        if (event == null || !Float.isFinite(payload.volume()) || !Float.isFinite(payload.pitch())) return;
        Minecraft.getInstance().getSoundManager().play(new GlobalRoar(event, payload));
    }
    public static final class GlobalRoar extends AbstractSoundInstance {
        public GlobalRoar(SoundEvent event, MinotaurGlobalSoundPayload payload) {
            super(event, SoundSource.HOSTILE, RandomSource.create(payload.seed()));
            volume = Math.clamp(payload.volume(), 0F, 1F);
            pitch = Math.clamp(payload.pitch(), .5F, 2F);
            relative = true;
            attenuation = Attenuation.NONE;
        }
    }
}
