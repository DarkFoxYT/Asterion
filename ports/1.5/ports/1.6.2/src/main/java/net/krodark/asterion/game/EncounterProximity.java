package net.krodark.asterion.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Shared entrance range for encounter invitations and cinematic recipients. */
public final class EncounterProximity {
    private static final double HORIZONTAL_RANGE = 12.0;
    private static final double VERTICAL_RANGE = 5.0;

    private EncounterProximity() { }

    public static boolean isNear(Vec3 position, Vec3 entrance) {
        Vec3 offset = position.subtract(entrance);
        return offset.horizontalDistanceSqr() <= HORIZONTAL_RANGE * HORIZONTAL_RANGE
                && Math.abs(offset.y) <= VERTICAL_RANGE;
    }

    public static boolean canJoin(ServerPlayer player, ServerLevel level, Vec3 entrance) {
        return player.level() == level && player.isAlive() && !player.isSpectator()
                && !player.isCreative() && !ArenaDeathRecovery.isRecovering(player)
                && isNear(player.position(), entrance);
    }
}
