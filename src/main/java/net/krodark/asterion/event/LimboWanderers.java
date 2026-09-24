package net.krodark.asterion.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.WandererEntity;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.gamerules.GameRules;

/** Bounded encounters on Limbo's path and in its dry side caves. */
public final class LimboWanderers {
    private LimboWanderers() { }
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel level = server.getLevel(Asterion.LIMBO_LEVEL);
            if (level == null || level.getGameTime() % 200 != 0
                    || !level.getGameRules().get(GameRules.SPAWN_MOBS)) return;
            for (var player : level.players()) {
                if (!player.isAlive() || player.isSpectator() || player.getZ() < UnderworldTerrain.START_Z + 40
                        || player.getZ() > -55 || level.getRandom().nextFloat() > .65F
                        || level.getEntitiesOfClass(WandererEntity.class,
                        player.getBoundingBox().inflate(76)).size() >= 7) continue;
                for (int attempt = 0; attempt < 12; attempt++) {
                    boolean cave = level.getRandom().nextFloat() < .72F;
                    int z = player.getBlockZ() + level.getRandom().nextInt(91) - 45;
                    int x = (int)Math.round(UnderworldTerrain.riverCenter(z) - 15
                            + (cave ? (level.getRandom().nextBoolean() ? 1 : -1)
                            * (26 + level.getRandom().nextInt(45)) : level.getRandom().nextInt(11) - 5));
                    if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
                    BlockPos feet = findFloor(level, x, z, player.getBlockY() + 25, player.getBlockY() - 35);
                    if (feet == null || player.distanceToSqr(x + .5, feet.getY(), z + .5) < 18 * 18
                            || cave && Math.abs(x - (UnderworldTerrain.riverCenter(z) - 15)) < 21) continue;
                    var wanderer = Asterion.WANDERER.create(level, EntitySpawnReason.NATURAL);
                    if (wanderer == null) break;
                    wanderer.setPos(x + .5, feet.getY(), z + .5);
                    wanderer.setCaveDweller(cave);
                    if (!level.noCollision(wanderer) || !level.isUnobstructed(wanderer)) continue;
                    level.addFreshEntity(wanderer);
                    break;
                }
            }
        });
    }
    public static BlockPos findFloor(ServerLevel level, int x, int z, int top, int bottom) {
        for (int y = Math.min(UnderworldTerrain.MAX_Y - 3, top);
             y >= Math.max(UnderworldTerrain.MIN_Y + 2, bottom); y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(feet.below()).isSolidRender()
                    && level.getFluidState(feet).isEmpty()) return feet;
        }
        return null;
    }
}
