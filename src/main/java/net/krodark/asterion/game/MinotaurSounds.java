package net.krodark.asterion.game;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.network.MinotaurGlobalSoundPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;

public final class MinotaurSounds {
    private MinotaurSounds() { }
    public static SoundEvent globalSound(int index) {
        return switch (index) {
            case 0 -> Asterion.MINOTAUR_ROAR;
            case 1 -> Asterion.MINOTAUR_ENTRY_ROAR;
            case 2 -> Asterion.MINOTAUR_CHARGE_ROAR;
            case 3 -> Asterion.MINOTAUR_AGGRO;
            default -> null;
        };
    }
    public static boolean playGlobal(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        int index = -1;
        for (int i = 0; i < 4; i++) if (globalSound(i) == sound) { index = i; break; }
        if (index < 0) return false;
        // Positional call sites used volume to extend range. Global playback needs bounded gain instead.
        var payload = new MinotaurGlobalSoundPayload(index, Math.clamp(volume, .75F, 1F),
                Math.clamp(pitch, .5F, 2F), level.getRandom().nextLong());
        for (var player : level.players())
            if (ServerPlayNetworking.canSend(player, MinotaurGlobalSoundPayload.TYPE))
                ServerPlayNetworking.send(player, payload);
        return true;
    }
}
