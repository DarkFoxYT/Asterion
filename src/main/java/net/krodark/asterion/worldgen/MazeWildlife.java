package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/** Bounded ecology spawning for the maze's vertical biomes, which share a vanilla biome ID. */
public final class MazeWildlife {
    private MazeWildlife() { }
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 200 != 0 || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return;
        for (var player : level.players()) {
            if (player.isSpectator() || !player.isAlive()) continue;
            boolean sewer = CatacombLayout.contains(player.blockPosition());
            AABB area = player.getBoundingBox().inflate(56);
            int count = sewer ? level.getEntitiesOfClass(ScarletCentipedeEntity.class, area).size()
                    : level.getEntitiesOfClass(BombadierBeetleEntity.class, area).size();
            if (count >= (sewer ? 2 : 6)) continue;
            for (int attempt = 0; attempt < 18; attempt++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2, distance = 24 + level.getRandom().nextInt(20);
                int x = (int)Math.floor(player.getX() + Math.cos(angle) * distance);
                int z = (int)Math.floor(player.getZ() + Math.sin(angle) * distance);
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                for (int y = sewer ? CatacombLayout.WATER_Y + 3 : 59; y >= (sewer ? CatacombLayout.FLOOR_Y - 1 : 48); y--) {
                    BlockPos feet = new BlockPos(x, y, z);
                    if (Math.abs(x) < 65 && Math.abs(z) < 65 || WorldGenerator.isNearSafeRune(level, feet)
                            || !BugSurfaces.allowed(level, feet.below())) continue;
                    var mob = sewer ? Asterion.SCARLET_CENTIPEDE.create(level, EntitySpawnReason.NATURAL)
                            : Asterion.BOMBARDIER_BEETLE.create(level, EntitySpawnReason.NATURAL);
                    if (mob == null) return;
                    mob.setPos(x + .5, y, z + .5);
                    if (!level.noCollision(mob) || !level.isUnobstructed(mob)
                            || level.players().stream().anyMatch(other -> other.distanceToSqr(mob) < 24 * 24)) continue;
                    level.addFreshEntity(mob);
                    return; // One successful spawn per ecology tick, regardless of party size.
                }
            }
        }
    }
}
