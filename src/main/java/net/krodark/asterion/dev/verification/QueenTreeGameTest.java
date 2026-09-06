package net.krodark.asterion.dev.verification;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.*;
import net.krodark.asterion.worldgen.*;
import net.minecraft.core.BlockPos;
public final class QueenTreeGameTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context) {
  context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
  try(var world=context.worldBuilder().create()) {
   world.getServer().runOnServer(server -> {
    var level=server.getLevel(Asterion.ASTERION_LEVEL);MazeBiomes.load(level);
    var config=AsterionConfig.INSTANCE;
    var layout=MazeNbtStructures.layout(level,config.mazeRadiusCells,config.cellSize,(a,b,c,d)->true);
    try {
     var field=layout.getClass().getDeclaredField("placements");field.setAccessible(true);
     int count=0;
     for(Object placement:(java.util.List<?>)field.get(layout)) {
      var id=placement.getClass().getDeclaredMethod("id");id.setAccessible(true);
      if(!id.invoke(placement).equals(Asterion.id("tree_beetle")))continue;
      count++;
      var accessor=placement.getClass().getDeclaredMethod("origin");accessor.setAccessible(true);
      BlockPos origin=(BlockPos)accessor.invoke(placement);
      BlockPos spawn=origin.offset(37,26,27);
      double distance=Math.hypot(spawn.getX(),spawn.getZ());
      if(distance<300||distance>500)throw new AssertionError("Queen outside requested range");
      var boundsMethod=placement.getClass().getDeclaredMethod("reserved");boundsMethod.setAccessible(true);
      var bounds=(net.minecraft.world.level.levelgen.structure.BoundingBox)boundsMethod.invoke(placement);
      bounds.intersectingChunks().forEach(cp->level.getChunk(cp.x(),cp.z()));
      layout.onChunkBuilt(level.getChunkAt(origin));
      for(int tick=0;tick<200;tick++)MazeNbtStructures.tick(level);
      if(level.getBlockState(spawn).is(net.minecraft.world.level.block.Blocks.RED_WOOL))throw new AssertionError("Red wool remained");
      var nearby=level.getEntitiesOfClass(net.krodark.asterion.entity.QueenBeetleEntity.class,new net.minecraft.world.phys.AABB(spawn).inflate(8));
      if(nearby.size()!=1)throw new AssertionError("Expected one Queen at "+spawn+", got "+nearby.size());
      layout.onChunkBuilt(level.getChunkAt(origin));for(int tick=0;tick<20;tick++)MazeNbtStructures.tick(level);
      if(level.getEntitiesOfClass(net.krodark.asterion.entity.QueenBeetleEntity.class,new net.minecraft.world.phys.AABB(spawn).inflate(8)).size()!=1)throw new AssertionError("Duplicate Queen");
      if(count==1){var player=server.getPlayerList().getPlayers().getFirst();player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);player.teleportTo(level,spawn.getX()+70,spawn.getY()+60,spawn.getZ()+70,java.util.Set.of(),135,15,true);}
     }
     if(count!=2)throw new AssertionError("Expected exactly two tree sites, got "+count);
     Asterion.LOGGER.info("PASS: two Queen trees in range, red wool consumed, one Queen each and no duplicate placement");
    }catch(ReflectiveOperationException e){throw new AssertionError(e);}
   });
   context.waitTicks(40);context.takeScreenshot("queen-tree-generated");
  }
 }
}
