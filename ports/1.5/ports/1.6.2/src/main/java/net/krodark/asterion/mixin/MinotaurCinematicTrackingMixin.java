package net.krodark.asterion.mixin;

import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** A remote cinematic camera must receive the boss even on a low view distance. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class MinotaurCinematicTrackingMixin implements net.krodark.asterion.worldgen.CinematicTracking {
    @Shadow public abstract void updatePlayer(ServerPlayer player);
    @Override public void refresh(ServerPlayer player) { updatePlayer(player); }
    @Shadow @Final private Entity entity;

    @ModifyVariable(method = "updatePlayer", at = @At("STORE"), ordinal = 0)
    private boolean asterion$trackCinematicBoss(boolean tracked, ServerPlayer player) {
        return tracked || entity instanceof MinotaurEntity boss && boss.doorEntryTicks() > 0
                && boss.level() == player.level() && boss.distanceToSqr(player) < 128 * 128
                && BossArenaEncounter.isMovementLocked(player);
    }
}
