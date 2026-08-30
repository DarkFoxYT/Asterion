package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.LabyrinthVineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Luminous tendrils growing upward only from moss-covered maze floor. */
public final class AncientGroundVineFeature extends Feature<NoneFeatureConfiguration> {
    public AncientGroundVineFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!OvergrowthFeatureSupport.enabled(level, origin, "ground_vines")) return false;
        int placed = 0;
        int attempts = 18 + random.nextInt(12);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = origin.getX() + random.nextIntBetweenInclusive(-7, 7);
            int z = origin.getZ() + random.nextIntBetweenInclusive(-7, 7);
            BlockPos floor = OvergrowthFeatureSupport.findFloor(level, x, z);
            if (floor == null || !OvergrowthFeatureSupport.enabled(level, floor, "ground_vines")) continue;
            var floorState = level.getBlockState(floor);
            if (!floorState.is(Asterion.ANCIENT_MOSS)
                    && !floorState.is(Asterion.MOSSY_ANCIENT_STONE)) continue;
            int wantedLength = 2 + random.nextInt(4);
            int length = 0;
            while (length < wantedLength
                    && OvergrowthFeatureSupport.isOpen(level, floor.above(length + 1))) length++;
            if (length == 0) continue;
            for (int rise = 1; rise <= length; rise++) {
                BlockPos vine = floor.above(rise);
                level.setBlock(vine, Asterion.LABYRINTH_VINE.defaultBlockState()
                        .setValue(LabyrinthVineBlock.FACING, Direction.UP)
                        .setValue(LabyrinthVineBlock.END, rise == length), 2);
                placed++;
            }
        }
        return placed > 0;
    }
}
