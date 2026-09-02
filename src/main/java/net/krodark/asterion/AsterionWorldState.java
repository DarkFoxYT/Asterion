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
            Codec.BOOL.optionalFieldOf("cursed_brazier_defeated", false)
                    .forGetter(state -> state.cursedBrazierDefeated),
            Codec.INT.listOf().optionalFieldOf("defeated_cursed_brazier_rooms", java.util.List.of())
                    .forGetter(state -> state.cursedBrazierDefeatedRooms.stream().sorted().toList()),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("rune_checkpoints", Map.of())
                    .forGetter(state -> state.runeCheckpoints),
            Codec.LONG.optionalFieldOf("summoned_portal_center", Long.MIN_VALUE)
                    .forGetter(state -> state.summonedPortalCenter),
            Codec.INT.optionalFieldOf("summoned_portal_y", 0)
                    .forGetter(state -> state.summonedPortalY),
            Codec.LONG.optionalFieldOf("summoned_portal_seed", 0L)
                    .forGetter(state -> state.summonedPortalSeed),
            Codec.STRING.optionalFieldOf("summoned_portal_dimension", "minecraft:overworld")
                    .forGetter(state -> state.summonedPortalDimension),
            Codec.INT.optionalFieldOf("boss_arena_revision", 0).forGetter(state -> state.bossArenaRevision),
            Codec.BOOL.optionalFieldOf("arena_lamenters_installed", false).forGetter(state -> state.arenaLamentersInstalled)
    ).apply(instance, AsterionWorldState::new));
    private static final SavedDataType<AsterionWorldState> TYPE = new SavedDataType<>(
            Asterion.id("world_state"), AsterionWorldState::new, CODEC, DataFixTypes.LEVEL);

    private boolean minotaurDefeated;
    private boolean cursedBrazierDefeated;
    private final java.util.Set<Integer> cursedBrazierDefeatedRooms;
    private final Map<String, Long> runeCheckpoints;
    private long summonedPortalCenter;
    private int summonedPortalY;
    private long summonedPortalSeed;
    private String summonedPortalDimension;
    private int bossArenaRevision;
    private boolean arenaLamentersInstalled;

    public AsterionWorldState() {
        this(false, false, java.util.List.of(), Map.of(), Long.MIN_VALUE, 0, 0L, "minecraft:overworld", 0, false);
    }
    private AsterionWorldState(boolean minotaurDefeated, boolean cursedBrazierDefeated,
                               java.util.List<Integer> cursedBrazierDefeatedRooms,
                               Map<String, Long> runeCheckpoints,
                               long summonedPortalCenter, int summonedPortalY,
                               long summonedPortalSeed, String summonedPortalDimension, int bossArenaRevision,
                               boolean arenaLamentersInstalled) {
        this.minotaurDefeated = minotaurDefeated;
        this.cursedBrazierDefeated = cursedBrazierDefeated;
        this.cursedBrazierDefeatedRooms = new java.util.HashSet<>(cursedBrazierDefeatedRooms);
        this.runeCheckpoints = new HashMap<>(runeCheckpoints);
        this.summonedPortalCenter = summonedPortalCenter;
        this.summonedPortalY = summonedPortalY;
        this.summonedPortalSeed = summonedPortalSeed;
        this.summonedPortalDimension = summonedPortalDimension;
        this.bossArenaRevision = bossArenaRevision;
        this.arenaLamentersInstalled = arenaLamentersInstalled;
    }

    public static AsterionWorldState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean minotaurDefeated() { return minotaurDefeated; }
    public boolean cursedBrazierDefeated() { return cursedBrazierDefeated; }
    public boolean cursedBrazierDefeated(int roomIndex) {
        return roomIndex == 0 ? cursedBrazierDefeated : cursedBrazierDefeatedRooms.contains(roomIndex);
    }

    public int bossArenaRevision() { return bossArenaRevision; }
    public void setBossArenaRevision(int revision) { bossArenaRevision = revision; setDirty(); }
    public boolean arenaLamentersInstalled() { return arenaLamentersInstalled; }
    public void markArenaLamentersInstalled() { arenaLamentersInstalled = true; setDirty(); }

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

    public void resetMinotaurEncounter() {
        minotaurDefeated = false;
        setDirty();
    }

    public void markMinotaurDefeated() {
        if (minotaurDefeated) return;
        minotaurDefeated = true;
        setDirty();
    }

    public void resetCursedBrazierEncounter() {
        resetCursedBrazierEncounter(0);
    }

    public void resetCursedBrazierEncounter(int roomIndex) {
        if (roomIndex == 0) cursedBrazierDefeated = false;
        else cursedBrazierDefeatedRooms.remove(roomIndex);
        setDirty();
    }

    public void markCursedBrazierDefeated() {
        markCursedBrazierDefeated(0);
    }

    public void markCursedBrazierDefeated(int roomIndex) {
        if (cursedBrazierDefeated(roomIndex)) return;
        if (roomIndex == 0) cursedBrazierDefeated = true;
        else cursedBrazierDefeatedRooms.add(roomIndex);
        setDirty();
    }
}
