package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.block.PillarBlock;
import net.krodark.asterion.block.PillarBlockEntity;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

final class PillarCheck {
    static void integration(net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext context) {
        try(var world=context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level=server.overworld(); var player=server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level,22.5,121,-8,java.util.Set.of(),0,0,true);
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                for(int x=20;x<=24;x++) for(int z=-2;z<=2;z++) level.setBlock(new BlockPos(x,120,z),Blocks.STONE.defaultBlockState(),2);
                run(level,player);
                var maze=server.getLevel(Asterion.ASTERION_LEVEL);
                arena(maze);
                var root=firstArenaRoot(maze);
                check(net.krodark.asterion.WorldGenerator.breakBossPillar(maze,new AABB(root)),"Boss could not destroy new pillar");
                for(int y=0;y<27;y++) check(!maze.getBlockState(root.above(y)).is(Asterion.PILLAR),"Boss left upper pillar fragments");
                Asterion.LOGGER.info("PASS: authored arena pillar generation and boss destruction");
            });
            context.waitTicks(5);
        }
    }
    static void arena(ServerLevel level) {
        net.krodark.asterion.worldgen.AuthoredCatacombs.ensureArenaPillars(level);
        int found = 0;
        for (int x = -61; x <= 61; x++) for (int z = -61; z <= 61; z++)
            for (int y = 1; y <= 48; y++) {
                BlockPos root = new BlockPos(x, y, z);
                var state = level.getBlockState(root);
                if (!state.is(Asterion.PILLAR) || !PillarBlock.isRoot(state)) continue;
                found++;
            }
        check(found >= 4, "Authored arena did not expose enough dynamic pillars: " + found);
    }
    static BlockPos firstArenaRoot(ServerLevel level) {
        for (int x = -61; x <= 61; x++) for (int z = -61; z <= 61; z++)
            for (int y = 1; y <= 48; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos).is(Asterion.PILLAR)
                        && PillarBlock.isRoot(level.getBlockState(pos))) return pos;
            }
        throw new AssertionError("Authored arena has no dynamic pillar root");
    }
    static void run(ServerLevel level, ServerPlayer player) {
        BlockPos root = new BlockPos(22,121,0);
        var item = (BlockItem) Asterion.PILLAR.asItem();
        var stack = new ItemStack(item,2);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(root),Direction.UP,root.below(),false);
        var context = new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,hit);
        level.setBlock(root.above(26).east(),Blocks.STONE.defaultBlockState(),2);
        item.place(context);
        check(!level.getBlockState(root).is(Asterion.PILLAR) && stack.getCount()==2,"Obstructed pillar partly placed or consumed item");
        level.setBlock(root.above(26).east(),Blocks.AIR.defaultBlockState(),2);
        item.place(context);
        verify(level,root,27);
        var edge = root.west().above(8);
        var shape = level.getBlockState(edge).getCollisionShape(level,edge).bounds();
        check(shape.minX==0.5 && shape.maxX==1 && shape.minZ==0 && shape.maxZ==1,"Shaft collision does not match model");
        var base = root.west();
        check(level.getBlockState(base).getCollisionShape(level,base).bounds().getXsize()==1,"Base collision too narrow");
        player.gameMode.destroyBlock(edge);
        for(int x=0;x<3;x++) for(int y=0;y<27;y++) for(int z=0;z<3;z++)
            check(!level.getBlockState(PillarBlock.part(root,x,y,z)).is(Asterion.PILLAR),"Breaking side left pillar remnants");
        Asterion.LOGGER.info("PASS: pillar placement clearance, 243 parts, single renderer anchor, exact shaft/base collisions and whole-pillar removal");
    }
    private static void verify(ServerLevel level,BlockPos root,int height) {
        int entities=0;
        for(int x=0;x<3;x++) for(int y=0;y<height;y++) for(int z=0;z<3;z++) {
            BlockPos pos=PillarBlock.part(root,x,y,z);
            var state=level.getBlockState(pos);
            check(state.is(Asterion.PILLAR) && PillarBlock.root(pos,state).equals(root),"Missing or incorrectly linked pillar part: "+pos);
            if(level.getBlockEntity(pos)!=null) entities++;
        }
        check(entities==1 && level.getBlockEntity(root) instanceof PillarBlockEntity,"Pillar must have exactly one renderer anchor");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
