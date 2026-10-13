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

    /** Same falloff for a mesh grid, sharing the nearest-shore search between vertices. */
    public static float[] attenuationGrid(int[] depths, int stride, int startX, int startZ,
                                          int width, int height) {
        int rows = depths.length / stride, vertexStride = stride + 1;
        int[] horizontal = new int[vertexStride * (rows + 1)];
        java.util.Arrays.fill(horizontal, 64);
        // Distance to a closed dry cell is distance to its nearest corner at integer vertices.
        for (int z = 0; z < rows; z++) for (int x = 0; x < stride; x++) {
            if (depths[z * stride + x] != 0) continue;
            int at = z * vertexStride + x;
            horizontal[at] = horizontal[at + 1] = 0;
            horizontal[at + vertexStride] = horizontal[at + vertexStride + 1] = 0;
        }
        for (int z = 0; z <= rows; z++) {
            int distance = 8, row = z * vertexStride;
            for (int x = 0; x <= stride; x++) {
                distance = horizontal[row + x] == 0 ? 0 : Math.min(8, distance + 1);
                horizontal[row + x] = distance * distance;
            }
            distance = 8;
            for (int x = stride; x >= 0; x--) {
                distance = horizontal[row + x] == 0 ? 0 : Math.min(8, distance + 1);
                horizontal[row + x] = Math.min(horizontal[row + x], distance * distance);
            }
        }
        float[] result = new float[width * height];
        for (int dz = 0; dz < height; dz++) for (int dx = 0; dx < width; dx++) {
            int x = startX + dx, z = startZ + dz;
            int depth = Math.min(Math.min(depths[z * stride + x], depths[z * stride + x - 1]),
                    Math.min(depths[(z - 1) * stride + x], depths[(z - 1) * stride + x - 1]));
            if (depth <= 1) continue;
            int nearest = 64;
            // Distances of eight blocks or more already have full attenuation.
            for (int row = Math.max(0, z - 7); row <= Math.min(rows, z + 7); row++) {
                int offset = row - z;
                nearest = Math.min(nearest, horizontal[row * vertexStride + x] + offset * offset);
            }
            double shore = Math.min(1, Math.sqrt(nearest) / 8);
            double shallow = Math.clamp((depth - 1) / 5.0, 0, 1);
            result[dz * width + dx] = (float)(shore * shore * (3 - 2 * shore)
                    * shallow * shallow * (3 - 2 * shallow));
        }
        return result;
    }
    public static float sample(BlockGetter level, int x, int z) {
        int surface = UnderworldWaterPhysics.surfaceBlockY(level,x,z);
        return surface == Integer.MIN_VALUE ? 0 : sample(level,x,surface,z);
    }
    public static float sample(BlockGetter level, int x, int surfaceY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos above=new BlockPos.MutableBlockPos();
        // A higher neighboring sea contains water at the lower Y, but is not part of
        // that exposed surface. Match the renderer's separate depth grids per tier.
        boolean tierEdge=Math.abs(LimboCascades.boundaryDistance(x,z))<14;
        int centerDepth = 6;
        for (int dz = -1; dz <= 0; dz++) for (int dx = -1; dx <= 0; dx++) {
            int depth = 0;
            if(tierEdge && level.getFluidState(above.set(x+dx,surfaceY+1,z+dz)).is(FluidTags.WATER))return 0;
            while (depth < 6 && level.getFluidState(pos.set(x + dx, surfaceY - depth, z + dz))
                    .is(FluidTags.WATER)) depth++;
            centerDepth = Math.min(centerDepth, depth);
        }
        if (centerDepth <= 1) return 0;
        boolean openWater = true;
        for (int dz = -8; dz < 8 && openWater; dz++) for (int dx = -8; dx < 8; dx++) {
            if (!level.getFluidState(pos.set(x + dx, surfaceY, z + dz)).is(FluidTags.WATER)
                    || tierEdge && level.getFluidState(above.set(x+dx,surfaceY+1,z+dz)).is(FluidTags.WATER)) {
                openWater = false;
                break;
            }
        }
        if (openWater) {
            double shallow = Math.clamp((centerDepth - 1) / 5.0, 0, 1);
            return (float)(shallow * shallow * (3 - 2 * shallow));
        }
        int[] depths = new int[18 * 18];
        for (int dz = 0; dz < 18; dz++) for (int dx = 0; dx < 18; dx++) {
            int depth = 0;
            if(tierEdge && level.getFluidState(above.set(x+dx-9,surfaceY+1,z+dz-9)).is(FluidTags.WATER))continue;
            while (depth < 6 && level.getFluidState(pos.set(x + dx - 9,
                    surfaceY - depth, z + dz - 9)).is(FluidTags.WATER)) depth++;
            depths[dz * 18 + dx] = depth;
        }
        return attenuation(depths, 18, 9, 9);
    }
}
