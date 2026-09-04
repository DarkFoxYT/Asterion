package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.krodark.asterion.worldgen.LabyrinthLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Crossing placement must not cut the nearby wall or clear its upper lintel. */
final class CrossingSurfaceCheck {
    static void run(ServerLevel level) {
        int index=0,checked=0;
        for(String name:new String[]{"crossing_01","crossing_02"})for(Rotation rotation:Rotation.values()) {
            BlockPos origin=new BlockPos(3+index++*32,AuthoredCatacombs.BASE_Y,131);
            long seed=94213L+index;
            var template=level.getStructureManager().get(Asterion.id("catacombs/"+name)).orElseThrow();
            var walls=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(int x=0;x<19;x++)for(int z=0;z<19;z++) {
                int wx=origin.getX()+x,wz=origin.getZ()+z;
                int floor=WorldGenerator.mazeFloorHeight(seed,wx,wz);
                for(int y=LabyrinthLevels.MAZE_FLOOR_Y;y<=floor;y++)
                    level.setBlock(new BlockPos(wx,y,wz),Blocks.STONE.defaultBlockState(),18);
                // Two flanking walls and a wall directly above the hatch recess.
                if(x==4 || x==14 || x>=8 && x<=10 && z==9)
                    for(int y=floor+1;y<=LabyrinthLevels.MAZE_FLOOR_Y+17;y++) {
                    BlockPos pos=new BlockPos(wx,y,wz);
                    level.setBlock(pos,Blocks.DEEPSLATE_BRICKS.defaultBlockState(),18);
                    if(x==4 || x==14 || y>floor+2)walls.put(pos,level.getBlockState(pos));
                }
            }
            var chunks=new java.util.ArrayList<ChunkPos>();
            for(int cx=Math.floorDiv(origin.getX(),16);cx<=Math.floorDiv(origin.getX()+18,16);cx++)
                for(int cz=Math.floorDiv(origin.getZ(),16);cz<=Math.floorDiv(origin.getZ()+18,16);cz++)chunks.add(new ChunkPos(cx,cz));
            if((index&1)==0)java.util.Collections.reverse(chunks);
            for(var chunk:chunks) {
                var clip=new BoundingBox(chunk.getMinBlockX(),AuthoredCatacombs.BASE_Y,chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(),AuthoredCatacombs.BASE_Y+30,chunk.getMaxBlockZ());
                template.placeInWorld(level,origin,origin,AuthoredCatacombs.placementSettings(clip,true)
                        .setRotation(rotation).setRotationPivot(new BlockPos(9,0,9)),RandomSource.create(seed),18);
                AuthoredCatacombs.surfaceApproach(level,chunk,origin,seed);
            }
            for(var entry:walls.entrySet()) {
                if(level.getBlockState(entry.getKey())!=entry.getValue())throw new AssertionError("Crossing cut maze wall at "+entry.getKey()+" / "+rotation);
                checked++;
            }
            var settings=AuthoredCatacombs.settings(new BoundingBox(origin.getX(),AuthoredCatacombs.BASE_Y,
                    origin.getZ(),origin.getX()+18,AuthoredCatacombs.BASE_Y+30,origin.getZ()+18))
                    .setRotation(rotation).setRotationPivot(new BlockPos(9,0,9));
            for(var lever:template.filterBlocks(origin,settings,Blocks.LEVER,true))
                if(!level.getBlockState(lever.pos()).is(Blocks.LEVER))throw new AssertionError("Surface adaptation removed hatch controls");
            if(!level.getBlockState(origin.offset(9,31,9)).isAir())throw new AssertionError("Hatch headroom blocked");
        }
        Asterion.LOGGER.info("PASS: {} maze wall/lintel blocks preserved around both crossings, all rotations and split chunk orders; controls and hatch headroom intact",checked);
    }
}
