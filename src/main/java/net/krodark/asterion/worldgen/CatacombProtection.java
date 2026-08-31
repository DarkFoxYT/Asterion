package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

/** Protect player edits, not generation, puzzle updates, or the flooding event. */
public final class CatacombProtection {
    private CatacombProtection() { }

    public static boolean contains(Level level, BlockPos pos) {
        return level.dimension().equals(Asterion.ASTERION_LEVEL)
                && CatacombLayout.contains(pos);
    }

    /** Convention-tagged and vanilla-named ores are permanent player edits. */
    public static boolean isOre(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return state.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "ores")))
                || id.getPath().endsWith("_ore") || id.getPath().equals("ancient_debris");
    }
}
