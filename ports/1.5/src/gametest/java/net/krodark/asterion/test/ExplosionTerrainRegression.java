package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

final class ExplosionTerrainRegression {
    private static final BlockPos POS = new BlockPos(80,180,80);
    private static long blastedAt;
    static volatile boolean passed;

    static void prepare(ServerPlayer player) {
        var maze=player.getServer().getLevel(Asterion.ASTERION_LEVEL);
        GameplaySmoke.require(maze!=null,"Labyrinth missing from runtime world");
        maze.setChunkForced(5,5,true);
        for (var pos:java.util.List.of(POS,POS.east(),POS.west())) maze.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        WorldGenerator.trackPlayerPlacement(maze,POS.east(),Blocks.STONE.defaultBlockState());
        maze.explode(null,80.5,180.5,80.5,12F,Level.ExplosionInteraction.TNT);
        GameplaySmoke.require(maze.getBlockState(POS).isAir(),"Explosion must destroy terrain initially");
        maze.setBlockAndUpdate(POS.west(),Blocks.GOLD_BLOCK.defaultBlockState());
        WorldGenerator.trackPlayerPlacement(maze,POS.west(),Blocks.GOLD_BLOCK.defaultBlockState());
        var looseItem=new net.minecraft.world.entity.item.ItemEntity(maze,80.5,180.2,80.5,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
        looseItem.setNoGravity(true);
        looseItem.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        maze.addFreshEntity(looseItem);
        blastedAt=maze.getGameTime();
    }

    static void verify(ServerPlayer player) {
        var maze=player.getServer().getLevel(Asterion.ASTERION_LEVEL);
        if (maze.getGameTime()-blastedAt<130) return;
        GameplaySmoke.require(maze.getBlockState(POS).is(Blocks.STONE),"Blasted terrain must regrow");
        GameplaySmoke.require(maze.getBlockState(POS.east()).isAir(),"Player-built terrain must not regrow");
        GameplaySmoke.require(maze.getBlockState(POS.west()).is(Blocks.GOLD_BLOCK),"Replacement must survive restoration");
        maze.setChunkForced(5,5,false);
        passed=true;
        Asterion.LOGGER.info("ASTERION_EXPLOSION PASS: terrain regrows, player-built blocks do not, replacement preserved");
    }
}
