package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

/** A continuous deck across the authored arena boundary, including old erased chunk strips. */
public final class ArenaSurfaceSeam {
    public static final int APPROACH_RADIUS = AuthoredCatacombs.ARENA_RADIUS + 24;
    private ArenaSurfaceSeam() { }
    public static boolean approach(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) <= APPROACH_RADIUS;
    }
    public static void repair(ServerLevel level, LevelChunk chunk, boolean force) {
        var cp=chunk.getPos();
        if(cp.getMinBlockX()>APPROACH_RADIUS || cp.getMaxBlockX() < -APPROACH_RADIUS
                || cp.getMinBlockZ()>APPROACH_RADIUS || cp.getMaxBlockZ() < -APPROACH_RADIUS) return;
        var marker=new BlockPos(cp.getMinBlockX(),LabyrinthLevels.ARENA_BASE_Y-4,cp.getMinBlockZ());
        var complete=Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL,1);
        if(!force && chunk.getBlockState(marker).equals(complete)) return;
        var cursor=new BlockPos.MutableBlockPos();
        int roof=LabyrinthLevels.ARENA_ROOF_Y;
        for(int x=Math.max(-APPROACH_RADIUS,cp.getMinBlockX());x<=Math.min(APPROACH_RADIUS,cp.getMaxBlockX());x++)
            for(int z=Math.max(-APPROACH_RADIUS,cp.getMinBlockZ());z<=Math.min(APPROACH_RADIUS,cp.getMaxBlockZ());z++) {
                int edge=Math.max(Math.abs(x),Math.abs(z));
                if(edge<=AuthoredCatacombs.ARENA_RADIUS) {
                    cursor.set(x,roof,z);
                    var top=chunk.getBlockState(cursor);
                    var below=chunk.getBlockState(cursor.below());
                    // Preserve the central entrance and unsupported openings left by roof collapse.
                    if(!top.hasBlockEntity() && top.getFluidState().isEmpty()
                            && (!top.isAir() || edge>24 && below.isCollisionShapeFullBlock(level,cursor.below())))
                        level.setBlock(cursor,Asterion.ANCIENT_STONE.defaultBlockState(),18);
                    continue;
                }
                cursor.set(x,roof+1,z);
                var raised=chunk.getBlockState(cursor);
                var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(raised.getBlock());
                if(!raised.hasBlockEntity() && id.getNamespace().equals("asterion")
                        && id.getPath().startsWith("ancient_") && chunk.getBlockState(cursor.above()).isAir())
                    level.setBlock(cursor,Blocks.AIR.defaultBlockState(),18);
                for(int depth=0;depth<4;depth++) {
                    cursor.set(x,roof-depth,z);
                    if(!chunk.getBlockState(cursor).hasBlockEntity())
                        level.setBlock(cursor,Asterion.ANCIENT_STONE.defaultBlockState(),18);
                }
            }
        level.setBlock(marker,complete,18);
    }
}
