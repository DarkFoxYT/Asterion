package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Keeps calm surface swimming aligned with the same displaced mesh shown to the player. */
public final class UnderworldWaterPhysics {
    private UnderworldWaterPhysics() { }

    public static void alignSurface(Player player, double ticks) {
        if (!player.level().dimension().equals(Asterion.LIMBO_LEVEL) || !player.isAlive()
                || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
                || !player.isInWater() || player.isSwimming() || player.isShiftKeyDown()) return;
        Vec3 motion = player.getDeltaMovement();
        // Active ascent/descent keeps vanilla authority; this only removes the flat-water hover.
        if (motion.y > .075 || motion.y < -.075) return;
        double surface = surfaceAt(player, ticks);
        if (!Double.isFinite(surface)) return;
        double targetFeet = surface - 1.18;
        double error = targetFeet - player.getY();
        if (Math.abs(error) > 1.1) return;
        double correction = Math.clamp(error * .115 - motion.y * .18, -.032, .032);
        player.setDeltaMovement(motion.x, motion.y + correction, motion.z);
    }

    public static double surfaceAt(Player player, double ticks) {
        int x = (int)Math.floor(player.getX()), z = (int)Math.floor(player.getZ());
        int surfaceY = exposedSurface(player, x, z);
        if (surfaceY == Integer.MIN_VALUE) return Double.NaN;
        float shore = WaterShoreline.sample(player.level(), x, surfaceY, z);
        return surfaceY + 8.0 / 9.0
                + UnderworldTerrain.waveHeight(player.getX(), player.getZ(), ticks) * shore;
    }

    private static int exposedSurface(Player player, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int center = (int)Math.floor(player.getY());
        for (int y = center + 3; y >= center - 2; y--) {
            pos.set(x, y, z);
            var fluid = player.level().getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()
                    && !player.level().getFluidState(pos.above()).is(FluidTags.WATER)) return y;
        }
        return Integer.MIN_VALUE;
    }
}
