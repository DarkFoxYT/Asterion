package net.krodark.asterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AsterionWorldState extends SavedData {
    private static final Codec<AsterionWorldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("minotaur_defeated", false)
                    .forGetter(state -> state.minotaurDefeated),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("rune_checkpoints", Map.of())
                    .forGetter(state -> state.runeCheckpoints)
    ).apply(instance, AsterionWorldState::new));
    private static final SavedDataType<AsterionWorldState> TYPE = new SavedDataType<>(
            Asterion.id("world_state"), AsterionWorldState::new, CODEC, DataFixTypes.LEVEL);

    private boolean minotaurDefeated;
    private final Map<String, Long> runeCheckpoints;

    public AsterionWorldState() { this(false, Map.of()); }
    private AsterionWorldState(boolean minotaurDefeated, Map<String, Long> runeCheckpoints) {
        this.minotaurDefeated = minotaurDefeated;
        this.runeCheckpoints = new HashMap<>(runeCheckpoints);
    }

    public static AsterionWorldState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean minotaurDefeated() { return minotaurDefeated; }

    public net.minecraft.core.BlockPos runeCheckpoint(UUID playerId) {
        Long packed = runeCheckpoints.get(playerId.toString());
        return packed == null ? null : net.minecraft.core.BlockPos.of(packed);
    }

    public void setRuneCheckpoint(UUID playerId, net.minecraft.core.BlockPos position) {
        Long previous = runeCheckpoints.put(playerId.toString(), position.asLong());
        if (previous == null || previous.longValue() != position.asLong()) setDirty();
    }

    public void markMinotaurDefeated() {
        if (minotaurDefeated) return;
        minotaurDefeated = true;
        setDirty();
    }
}
