package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.CatacombFloodState;
import net.krodark.asterion.fluid.HeavyWater;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Raised floors, chunk seams, newly opened passages and complete recession in real block palettes. */
public final class FloodSpreadCheck {
    public static void run(ServerLevel level) {
        int base = CatacombLayout.WATER_Y;
        for (BlockPos p : BlockPos.betweenClosed(318, base - 4, 318, 339, base + 7, 326))
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 18);
        for (BlockPos p : BlockPos.betweenClosed(319, base + 3, 320, 337, base + 4, 320))
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 18);
        for (int y = base; y <= base + 4; y++) level.setBlock(new BlockPos(319, y, 320), Blocks.AIR.defaultBlockState(), 18);
        BlockPos inlet = new BlockPos(319, base, 320);
        level.setBlock(inlet, Blocks.WATER.defaultBlockState(), 18);
        // A lower pocket also needs to fill, not retain an air bubble below the old baseline.
        for (int y = base - 3; y <= base + 3; y++) level.setBlock(new BlockPos(320, y, 320), Blocks.AIR.defaultBlockState(), 18);
        BlockPos door = new BlockPos(329, base + 3, 320);
        level.setBlock(door, Blocks.STONE.defaultBlockState(), 18);
        level.setBlock(door.above(), Blocks.STONE.defaultBlockState(), 18);
        BlockPos sealed = new BlockPos(337, base + 3, 324);
        level.setBlock(sealed, Blocks.AIR.defaultBlockState(), 18);
        BlockPos slab = new BlockPos(333, base + 3, 320);
        level.setBlock(slab, Blocks.STONE_SLAB.defaultBlockState(), 18);
        reconcile(level, 32);
        check(level.getBlockState(new BlockPos(327, base + 3, 320)).isAir(), "Raised room filled instantly instead of spreading");
        for (int wave = 0; wave < 100; wave++) CatacombFloodState.spread(level, 32);
        check(level.getBlockState(new BlockPos(327, base + 3, 320)).is(HeavyWater.BLOCK), "Water did not cross raised floor/chunk seam");
        check(level.getBlockState(new BlockPos(320, base - 3, 320)).is(HeavyWater.BLOCK), "Lower pocket retained air");
        check(level.getBlockState(new BlockPos(335, base + 3, 320)).isAir(), "Water crossed a solid doorway");
        level.setBlock(door, Blocks.AIR.defaultBlockState(), 18);
        level.setBlock(door.above(), Blocks.AIR.defaultBlockState(), 18);
        reconcile(level, 32);
        for (int wave = 0; wave < 100; wave++) CatacombFloodState.spread(level, 32);
        check(level.getBlockState(new BlockPos(337, base + 3, 320)).is(HeavyWater.BLOCK), "Newly opened room remained dry");
        check(HeavyWaterlogging.isTidal(level.getBlockState(slab)), "Water did not enter the slab");
        check(level.getBlockState(sealed).isAir(), "Flood crossed sealed room walls");
        // Reconciliation at an unchanged tide must catch a newly accessible/reloaded room edge.
        BlockPos late = new BlockPos(337, base + 3, 321);
        level.setBlock(late, Blocks.AIR.defaultBlockState(), 18);
        reconcile(level, 32);
        CatacombFloodState.spread(level, 32);
        check(level.getBlockState(late).is(HeavyWater.BLOCK), "Steady tide did not revisit dry openings");
        reconcile(level, 0);
        CatacombFloodState.spread(level, 0);
        check(level.getBlockState(late).isAir(), "Recession left room water behind");
        check(level.getBlockState(new BlockPos(320, base - 3, 320)).isAir(), "Recession left pocket water behind");
        check(!HeavyWaterlogging.isTidal(level.getBlockState(slab)), "Slab stayed flooded");
        check(level.getBlockState(inlet).is(HeavyWater.WATER_BLOCK), "Recession erased original water");
        Asterion.LOGGER.info("PASS: gradual lateral flood, raised floors, lower pockets, chunk seams, closed/open passages, waterlogging and recession");
    }
    private static void reconcile(ServerLevel level, int rise) {
        for (int cx = 19; cx <= 21; cx++) for (int cz = 19; cz <= 20; cz++)
            CatacombFloodState.reconcile(level, level.getChunk(cx, cz), rise);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
