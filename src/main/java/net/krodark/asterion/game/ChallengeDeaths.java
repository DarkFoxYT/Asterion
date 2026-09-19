package net.krodark.asterion.game;

import com.mojang.serialization.Codec;
import java.util.*;
import net.krodark.asterion.Asterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;


public final class ChallengeDeaths extends SavedData {
    public static final String TAG = "asterion_challenge_mob";
    private static final Codec<ChallengeDeaths> CODEC = Codec.STRING.listOf().xmap(ChallengeDeaths::new,
            state -> state.dead.stream().map(UUID::toString).toList());
    private static final net.krodark.asterion.port.compat.SavedDataCompat.Factory<ChallengeDeaths> FACTORY =
            net.krodark.asterion.port.compat.SavedDataCompat.factory(
                    CODEC, () -> new ChallengeDeaths(List.of()));
    private final Set<UUID> dead = new HashSet<>();
    private ChallengeDeaths(List<String> ids) { ids.forEach(id -> dead.add(UUID.fromString(id))); }
    public static ChallengeDeaths get(ServerLevel level) {
        return net.krodark.asterion.port.compat.SavedDataCompat.get(level.getDataStorage(), FACTORY, "asterion_challenge_deaths");
    }
    public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        return net.krodark.asterion.port.compat.SavedDataCompat.save(CODEC, this, tag, registries);
    }
    public void record(UUID id) { if (dead.add(id)) setDirty(); }
    public boolean consume(UUID id) { boolean removed = dead.remove(id); if (removed) setDirty(); return removed; }
//? if <1.20.5 {
/*    @Override public net.minecraft.nbt.CompoundTag save(net.minecraft.nbt.CompoundTag tag) { return save(tag, null); }*/
//?}
}
