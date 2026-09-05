package net.krodark.asterion.event;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.krodark.asterion.Asterion;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-game-time deadlines survive saves; weighted rolls cannot bypass the long quiet period. */
public final class RareMazeEvents extends SavedData {
    public static final int HOUR = 20 * 60 * 60;
    public static final Codec<RareMazeEvents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("next_eclipse", -1L).forGetter(s -> s.eclipse),
            Codec.LONG.optionalFieldOf("next_flood", -1L).forGetter(s -> s.flood)
    ).apply(instance, RareMazeEvents::new));
    private static final SavedDataType<RareMazeEvents> TYPE = new SavedDataType<>(
            Asterion.id("rare_maze_events"), RareMazeEvents::new, CODEC, null);
    private long eclipse, flood;
    private RareMazeEvents() { this(-1, -1); }
    private RareMazeEvents(long eclipse, long flood) { this.eclipse = eclipse; this.flood = flood; }
    public static RareMazeEvents get(ServerLevel level) {
        RareMazeEvents state = level.getDataStorage().computeIfAbsent(TYPE);
        if (state.eclipse < 0) state.schedule(level, DeadSunEventSystem.ECLIPSE, 0);
        if (state.flood < 0) state.schedule(level, DeadSunEventSystem.FLOOD, 0);
        // Migrate the old 3-6 hour wait so existing saves also see the slow tide.
        if (state.flood > level.getGameTime() + HOUR / 2) {
            state.flood = level.getGameTime() + HOUR / 2;
            state.setDirty();
        }
        return state;
    }
    public boolean ready(Identifier event, long now) {
        return event.equals(DeadSunEventSystem.ECLIPSE) ? now >= eclipse
                : !event.equals(DeadSunEventSystem.FLOOD) || now >= flood;
    }
    public long nextEclipseTick() { return eclipse; }
    public long nextFloodTick() { return flood; }
    public void schedule(ServerLevel level, Identifier event, int duration) {
        if (event.equals(DeadSunEventSystem.ECLIPSE))
            eclipse = level.getGameTime() + duration + level.getRandom().nextIntBetweenInclusive(2 * HOUR, 4 * HOUR);
        else if (event.equals(DeadSunEventSystem.FLOOD))
            flood = level.getGameTime() + duration + level.getRandom().nextIntBetweenInclusive(HOUR / 3, HOUR / 2);
        else return;
        setDirty();
    }
}
