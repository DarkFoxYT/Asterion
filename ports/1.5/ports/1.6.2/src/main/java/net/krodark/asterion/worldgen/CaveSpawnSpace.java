package net.krodark.asterion.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CaveSpawnSpace {
    private CaveSpawnSpace() { }

    public static boolean nearOccupiedChamber(ServerLevel level, BlockPos feet) {
        if (!ShaleCaves.contains(feet)) return false;
        Vec3 center = Vec3.atBottomCenterOf(feet).add(0, 1, 0);
        for (var player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || !ShaleCaves.contains(player.blockPosition())
                    || Math.abs(player.getY() - feet.getY()) > 8 || player.distanceToSqr(center) > 56 * 56) continue;
            if (level.clip(new ClipContext(player.getEyePosition(), center,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS)
                return true;
        }
        return false;
    }
}
