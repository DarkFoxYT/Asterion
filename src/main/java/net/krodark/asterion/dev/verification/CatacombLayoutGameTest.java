package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.worldgen.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.util.RandomSource;

/** Tests the imported assets in a disposable game world, not just their filenames. */
public final class CatacombLayoutGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        try(var world=context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level=server.getLevel(Asterion.ASTERION_LEVEL);
                // Runtime installs arena pieces from completed chunk callbacks. This test
                // intentionally forces all pieces so it can inspect the entire 123x123 build.
                for(int cx=-4;cx<=3;cx++)for(int cz=-4;cz<=3;cz++)
                    AuthoredCatacombs.placeArenaChunk(level,level.getChunk(cx,cz));
                for(int cx=-1;cx<=5;cx++)for(int cz=3;cz<=5;cz++)
                    AuthoredCatacombs.placeArenaChunk(level,level.getChunk(cx,cz));
                check(WorldGenerator.isBossArenaReady(),"Arena not ready");
                check(level.getBlockState(new BlockPos(0,6,0)).is(Asterion.ANCIENT_MOSSY_BRICKS),"Authored arena center was overwritten");
                check(level.getBlockState(new BlockPos(0,7,0)).isAir(),"Arena center obstructed");
                for(int x=-1;x<=1;x++)for(int y=6;y<=10;y++)
                    check(!level.getBlockState(new BlockPos(x,y,-60)).is(Blocks.CYAN_WOOL),
                            "Arena exit portal marker remained as cyan wool");
                check(level.getBlockEntity(MinotaurArenaEntrances.door(net.minecraft.core.Direction.SOUTH)) instanceof net.krodark.asterion.block.MinotaurDoorBlockEntity,"Single keyed arena entrance missing");
                check(MinotaurArenaEntrances.DOORS.size()==1,"Extra arena entrance");
                check(WorldGenerator.activeBossBraziers(level)==0,"Generated braziers overwrote the authored arena");
                check(WorldGenerator.bossPillarsRemaining()==0,"Generated pillars were added to the authored arena");
                int closedDoorPieces=0;
                for(int x=-61;x<=61;x++)for(int z=-61;z<=61;z++)for(int y=1;y<=48;y++) {
                    var state=level.getBlockState(new BlockPos(x,y,z));
                    if(state.is(Asterion.BARREL_DOOR)) {
                        closedDoorPieces++;
                        check(!state.getValue(net.krodark.asterion.block.BarrelDoorBlock.OPEN)
                                && !state.getValue(net.krodark.asterion.block.BarrelDoorBlock.WING),
                                "Authored arena barrel door was not placed closed");
                    }
                }
                for (int x=-42;x<=42;x++) for (int z=-42;z<=42;z++) for (int y=7;y<=34;y++) {
                    var state=level.getBlockState(new BlockPos(x,y,z));
                    check(!state.is(Asterion.PILLAR) && !state.is(Asterion.GREEK_BRAZIER) && !state.is(Asterion.LAMENTER),
                            "Procedural fixture added inside the supplied arena at "+x+","+y+","+z);
                }
                level.getChunkAt(new BlockPos(CatacombLayout.ROOT_CENTER,49,CatacombLayout.ROOT_CENTER));
                check(CatacombEntrances.checkpoint(level,new BlockPos(CatacombLayout.ROOT_CENTER,24,CatacombLayout.ROOT_CENTER))!=null,"Authored crossing lost its safe checkpoint");
                for(int cx=-4;cx<=3;cx++)for(int cz=-4;cz<=3;cz++)
                    check(level.getBlockState(new BlockPos(cx*16,0,cz*16)).is(Blocks.LIGHT),"Missing arena reload marker");
                for(int z=62;z<=CatacombLayout.ROOT_CENTER;z++) clear(level,new BlockPos(0,AuthoredCatacombs.CONNECTOR_Y+1,z));
                for(int x=0;x<=CatacombLayout.ROOT_CENTER-10;x++) clear(level,new BlockPos(x,AuthoredCatacombs.CONNECTOR_Y+1,CatacombLayout.ROOT_CENTER));
                long seed=MazeChunkGenerator.terrainSeed(level.getChunkSource().randomState());
                // Force full generation, including modules spanning multiple chunks.
                for(int cz=19;cz>=16;cz--)for(int cx=19;cx>=16;cx--)level.getChunk(cx,cz);
                for(int tx=14;tx<=15;tx++)for(int tz=14;tz<=15;tz++) {
                    if(!CatacombLayout.occupied(seed,tx,tz))continue;
                    var module=AuthoredCatacombs.module(seed,tx,tz);
                    var template=level.getStructureManager().get(Asterion.id("catacombs/"+module.name())).orElseThrow();
                    check(template.getSize().getY()==31,"Old placeholder loaded");
                    for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL) if(CatacombLayout.connected(seed,tx,tz,side)) {
                        BlockPos seam=new BlockPos(tx*19+9,AuthoredCatacombs.CONNECTOR_Y,tz*19+9).relative(side,9);
                        check(!level.getBlockState(seam).is(Blocks.JIGSAW),"Unreplaced connector");
                        clear(level,seam); clear(level,seam.relative(side));
                    }
                }
                // Every variant is also loaded and placed, catching missing blocks and bad NBT.
                int index=0;
                for(String name:AuthoredCatacombs.TEMPLATES) {
                    var template=level.getStructureManager().get(Asterion.id("catacombs/"+name)).orElseThrow();
                    BlockPos origin=new BlockPos(index++*24,100,0);
                    var settings=AuthoredCatacombs.settings(new net.minecraft.world.level.levelgen.structure.BoundingBox(origin.getX(),100,0,origin.getX()+18,130,18));
                    check(template.placeInWorld(server.overworld(),origin,origin,settings,RandomSource.create(1),18),"Failed to place "+name);
                    check(!server.overworld().getBlockState(origin.offset(9,5,0)).is(Blocks.JIGSAW),"Live jigsaw in "+name);
                    for(BlockPos doorPos:BlockPos.betweenClosed(origin,origin.offset(18,30,18))) {
                        var state=server.overworld().getBlockState(doorPos);
                        if(!state.is(Asterion.BARREL_DOOR))continue;
                        closedDoorPieces++;
                        check(!state.getValue(net.krodark.asterion.block.BarrelDoorBlock.OPEN)
                                && !state.getValue(net.krodark.asterion.block.BarrelDoorBlock.WING),
                                "Barrel door was not closed in "+name);
                    }
                    if(name.equals("puzzleroom")) {
                        var reward=(net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity)server.overworld().getBlockEntity(origin.offset(2,16,17));
                        var barrel=(net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity)server.overworld().getBlockEntity(origin.offset(16,13,17));
                        check(reward!=null&&reward.getLootTable()!=null&&reward.getLootTable().identifier().equals(Asterion.id("chests/catacomb_puzzle_reward")),"Puzzle reward NBT was not placed");
                        check(barrel!=null&&barrel.getLootTable()!=null&&barrel.getLootTable().identifier().equals(Asterion.id("chests/catacomb_puzzle_supplies")),"Barrel loot NBT was not placed");
                    }
                }
                check(closedDoorPieces>0,"No authored barrel doors were exercised");
                Asterion.LOGGER.info("PASS: authored arena assembly, single keyed entrance, reload markers, arena approach, real chunk seams and all 15 crypt variants");
                CrossingSurfaceCheck.run(server.overworld());
                CatacombRedstoneCheck.run(server.overworld());
                CatacombLootCheck.run(server.overworld());
                var player=server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level,.5,AuthoredCatacombs.CONNECTOR_Y,63.5,java.util.Set.of(),180,0,true);
                var boss=net.krodark.asterion.entity.MinotaurEntity.activateCenterBoss(level,player,null,net.minecraft.core.Direction.SOUTH);
                check(boss!=null && boss.getY()==AuthoredCatacombs.ARENA_FLOOR_Y+1,"Boss did not spawn on authored floor");
                BossArenaEncounter.begin(level,player,boss,net.minecraft.core.Direction.SOUTH);
                check(player.getY()==AuthoredCatacombs.ARENA_FLOOR_Y+1,"Party entry used old arena elevation");
                check(level.noCollision(player),"Party entry placed player in masonry");
                BossArenaEncounter.finish(level);
                boss.discard();
                Asterion.LOGGER.info("PASS: authored arena boss spawn, safe party entry without generated arena fixtures");
            });
        }
    }
    private static void clear(net.minecraft.server.level.ServerLevel level,BlockPos pos){check(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty(),"Blocked connection at "+pos+": "+level.getBlockState(pos));}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
