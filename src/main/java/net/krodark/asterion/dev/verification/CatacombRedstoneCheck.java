package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.AuthoredCatacombs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.Fluids;

/** Real authored circuitry, rotated and split across chunk boundaries in both orders. */
final class CatacombRedstoneCheck {
    private static final Block[] CIRCUIT = {Blocks.REDSTONE_WIRE, Blocks.REPEATER,
            Blocks.COMPARATOR, Blocks.REDSTONE_TORCH, Blocks.REDSTONE_WALL_TORCH,
            Blocks.LEVER, Blocks.PALE_OAK_BUTTON, Blocks.STICKY_PISTON,
            Blocks.PISTON, Blocks.OBSERVER, Blocks.REDSTONE_LAMP};

    static void run(ServerLevel level) {
        int verified=0, index=0;
        for(String name : new String[]{"puzzleroom","crossing_01","crossing_02","corridor_t_02"})
            for(Rotation rotation : Rotation.values()) {
                var template=level.getStructureManager().get(Asterion.id("catacombs/"+name)).orElseThrow();
                // Deliberately offset from chunk boundaries, like normal 19-block placement.
                BlockPos origin=new BlockPos(3+index++*32,160,35);
                var whole=new BoundingBox(origin.getX(),160,origin.getZ(),origin.getX()+18,190,origin.getZ()+18);
                var settings=AuthoredCatacombs.settings(whole).setRotation(rotation).setRotationPivot(new BlockPos(9,0,9));
                var chunks=new java.util.ArrayList<BoundingBox>();
                for(int cx=Math.floorDiv(whole.minX(),16);cx<=Math.floorDiv(whole.maxX(),16);cx++)
                    for(int cz=Math.floorDiv(whole.minZ(),16);cz<=Math.floorDiv(whole.maxZ(),16);cz++) {
                        level.getChunk(cx,cz);
                        chunks.add(new BoundingBox(cx*16,160,cz*16,cx*16+15,190,cz*16+15));
                    }
                if((index&1)==0)java.util.Collections.reverse(chunks);
                for(var clip:chunks)template.placeInWorld(level,origin,origin,settings.copy().setBoundingBox(clip),RandomSource.create(index),18);
                for(Block block:CIRCUIT)for(var info:template.filterBlocks(origin,settings,block,true)) {
                    var actual=level.getBlockState(info.pos());
                    require(actual.is(block),name+" lost "+block+" at "+info.pos()+" in "+rotation+": "+actual);
                    for(var property:info.state().getProperties())if(property.getName().equals("facing") || property.getName().equals("delay"))
                        require(actual.getValue(property).equals(info.state().getValue(property)),"Circuit direction/delay changed at "+info.pos());
                    verified++;
                }
            }
        // Exercise the actual empty-fluid bug, independently of optimized template settings.
        BlockPos wire=new BlockPos(0,150,60);
        level.setBlock(wire.below(),Blocks.STONE.defaultBlockState(),18);
        level.setBlock(wire,Blocks.REDSTONE_WIRE.defaultBlockState(),18);
        var state=level.getBlockState(wire);
        var container=(LiquidBlockContainer)state.getBlock();
        require(!container.placeLiquid(level,wire,state,Fluids.EMPTY.defaultFluidState()),"Empty fluid was accepted");
        require(level.getBlockState(wire).is(Blocks.REDSTONE_WIRE),"Empty fluid deleted wire");
        // A newly placed dry circuit must also respond to an actual power change.
        level.setBlock(wire.west(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        require(level.getBlockState(wire).getValue(RedStoneWireBlock.POWER)>0,"Placed redstone does not conduct");
        level.setBlock(wire.west(),Blocks.AIR.defaultBlockState(),3);
        require(level.getBlockState(wire).getValue(RedStoneWireBlock.POWER)==0,"Wire stayed powered after source removal");
        Asterion.LOGGER.info("PASS: {} authored circuit blocks survived all rotations and split placement; empty-fluid safety and live wire power verified",verified);
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
