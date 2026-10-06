package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

 
public final class CatacombProtection {
    private CatacombProtection() { }

    /** Keep authored controls usable even when a crossing floods. No scans or ticking. */
    public static boolean waterproofLever(net.minecraft.world.level.BlockGetter level, BlockState state) {
        return level instanceof Level world && waterproofLever(world.dimension(), state);
    }
    public static boolean waterproofLever(net.minecraft.resources.ResourceKey<Level> dimension, BlockState state) {
        return dimension.equals(Asterion.ASTERION_LEVEL)
                && state.getBlock() instanceof net.minecraft.world.level.block.LeverBlock;
    }

    public static boolean contains(Level level, BlockPos pos) {
        return level.dimension().equals(Asterion.ASTERION_LEVEL)
                && CatacombLayout.contains(pos);
    }

     
    public static boolean isOre(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "ores")))
                || id.getPath().endsWith("_ore") || id.getPath().equals("ancient_debris");
    }
}
