package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;

/** Forge chunk lifecycle adapter for shared world generation handlers. */
public final class ServerChunkEvents {
    public static final Load CHUNK_LOAD = new Load();
    public static final Unload CHUNK_UNLOAD = new Unload();
    private ServerChunkEvents() {}

    @FunctionalInterface public interface LoadCallback {
        void onLoad(ServerLevel level, LevelChunk chunk, boolean newlyGenerated);
    }
    @FunctionalInterface public interface UnloadCallback {
        void onUnload(ServerLevel level, LevelChunk chunk);
    }

    public static final class Load {
        public void register(LoadCallback callback) {
            ChunkEvent.Load.BUS.addListener(event -> {
                if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel() instanceof ServerLevel level)
                    callback.onLoad(level, chunk, event.isNewChunk());
            });
        }
    }
    public static final class Unload {
        public void register(UnloadCallback callback) {
            ChunkEvent.Unload.BUS.addListener(event -> {
                if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel() instanceof ServerLevel level)
                    callback.onUnload(level, chunk);
            });
        }
    }
}
