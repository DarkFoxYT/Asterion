package net.krodark.asterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
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
                    .forGetter(state -> state.runeCheckpoints),
            Codec.LONG.optionalFieldOf("summoned_portal_center", Long.MIN_VALUE)
                    .forGetter(state -> state.summonedPortalCenter),
            Codec.INT.optionalFieldOf("summoned_portal_y", 0)
                    .forGetter(state -> state.summonedPortalY),
            Codec.LONG.optionalFieldOf("summoned_portal_seed", 0L)
                    .forGetter(state -> state.summonedPortalSeed),
            Codec.STRING.optionalFieldOf("summoned_portal_dimension", "minecraft:overworld")
                    .forGetter(state -> state.summonedPortalDimension)
    ).apply(instance, AsterionWorldState::new));
    private static final SavedDataType<AsterionWorldState> TYPE = new SavedDataType<>(
            Asterion.id("world_state"), AsterionWorldState::new, CODEC, DataFixTypes.LEVEL);

    private boolean minotaurDefeated;
    private final Map<String, Long> runeCheckpoints;
    private long summonedPortalCenter;
    private int summonedPortalY;
    private long summonedPortalSeed;
    private String summonedPortalDimension;

    public AsterionWorldState() {
        this(false, Map.of(), Long.MIN_VALUE, 0, 0L, "minecraft:overworld");
    }
    private AsterionWorldState(boolean minotaurDefeated, Map<String, Long> runeCheckpoints,
                               long summonedPortalCenter, int summonedPortalY,
                               long summonedPortalSeed, String summonedPortalDimension) {
        this.minotaurDefeated = minotaurDefeated;
        this.runeCheckpoints = new HashMap<>(runeCheckpoints);
        this.summonedPortalCenter = summonedPortalCenter;
        this.summonedPortalY = summonedPortalY;
        this.summonedPortalSeed = summonedPortalSeed;
        this.summonedPortalDimension = summonedPortalDimension;
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

    public SavedPortal summonedPortal() {
        if (summonedPortalCenter == Long.MIN_VALUE) return null;
        Identifier id = Identifier.tryParse(summonedPortalDimension);
        if (id == null) return null;
        return new SavedPortal(net.minecraft.core.BlockPos.of(summonedPortalCenter), summonedPortalY,
                summonedPortalSeed, ResourceKey.create(Registries.DIMENSION, id));
    }

    public void setSummonedPortal(ResourceKey<Level> dimension, net.minecraft.core.BlockPos center,
                                  int surfaceY, long visualSeed) {
        summonedPortalCenter = center.asLong();
        summonedPortalY = surfaceY;
        summonedPortalSeed = visualSeed;
        summonedPortalDimension = dimension.identifier().toString();
        setDirty();
    }

    public record SavedPortal(net.minecraft.core.BlockPos center, int surfaceY, long visualSeed,
                              ResourceKey<Level> dimension) { }

    public void markMinotaurDefeated() {
        if (minotaurDefeated) return;
        minotaurDefeated = true;
        setDirty();
    }
}
