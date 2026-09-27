package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;

import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerChunkEvents {
    private static final CopyOnWriteArrayList<Load> LOAD = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<Unload> UNLOAD = new CopyOnWriteArrayList<>();
    public static final Event<Load> CHUNK_LOAD = LOAD::add;
    public static final Event<Unload> CHUNK_UNLOAD = UNLOAD::add;

    static {
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
                LOAD.forEach(listener -> listener.onChunkLoad(level, chunk));
        });
        MinecraftForge.EVENT_BUS.addListener((ChunkEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
                UNLOAD.forEach(listener -> listener.onChunkUnload(level, chunk));
        });
    }

    private ServerChunkEvents() {}
    @FunctionalInterface public interface Load { void onChunkLoad(ServerLevel level, LevelChunk chunk); }
    @FunctionalInterface public interface Unload { void onChunkUnload(ServerLevel level, LevelChunk chunk); }
}
