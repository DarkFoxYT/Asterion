package net.krodark.asterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Persistent progression that survives leaving the maze and restarting the world. */
public final class AsterionWorldState extends SavedData {
    private static final Codec<AsterionWorldState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("minotaur_defeated", false)
                    .forGetter(state -> state.minotaurDefeated)
    ).apply(instance, AsterionWorldState::new));
    private static final SavedDataType<AsterionWorldState> TYPE = new SavedDataType<>(
            Asterion.id("world_state"), AsterionWorldState::new, CODEC, DataFixTypes.LEVEL);

    private boolean minotaurDefeated;

    public AsterionWorldState() { this(false); }
    private AsterionWorldState(boolean minotaurDefeated) { this.minotaurDefeated = minotaurDefeated; }

    public static AsterionWorldState get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean minotaurDefeated() { return minotaurDefeated; }

    public void markMinotaurDefeated() {
        if (minotaurDefeated) return;
        minotaurDefeated = true;
        setDirty();
    }
}
