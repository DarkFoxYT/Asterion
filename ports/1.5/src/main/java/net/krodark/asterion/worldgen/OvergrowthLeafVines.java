package net.krodark.asterion.worldgen;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.phys.Vec3;

final class OvergrowthLeafVines {
    private static final int[][] DIRECTIONS={{1,0},{0,1},{1,1},{1,-1}};
    private OvergrowthLeafVines() { }
    static int place(WorldGenLevel level,RandomSource random,BlockPos origin) {
        if(random.nextInt(5)!=0)return 0;
        long seed=OvergrowthFeatureSupport.terrainSeed(level);
        for(int attempt=0;attempt<3;attempt++) {
            int x=origin.getX()+random.nextIntBetweenInclusive(-7,7);
            int z=origin.getZ()+random.nextIntBetweenInclusive(-7,7);
            BlockPos floor=OvergrowthFeatureSupport.findFloor(level,x,z);
            if(floor==null || !overgrown(seed,floor))continue;
            int available=AsterionConfig.INSTANCE.wallHeight;
            if(available<14)return 0;
            BlockPos center=floor.above(10+random.nextInt(Math.max(1,available-13)));
            int[] direction=DIRECTIONS[random.nextInt(DIRECTIONS.length)];
            BlockPos start=wall(level,seed,center,direction[0],direction[1]);
            BlockPos end=wall(level,seed,center,-direction[0],-direction[1]);
            if(start==null || end==null)continue;
            double distance=Vec3.atCenterOf(start).distanceTo(Vec3.atCenterOf(end));
            if(distance<6 || distance>30)continue;
            double sag=Math.min(7,distance*(.16+random.nextDouble()*.1));
            int thickness=3+random.nextInt(3);
            var blocks=HangingLeafSpan.blocks(start,end,thickness,sag);
            boolean clear=true;
            // Preflight the whole span so chunk boundaries/biome edges cannot cut it in half.
            for(BlockPos pos:blocks) {
                if(!OvergrowthFeatureSupport.canWrite(level,pos) || !overgrown(seed,pos)
                        || pos.getY()<WorldGenerator.mazeFloorHeight(seed,pos.getX(),pos.getZ())+4) {clear=false;break;}
            }
            if(!clear)continue;
            for(int step=2;step<distance*2-2;step++) {
                BlockPos pos=BlockPos.containing(HangingLeafSpan.point(Vec3.atCenterOf(start),Vec3.atCenterOf(end),step/(distance*2),sag));
                if(!OvergrowthFeatureSupport.isOpen(level,pos) && !level.getBlockState(pos).is(Asterion.ANCIENT_LEAVES)) {clear=false;break;}
            }
            if(!clear)continue;
            var leaves=Asterion.ANCIENT_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
            int placed=0;
            for(BlockPos pos:blocks)if(OvergrowthFeatureSupport.isOpen(level,pos)) {
                level.setBlock(pos,leaves,2);placed++;
            }
            return placed;
        }
        return 0;
    }
    private static boolean overgrown(long seed,BlockPos pos) {
        return WorldGenerator.mazeBiomeAt(seed,pos.getX(),pos.getZ(),AsterionConfig.INSTANCE.cellSize).kind()==MazeBiomes.Kind.OVERGROWTH;
    }
    private static BlockPos wall(WorldGenLevel level,long seed,BlockPos center,int dx,int dz) {
        for(int distance=1;distance<=18;distance++) {
            BlockPos pos=center.offset(dx*distance,0,dz*distance);
            if(!OvergrowthFeatureSupport.canWrite(level,pos) || !overgrown(seed,pos))return null;
            if(OvergrowthFeatureSupport.isMazeWall(level.getBlockState(pos)))return pos;
            if(!OvergrowthFeatureSupport.isOpen(level,pos) && !level.getBlockState(pos).is(Asterion.ANCIENT_LEAVES))return null;
        }
        return null;
    }
}
