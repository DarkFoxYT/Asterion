package net.krodark.asterion.worldgen;

import net.krodark.asterion.mixin.ChunkAccessAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.chunk.LevelChunk;

public final class MazeChunkData {
    private MazeChunkData() { }

    public static void prepare(ServerLevel level, LevelChunk chunk) {
        var pending = ((ChunkAccessAccessor) chunk).asterion$pendingBlockEntities();
        var pois = level.getPoiManager();
        for (var pos : chunk.getBlockEntitiesPos()) {
            var state = chunk.getBlockState(pos);
            var tag = pending.get(pos);
             
             
             
             
             
            if (state.getBlock() instanceof net.krodark.asterion.block.CrucibleBlock
                    && !net.krodark.asterion.block.CrucibleBlock.isRoot(state)) {
                if (tag != null) {
                    pending.remove(pos);
                    chunk.markUnsaved();
                }
                continue;
            }
            if (!state.hasBlockEntity()) {
                 
                if (tag != null && "DUMMY".equals(tag.getStringOr("id", ""))) {
                    pending.remove(pos);
                    chunk.markUnsaved();
                }
                continue;
            }

             
            if (tag != null) chunk.getBlockEntity(pos);

             
            PoiTypes.forState(state).ifPresent(type -> {
                var registered = pois.getType(pos);
                if (registered.filter(type::equals).isPresent()) return;
                if (registered.isPresent()) pois.remove(pos);
                pois.add(pos, type);
            });
        }
    }
}
