package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Protect player edits, not generation, puzzle updates, or the flooding event. */
public final class CatacombProtection {
    private CatacombProtection() { }

    public static boolean contains(Level level, BlockPos pos) {
        return level.dimension().equals(Asterion.ASTERION_LEVEL)
                && CatacombLayout.contains(pos);
    }
}
