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
                WorldGenerator.prepareBossArenaBeforePlayers(level);
                for(int cx=-4;cx<=3;cx++)for(int cz=-4;cz<=3;cz++)
                    AuthoredCatacombs.placeArenaChunk(level,level.getChunk(cx,cz));
                for(int cx=-1;cx<=5;cx++)for(int cz=3;cz<=5;cz++)
                    AuthoredCatacombs.placeArenaChunk(level,level.getChunk(cx,cz));
                // Chunks may already carry the revision marker from terrain generation;
                // finish explicitly so a fresh runtime build discovers those authored pieces.
                check(WorldGenerator.ensureBossArenaReady(level),"Arena not ready");
                BlockPos centerFloor = new BlockPos(0, AuthoredCatacombs.ARENA_FLOOR_Y - 1, 0);
                check(!level.getBlockState(centerFloor).getCollisionShape(level, centerFloor).isEmpty(),
                        "Authored arena center floor was not generated");
                check(level.getBlockState(centerFloor.above()).isAir(), "Arena center obstructed");
                for(int x=-1;x<=1;x++)for(int y=AuthoredCatacombs.ARENA_FLOOR_Y;
                                           y<=AuthoredCatacombs.ARENA_FLOOR_Y+4;y++)
                    check(!level.getBlockState(new BlockPos(x,y,-60)).is(Blocks.CYAN_WOOL),
                            "Arena exit portal marker remained as cyan wool");
                check(level.getBlockEntity(MinotaurArenaEntrances.door(net.minecraft.core.Direction.SOUTH)) instanceof net.krodark.asterion.block.MinotaurDoorBlockEntity,"Single keyed arena entrance missing");
                check(MinotaurArenaEntrances.DOORS.size()==1,"Extra arena entrance");
                check(WorldGenerator.activeBossBraziers(level)>0,"Authored arena braziers were not generated");
                check(WorldGenerator.bossPillarsRemaining()>0,"Authored arena pillars were not registered");
                int closedDoorPieces=0;
                for(int x=-61;x<=61;x++)for(int z=-61;z<=61;z++)
                    for(int y=AuthoredCatacombs.ARENA_BASE_Y;
                        y<=AuthoredCatacombs.ARENA_BASE_Y+47;y++) {
                    var state=level.getBlockState(new BlockPos(x,y,z));
                    if(state.is(Asterion.BARREL_DOOR)) {
                        closedDoorPieces++;
                        check(!state.getValue(net.krodark.asterion.block.BarrelDoorBlock.OPEN)
                                && !state.getValue(net.krodark.asterion.block.BarrelDoorBlock.WING),
                                "Authored arena barrel door was not placed closed");
                    }
                }
                level.getChunkAt(new BlockPos(CatacombLayout.ROOT_CENTER,AuthoredCatacombs.CONNECTOR_Y,
                        CatacombLayout.ROOT_CENTER));
                check(CatacombEntrances.checkpoint(level,new BlockPos(CatacombLayout.ROOT_CENTER,
                        CatacombLayout.FLOOR_Y,CatacombLayout.ROOT_CENTER))!=null,
                        "Authored crossing lost its safe checkpoint");
                // Force and inspect the lower authored district itself. Merely resolving
                // its templates does not prove the biome decoration hook actually ran.
                var forgeTemplate=level.getStructureManager().get(Asterion.id("forge/forge")).orElseThrow();
                var forgeRelative=forgeTemplate.getBoundingBox(new StructurePlaceSettings(),BlockPos.ZERO);
                BlockPos forgeOrigin=new BlockPos(CatacombLayout.ROOT_CENTER
                        -(forgeRelative.minX()+forgeRelative.maxX())/2,
                        LabyrinthLevels.FORGE_FLOOR_Y-forgeRelative.minY(),
                        CatacombLayout.ROOT_CENTER-(forgeRelative.minZ()+forgeRelative.maxZ())/2);
                var forgeBounds=forgeTemplate.getBoundingBox(new StructurePlaceSettings(),forgeOrigin);
                for(int cx=forgeBounds.minX()>>4;cx<=forgeBounds.maxX()>>4;cx++)
                    for(int cz=forgeBounds.minZ()>>4;cz<=forgeBounds.maxZ()>>4;cz++)level.getChunk(cx,cz);
                int forgedBlocks=0,forgeCrucibles=0;
                for(BlockPos forgePos:BlockPos.betweenClosed(forgeBounds.minX(),forgeBounds.minY(),forgeBounds.minZ(),
                        forgeBounds.maxX(),forgeBounds.maxY(),forgeBounds.maxZ())) {
                    var forgeState=level.getBlockState(forgePos);
                    if(forgeState.is(Asterion.MAZESTEEL_BLOCK)||forgeState.is(Asterion.MAZESTEEL_BRICKS))forgedBlocks++;
                    if(forgeState.is(Asterion.CRUCIBLE))forgeCrucibles++;
                    check(!forgeState.is(Blocks.JIGSAW),"Forge left a live jigsaw at "+forgePos);
                }
                check(forgedBlocks>5000,"Authored Forge masonry did not generate: "+forgedBlocks);
                check(forgeCrucibles>0,"Authored Forge crucibles did not generate");
                for(int cx=-4;cx<=3;cx++)for(int cz=-4;cz<=3;cz++)
                    check(level.getBlockState(new BlockPos(cx*16,AuthoredCatacombs.ARENA_BASE_Y-1,cz*16))
                            .is(Blocks.LIGHT),"Missing arena reload marker");
                BlockPos playerDoor=MinotaurArenaEntrances.door(MinotaurArenaEntrances.PLAYER_ENTRANCE);
                int previousFloor=Integer.MIN_VALUE;
                for(int z=playerDoor.getZ()+1;z<=AuthoredCatacombs.ARENA_RADIUS;z++) {
                    int expected=Math.min(AuthoredCatacombs.CONNECTOR_Y-1,
                            playerDoor.getY()-1+z-(playerDoor.getZ()+1));
                    BlockPos step=new BlockPos(0,expected,z);
                    if(level.getBlockState(step).getCollisionShape(level,step).isEmpty())step=step.below();
                    check(!level.getBlockState(step).getCollisionShape(level,step).isEmpty(),"Missing arena descent step at "+step);
                    clear(level,step.above());
                    clear(level,step.above(2));
                    if(previousFloor!=Integer.MIN_VALUE)
                        check(step.getY()>=previousFloor&&step.getY()-previousFloor<=1,
                                "Arena descent is too steep at z="+z);
                    previousFloor=step.getY();
                }
                // The current route bends from arena x=0 into module (0,4)'s north
                // connector at x=9; the old straight hand-carved hall is intentionally sealed.
                for(int z=AuthoredCatacombs.ARENA_RADIUS+1;z<=76;z++) {
                    int center=Math.round((z-(AuthoredCatacombs.ARENA_RADIUS+1))*9F
                            /(76-(AuthoredCatacombs.ARENA_RADIUS+1)));
                    clear(level,new BlockPos(center,AuthoredCatacombs.CONNECTOR_Y,z));
                    clear(level,new BlockPos(center,AuthoredCatacombs.CONNECTOR_Y+1,z));
                }
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
                check(player.getY()==AuthoredCatacombs.ARENA_FLOOR_Y,"Party entry used old arena elevation");
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
