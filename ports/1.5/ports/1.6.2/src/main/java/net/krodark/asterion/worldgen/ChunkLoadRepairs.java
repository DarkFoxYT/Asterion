package net.krodark.asterion.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** Deduplicated, bounded repairs, drained after chunk loading has returned. */
final class ChunkLoadRepairs {
    private static final Map<ServerLevel, LinkedHashMap<ChunkPos, Boolean>> PENDING = new WeakHashMap<>();
    private ChunkLoadRepairs() {}
    interface Repair { void apply(ServerLevel level, LevelChunk chunk, boolean generated); }
    static synchronized void enqueue(ServerLevel level, ChunkPos pos, boolean generated) {
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashMap<>())
                .merge(pos, generated, (before, after) -> before && after);
    }
    static void tick(ServerLevel level, Repair repair) {
        // Snapshot the budget: loads triggered by a repair belong to a later tick.
        int budget;
        synchronized (ChunkLoadRepairs.class) {
            var pending = PENDING.get(level);
            budget = pending == null ? 0 : Math.min(2, pending.size());
        }
        for (int i = 0; i < budget; i++) {
            Map.Entry<ChunkPos, Boolean> entry;
            synchronized (ChunkLoadRepairs.class) {
                var pending = PENDING.get(level);
                if (pending == null || pending.isEmpty()) return;
                var iterator = pending.entrySet().iterator();
                var next = iterator.next();
                entry = Map.entry(next.getKey(), next.getValue());
                iterator.remove();
            }
            ChunkPos pos = entry.getKey();
            // Do not revive an unloaded chunk just to perform a migration.
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getMinBlockX() >> 4, pos.getMinBlockZ() >> 4);
            if (chunk != null) repair.apply(level, chunk, entry.getValue());
        }
    }
    static synchronized void clear() { PENDING.clear(); }
}
