package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.CatacombProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import java.util.Set;

final class CatacombProtectionCheck {
    static void run(MinecraftServer server, ServerPlayer player) {
        var original = player.level();
        Vec3 oldPos = player.position();
        var oldMode = player.gameMode.getGameModeForPlayer();
        ItemStack oldItem = player.getMainHandItem().copy();
        var maze = server.getLevel(Asterion.ASTERION_LEVEL);
        BlockPos pos = new BlockPos(320, 8, 320);
        try {
            player.teleportTo(maze,324.5,8,320.5,Set.of(),0,0,true);
            for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
                player.setGameMode(mode);
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,16));
                maze.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
                check(player.gameMode.destroyBlock(pos) && maze.getBlockState(pos).isAir(),
                        "Player could not temporarily break catacomb masonry in " + mode);
                maze.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                maze.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false);
                ((BlockItem)Items.STONE).place(new BlockPlaceContext(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)));
                check(maze.getBlockState(pos).is(Blocks.STONE),"Player placement was rejected in catacombs");
                maze.setBlock(pos,Blocks.DIAMOND_ORE.defaultBlockState(),2);
                check(player.gameMode.destroyBlock(pos) && maze.getBlockState(pos).isAir(),"Player could not permanently mine catacomb ore in " + mode);
                check(!((BucketItem)Items.WATER_BUCKET).emptyContents(player,maze,pos,null),"Bucket bypassed protection");
                check(!player.mayUseItemAt(new BlockPos(320,net.krodark.asterion.worldgen.CatacombLayout.ROOF_Y - 1,320),Direction.UP,new ItemStack(Items.BUCKET)),"Bucket can drain protected roof from outside");
            }
            var lever = Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,AttachFace.FLOOR);
            maze.setBlock(pos,lever,2);
            lever.useWithoutItem(maze,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
            check(maze.getBlockState(pos).getValue(BlockStateProperties.POWERED),"Protection disabled switches");
            check(!CatacombProtection.contains(maze,pos.atY(net.krodark.asterion.worldgen.CatacombLayout.ROOF_Y + 1)) && !CatacombProtection.contains(maze,pos.atY(2)),"Protection extended beyond catacombs");
            maze.setBlock(pos,Blocks.WATER.defaultBlockState(),2);
            check(maze.getBlockState(pos).is(Blocks.WATER),"Protection blocked server world updates");
            player.teleportTo(original,oldPos.x,oldPos.y,oldPos.z,Set.of(),0,0,true);
            original.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
            check(player.gameMode.destroyBlock(pos),"Protection leaked into overworld");
            Asterion.LOGGER.info("PASS: catacomb masonry can be broken for timed repair; buckets stay protected, ores remain permanent and temporary placement is accepted");
        } finally {
            player.teleportTo(original,oldPos.x,oldPos.y,oldPos.z,Set.of(),0,0,true);
            player.setGameMode(oldMode);
            player.setItemInHand(InteractionHand.MAIN_HAND,oldItem);
        }
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
