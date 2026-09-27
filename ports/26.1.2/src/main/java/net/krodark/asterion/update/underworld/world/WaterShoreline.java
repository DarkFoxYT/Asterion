package net.krodark.asterion.update.underworld.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;

/** Cached mesh and buoyancy use the same eight-block shoreline falloff. */
public final class WaterShoreline {
    private WaterShoreline() { }
    public static float attenuation(int[] depths, int stride, int x, int z) {
        int depth = Math.min(Math.min(depths[z * stride + x], depths[z * stride + x - 1]),
                Math.min(depths[(z - 1) * stride + x], depths[(z - 1) * stride + x - 1]));
        if (depth <= 1) return 0;
        double nearest = 64;
        for (int dz = -8; dz < 8; dz++) for (int dx = -8; dx < 8; dx++) {
            if (depths[(z + dz) * stride + x + dx] != 0) continue;
            double sx = Math.max(0, Math.abs(dx + .5) - .5), sz = Math.max(0, Math.abs(dz + .5) - .5);
            nearest = Math.min(nearest, sx * sx + sz * sz);
        }
        double shore = Math.min(1, Math.sqrt(nearest) / 8);
        double shallow = Math.clamp((depth - 1) / 5.0, 0, 1);
        return (float)(shore * shore * (3 - 2 * shore) * shallow * shallow * (3 - 2 * shallow));
    }
    public static float sample(BlockGetter level, int x, int z) {
        return sample(level, x, UnderworldTerrain.WATER_Y, z);
    }
    public static float sample(BlockGetter level, int x, int surfaceY, int z) {
        int[] depths = new int[18 * 18];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < 18; dz++) for (int dx = 0; dx < 18; dx++) {
            int depth = 0;
            while (depth < 6 && level.getFluidState(pos.set(x + dx - 9,
                    surfaceY - depth, z + dz - 9)).is(FluidTags.WATER)) depth++;
            depths[dz * 18 + dx] = depth;
        }
        return attenuation(depths, 18, 9, 9);
    }
}
