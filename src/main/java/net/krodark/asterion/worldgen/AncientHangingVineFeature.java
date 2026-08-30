package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Downward vines attached only to the approved moss and ancient-leaf supports. */
public final class AncientHangingVineFeature extends Feature<NoneFeatureConfiguration> {
    public AncientHangingVineFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!OvergrowthFeatureSupport.enabled(level, origin, "hanging_vines")) return false;
        int placed = 0;
        int attempts = 22 + random.nextInt(14);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = origin.getX() + random.nextIntBetweenInclusive(-7, 7);
            int z = origin.getZ() + random.nextIntBetweenInclusive(-7, 7);
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level, x, z);
            if (floor == null) continue;
            int maxY = floor.getY() + AsterionConfig.INSTANCE.wallHeight + 1;
            BlockPos support = null;
            for (int y = maxY; y >= floor.getY() + 5; y--) {
                BlockPos candidate = new BlockPos(x, y, z);
                var state = level.getBlockState(candidate);
                if ((state.is(Asterion.ANCIENT_MOSS) || state.is(Asterion.ANCIENT_LEAVES)
                        || state.is(Asterion.MOSSY_ANCIENT_STONE)
                        || state.is(Asterion.ANCIENT_MOSSY_BRICKS)
                        || state.is(Asterion.DEAD_WOOD))
                        && OvergrowthFeatureSupport.isOpen(level, candidate.below())) {
                    support = candidate;
                    break;
                }
            }
            if (support == null || !OvergrowthFeatureSupport.enabled(level, support, "hanging_vines")) continue;
            int wantedLength = 3 + random.nextInt(8);
            int length = 0;
            while (length < wantedLength
                    && OvergrowthFeatureSupport.isOpen(level, support.below(length + 1))) length++;
            if (length == 0) continue;
            for (int drop = 1; drop <= length; drop++) {
                BlockPos vine = support.below(drop);
                level.setBlock(vine, Asterion.LABYRINTH_VINE.defaultBlockState()
                        .setValue(LabyrinthVineBlock.FACING, Direction.DOWN)
                        .setValue(LabyrinthVineBlock.END, drop == length), 2);
                placed++;
            }
        }
        return placed > 0;
    }
}
