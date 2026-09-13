package net.krodark.asterion.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krodark.asterion.Asterion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

 
public final class RareMazeEvents extends SavedData {
    public static final int HOUR = 20 * 60 * 60;
    public static final Codec<RareMazeEvents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("next_eclipse", -1L).forGetter(s -> s.eclipse),
            Codec.LONG.optionalFieldOf("next_flood", -1L).forGetter(s -> s.flood)
    ).apply(instance, RareMazeEvents::new));
    private static final SavedData.Factory<RareMazeEvents> FACTORY =
            net.krodark.asterion.port.compat.SavedDataCompat.factory(CODEC, RareMazeEvents::new);
    private long eclipse, flood;
    private RareMazeEvents() { this(-1, -1); }
    private RareMazeEvents(long eclipse, long flood) { this.eclipse = eclipse; this.flood = flood; }
    public static RareMazeEvents get(ServerLevel level) {
        RareMazeEvents state = level.getDataStorage().computeIfAbsent(FACTORY, "asterion_rare_maze_events");
        if (state.eclipse < 0 || state.eclipse > level.getGameTime() + HOUR / 2 + 20 * 60 * 10) {
             
            state.eclipse = level.getGameTime() + level.getRandom().nextIntBetweenInclusive(20 * 60 * 5, 20 * 60 * 8);
            state.setDirty();
        }
        if (state.flood < 0) state.schedule(level, DeadSunEventSystem.FLOOD, 0);
         
        if (state.flood > level.getGameTime() + HOUR / 2) {
            state.flood = level.getGameTime() + HOUR / 2;
            state.setDirty();
        }
        return state;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return net.krodark.asterion.port.compat.SavedDataCompat.save(CODEC, this, tag, registries);
    }
    public boolean ready(ResourceLocation event, long now) {
        return event.equals(DeadSunEventSystem.ECLIPSE) ? now >= eclipse
                : !event.equals(DeadSunEventSystem.FLOOD) || now >= flood;
    }
    public long nextEclipseTick() { return eclipse; }
    public long nextFloodTick() { return flood; }
    public void schedule(ServerLevel level, ResourceLocation event, int duration) {
        if (event.equals(DeadSunEventSystem.ECLIPSE))
            eclipse = level.getGameTime() + duration + level.getRandom().nextIntBetweenInclusive(HOUR / 3, HOUR / 2);
        else if (event.equals(DeadSunEventSystem.FLOOD))
            flood = level.getGameTime() + duration + level.getRandom().nextIntBetweenInclusive(HOUR / 3, HOUR / 2);
        else return;
        setDirty();
    }
}
