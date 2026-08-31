package net.krodark.asterion.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/** Support is restricted independently of physical collisions, so fences still block bugs. */
public final class BugSurfaces {
    private BugSurfaces() { }
    public static boolean allowed(BlockGetter level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isCollisionShapeFullBlock(level, pos) || state.getBlock() instanceof SlabBlock
                || state.getBlock() instanceof StairBlock;
    }
    public static List<AABB> collect(BlockGetter level, AABB bounds) {
        var result = new ArrayList<AABB>();
        for (BlockPos pos : BlockPos.betweenClosed((int)Math.floor(bounds.minX), (int)Math.floor(bounds.minY), (int)Math.floor(bounds.minZ),
                (int)Math.floor(bounds.maxX), (int)Math.floor(bounds.maxY), (int)Math.floor(bounds.maxZ))) {
            if (!allowed(level, pos)) continue;
            for (AABB shape : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                AABB world = shape.move(pos);
                if (world.intersects(bounds)) result.add(world);
            }
        }
        return result;
    }
    public static boolean touches(BlockGetter level, AABB bounds) { return !collect(level, bounds).isEmpty(); }
}
