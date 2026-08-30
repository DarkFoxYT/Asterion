package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Hanging bridge chains; every chain must begin beneath ancient wood. */
public final class OvergrowthBridgeChainFeature extends Feature<NoneFeatureConfiguration> {
    public OvergrowthBridgeChainFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!OvergrowthFeatureSupport.enabled(level, origin, "bridge_chains")) return false;
        int placed = 0;
        int minX = Math.floorDiv(origin.getX(), 16) * 16;
        int minZ = Math.floorDiv(origin.getZ(), 16) * 16;
        for (int x = minX; x < minX + 16; x++) {
            for (int z = minZ; z < minZ + 16; z++) {
                BlockPos floor = OvergrowthFeatureSupport.findFloor(level, x, z);
                if (floor == null) continue;
                int minY = floor.getY() + 6;
                int maxY = floor.getY() + AsterionConfig.INSTANCE.wallHeight - 2;
                for (int y = maxY; y >= minY; y--) {
                    BlockPos wood = new BlockPos(x, y, z);
                    var support = level.getBlockState(wood);
                    if (!support.is(Asterion.ANCIENT_PLANKS)
                            && !support.is(Asterion.ANCIENT_PLANK_SLAB)) continue;
                    if (!OvergrowthFeatureSupport.isOpen(level, wood.below()) || random.nextInt(9) != 0) break;
                    int length = 3 + random.nextInt(7);
                    for (int drop = 1; drop <= length; drop++) {
                        BlockPos chain = wood.below(drop);
                        if (!OvergrowthFeatureSupport.isOpen(level, chain)) break;
                        level.setBlock(chain, Asterion.MAZESTEEL_CHAIN.defaultBlockState(), 2);
                        placed++;
                    }
                    break;
                }
            }
        }
        return placed > 0;
    }
}
