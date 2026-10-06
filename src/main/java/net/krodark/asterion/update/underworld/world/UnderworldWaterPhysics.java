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
        int waterY = LimboCascades.waterY(player.getX(), player.getZ());
        if (!player.isInWater() && (player.getY() < waterY - 2
                || player.getY() > waterY + 4
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
        if(level.dimension().equals(Asterion.LIMBO_LEVEL) && LimboSeaRegions.caves(position.x,position.z))return Double.NaN;
        int x = (int)Math.floor(position.x), z = (int)Math.floor(position.z);
        int surfaceY = exposedSurface(level, position.y, x, z);
        if (surfaceY == Integer.MIN_VALUE) return Double.NaN;
        float shore = WaterShoreline.sample(level, x, surfaceY, z);
        return surfaceY + 8.0 / 9.0
                + UnderworldTerrain.waveHeight(position.x, position.z, ticks) * shore;
    }

    /** Fast authored-level probes also keep already-generated flat seas physically usable. */
    public static int surfaceBlockY(net.minecraft.world.level.BlockGetter level, int x, int z) {
        int expected = LimboCascades.waterY(x,z);
        var pos = new BlockPos.MutableBlockPos();
        for (int probe = -1; probe <= LimboCascades.COUNT; probe++) {
            int y = probe < 0 ? expected : UnderworldTerrain.WATER_Y - probe * LimboCascades.DROP;
            if (probe >= 0 && y == expected) continue;
            pos.set(x,y,z);
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()
                    && !level.getFluidState(pos.above()).is(FluidTags.WATER)) return y;
        }
        return Integer.MIN_VALUE;
    }

    public static void alignItem(ItemEntity item, double ticks) {
        if (!item.isAlive()) return;
        int waterY = LimboCascades.waterY(item.getX(), item.getZ());
        if (!item.isInWater() && (item.getY() < waterY - 1
                || item.getY() > waterY + 4
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
        int waterY = surfaceBlockY(level,x,z);
        if (waterY == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        if (Math.abs(center - waterY) <= 32) {
            pos.set(x, waterY, z);
            var fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.WATER) && fluid.isSource()
                    && !level.getFluidState(pos.above()).is(FluidTags.WATER)) return waterY;
        }
        return Integer.MIN_VALUE;
    }
}
