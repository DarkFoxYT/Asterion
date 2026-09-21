package net.krodark.asterion.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.server.level.ChunkMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

@Mixin(ChunkMap.class)
public interface ChunkMapTrackingAccessor {
    @Accessor("entityMap") Int2ObjectMap<?> asterion$trackedEntities();
}
