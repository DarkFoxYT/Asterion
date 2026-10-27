package net.krodark.asterion.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpawnPlacements.class)
public interface SpawnPlacementsAccessor {
    @Invoker("register")
    static <T extends Mob> void asterion$register(EntityType<T> entityType, SpawnPlacementType placementType,
                                                  Heightmap.Types heightmap,
                                                  SpawnPlacements.SpawnPredicate<T> predicate) {
        throw new AssertionError("Mixin transformation did not apply");
    }
}
