package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Organic moss islands with plants restricted to the moss-covered part of each clump. */
public final class AncientMossPatchFeature extends Feature<NoneFeatureConfiguration> {
    public AncientMossPatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!OvergrowthFeatureSupport.enabled(level, origin, "moss_patches")) return false;
        int placed = 0;
        int patches = 2 + random.nextInt(2);
        for (int patch = 0; patch < patches; patch++) {
            int centerX = origin.getX() + random.nextIntBetweenInclusive(-8, 8);
            int centerZ = origin.getZ() + random.nextIntBetweenInclusive(-8, 8);
            int radius = 2 + random.nextInt(4);
            int lobeX = random.nextIntBetweenInclusive(-radius / 2, radius / 2);
            int lobeZ = random.nextIntBetweenInclusive(-radius / 2, radius / 2);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double main = (dx * dx + dz * dz) / (double)(radius * radius);
                    double lobe = ((dx - lobeX) * (dx - lobeX) + (dz - lobeZ) * (dz - lobeZ))
                            / (double)Math.max(1, (radius - 1) * (radius - 1));
                    long texture = mix(level.getSeed() ^ (long)(centerX + dx) * 0x9E3779B97F4A7C15L
                            ^ (long)(centerZ + dz) * 0xD1B54A32D192ED03L);
                    double roughness = (Math.floorMod(texture, 1000L) / 1000.0D - 0.5D) * 0.24D;
                    // Broken islands leave generous ancient-stone paths between clumps.
                    if (Math.min(main, lobe) + roughness > 0.82D
                            || Math.floorMod(texture >>> 21, 9L) == 0L) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    BlockPos floor = OvergrowthFeatureSupport.findFloor(level, x, z);
                    if (floor == null || !OvergrowthFeatureSupport.enabled(level, floor, "moss_patches")
                            || !openForGrowth(level, floor.above())) continue;
                    boolean inner = Math.min(main, lobe) + roughness < 0.68D;
                    level.setBlock(floor, inner || Math.floorMod(texture >>> 12, 4) != 0
                            ? Asterion.ANCIENT_MOSS.defaultBlockState()
                            : Asterion.MOSSY_ANCIENT_STONE.defaultBlockState(), 2);
                    BlockPos growth = floor.above();
                    float plantRoll = random.nextFloat();
                    if (plantRoll < 0.22F)
                        level.setBlock(growth, Asterion.ANCIENT_MOSS_CARPET.defaultBlockState(), 2);
                    else if (plantRoll < 0.30F)
                        level.setBlock(growth, Asterion.SHORT_GRASS.defaultBlockState(), 2);
                    placed++;
                }
            }
        }
        return placed > 0;
    }

    private static boolean openForGrowth(WorldGenLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.above(2)).isAir();
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
