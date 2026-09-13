package net.krodark.asterion.port.fabric;

import net.fabricmc.api.ModInitializer;
import net.krodark.asterion.Asterion;

public final class AsterionFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Asterion.initialize();
        net.minecraft.world.entity.SpawnPlacements.register(
                net.krodark.asterion.game.AncientContent.SKELETON,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.krodark.asterion.entity.AncientSkeletonEntity::canSpawn);
        net.minecraft.world.entity.SpawnPlacements.register(
                Asterion.BOMBARDIER_BEETLE,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules);
        net.minecraft.world.entity.SpawnPlacements.register(
                Asterion.CONSTRUCT,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules);
        net.minecraft.world.entity.SpawnPlacements.register(
                Asterion.SCARLET_CENTIPEDE,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules);
    }
}
