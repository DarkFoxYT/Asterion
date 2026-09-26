package net.krodark.asterion.update.underworld.world;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Keeps calm surface swimming aligned with the same displaced mesh shown to the player. */
public final class UnderworldWaterPhysics {
    private UnderworldWaterPhysics() { }

    public static void alignSurface(Player player, double ticks) {
        if (!player.level().dimension().equals(Asterion.LIMBO_LEVEL) || !player.isAlive()
                || player.isSpectator() || player.getAbilities().flying || player.isPassenger()
                || player.isSwimming() || player.isShiftKeyDown()) return;
        if (!player.isInWater() && (player.getY() < UnderworldTerrain.WATER_Y - 2
                || player.getY() > UnderworldTerrain.WATER_Y + 4
                || player.level().getBlockState(player.blockPosition().below()).isSolidRender()
                || CharonsFerryEntity.supporting(player) != null)) return;
        Vec3 motion = player.getDeltaMovement();
        // Active ascent/descent keeps vanilla authority; this only removes the flat-water hover.
        if (motion.y > .075 || motion.y < -.075) return;
        double surface = surfaceAt(player, ticks);
        if (!Double.isFinite(surface)) return;
        double targetFeet = surface - 1.18;
        double error = targetFeet - player.getY();
        if (Math.abs(error) > 2.8) return;
        double correction = Math.clamp(error * .12 - motion.y * .18, -.065, .065);
        player.setDeltaMovement(motion.x, motion.y + correction, motion.z);
    }

    public static double surfaceAt(Player player, double ticks) {
        return surfaceAt((Entity)player, ticks);
    }

    public static double surfaceAt(Entity entity, double ticks) {
        return surfaceAt(entity.level(), entity.position(), ticks);
    }

    public static boolean sheltered(Entity entity) {
        if (!entity.level().dimension().equals(Asterion.LIMBO_LEVEL)) return false;
        if (entity.getVehicle() instanceof CharonsFerryEntity) return true;
        for (var ferry : entity.level().getEntitiesOfClass(CharonsFerryEntity.class,
                entity.getBoundingBox().inflate(4,2,4))) {
            double deckOffset=entity.getY()-ferry.deckHeightAt(entity.getX(),entity.getZ());
            if (ferry.supports(entity) || ferry.carries(entity) && deckOffset>=-.4 && deckOffset<1.2
                    && ferry.overlapsDeck(entity.getBoundingBox())) return true;
        }
        return false;
    }

    public static double surfaceAt(net.minecraft.world.level.Level level, Vec3 position, double ticks) {
        int x = (int)Math.floor(position.x), z = (int)Math.floor(position.z);
        int surfaceY = exposedSurface(level, position.y, x, z);
        if (surfaceY == Integer.MIN_VALUE) return Double.NaN;
        float shore = WaterShoreline.sample(level, x, surfaceY, z);
        return surfaceY + 8.0 / 9.0
                + UnderworldTerrain.waveHeight(position.x, position.z, ticks) * shore;
    }

    public static void alignItem(ItemEntity item, double ticks) {
        if (!item.isAlive()) return;
        if (!item.isInWater() && (item.getY() < UnderworldTerrain.WATER_Y - 1
                || item.getY() > UnderworldTerrain.WATER_Y + 4
                || item.level().getBlockState(item.blockPosition().below()).isSolidRender()
                || CharonsFerryEntity.supporting(item) != null)) return;
        double surface = surfaceAt(item, ticks);
        if (!Double.isFinite(surface)) return;
        Vec3 motion = item.getDeltaMovement();
        double error = surface - .24 - item.getY();
        if (Math.abs(error) > 2.5) return;
        double correction = Math.clamp(error * .11 - motion.y * .16, -.06, .06);
        item.setDeltaMovement(motion.x, motion.y + correction, motion.z);
    }

    private static int exposedSurface(net.minecraft.world.level.Level level, double height, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int center = (int)Math.floor(height);
        for (int y = center + 3; y >= center - 4; y--) {
            pos.set(x, y, z);
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()
                    && !level.getFluidState(pos.above()).is(FluidTags.WATER)) return y;
        }
        if (center < UnderworldTerrain.WATER_Y - 2) {
            pos.set(x, UnderworldTerrain.WATER_Y, z);
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()
                    && !level.getFluidState(pos.above()).is(FluidTags.WATER)) return UnderworldTerrain.WATER_Y;
        }
        return Integer.MIN_VALUE;
    }
}
