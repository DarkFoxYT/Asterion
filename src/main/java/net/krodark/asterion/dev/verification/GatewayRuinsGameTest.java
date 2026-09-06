package net.krodark.asterion.dev.verification;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.worldgen.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
public final class GatewayRuinsGameTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context) {
  context.runOnClient(c->org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
  try(var world=context.worldBuilder().create()) {
   world.getServer().runOnServer(server->{
    var level=server.overworld();
    for(int x=-24;x<=24;x++)for(int z=-24;z<=24;z++)for(int y=68;y<=78+Math.floorDiv(x,8);y++)
     level.setBlock(new BlockPos(x,y,z),(y==78+Math.floorDiv(x,8)?Blocks.GRASS_BLOCK:Blocks.STONE).defaultBlockState(),2);
    int surface=net.krodark.asterion.worldgen.GatewayRuins.surface(level,0,0);
    WorldGenerator.buildGateway(level,BlockPos.ZERO);
    for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=surface-26;y<=surface+3;y++)
     if(!level.getBlockState(new BlockPos(x,y,z)).isAir())throw new AssertionError("Portal shaft obstructed at "+new BlockPos(x,y,z));
    int masonry=0;
    for(BlockPos block:BlockPos.betweenClosed(-8,surface-89,-8,8,surface+13,8)) {
     var state=level.getBlockState(block);
     if(state.is(Blocks.CYAN_WOOL))throw new AssertionError("Portal marker remained in the blueprint");
     if(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("asterion"))masonry++;
    }
    if(masonry<500)throw new AssertionError("Authored gateway structure missing");
    var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    p.teleportTo(level,17,surface+13,22,java.util.Set.of(),142,25,true);
   });
   context.waitTicks(40);context.runOnClient(c->c.options.hideGui=true);context.takeScreenshot("gateway-ruins-sloped-ground");
  }
 }
}
