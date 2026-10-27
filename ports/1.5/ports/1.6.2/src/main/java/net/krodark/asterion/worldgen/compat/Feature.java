package net.krodark.asterion.worldgen.compat;

//? if >=26.3 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

// Asterion's features have no configuration. Keep the generation algorithm and
// expose each instance as a native 26.3 feature type with a unit codec.
public abstract class Feature<C> implements net.minecraft.world.level.levelgen.feature.Feature {
    protected Feature(Codec<C> ignored) { }
    @Override public abstract MapCodec<? extends Feature<C>> codec();
    @Override public final boolean place(WorldGenLevel world, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        return place(new FeaturePlaceContext<>(world, generator, random, origin));
    }
    public abstract boolean place(FeaturePlaceContext<C> context);
}
*///?}
