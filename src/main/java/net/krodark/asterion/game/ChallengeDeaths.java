package net.krodark.asterion.game;

import com.mojang.serialization.Codec;
import java.util.*;
import net.krodark.asterion.Asterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;

/** Death receipts survive unloading the spawner's chunk while its mobs are being fought. */
public final class ChallengeDeaths extends SavedData {
    public static final String TAG = "asterion_challenge_mob";
    private static final Codec<ChallengeDeaths> CODEC = Codec.STRING.listOf().xmap(ChallengeDeaths::new,
            state -> state.dead.stream().map(UUID::toString).toList());
    private static final SavedDataType<ChallengeDeaths> TYPE = new SavedDataType<>(
            Asterion.id("challenge_deaths"), () -> new ChallengeDeaths(List.of()), CODEC, null);
    private final Set<UUID> dead = new HashSet<>();
    private ChallengeDeaths(List<String> ids) { ids.forEach(id -> dead.add(UUID.fromString(id))); }
    public static ChallengeDeaths get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
    public void record(UUID id) { if (dead.add(id)) setDirty(); }
    public boolean consume(UUID id) { boolean removed = dead.remove(id); if (removed) setDirty(); return removed; }
}
