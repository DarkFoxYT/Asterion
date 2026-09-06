package net.krodark.asterion.dev.verification;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.WorldGenerator;
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
    for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=surface-5;y<=surface;y++)
     if(!level.getBlockState(new BlockPos(x,y,z)).isAir())throw new AssertionError("Portal shaft obstructed");
    for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
     if(Math.max(Math.abs(x),Math.abs(z))<=2||Math.abs(x)+Math.abs(z)>13)continue;
     BlockPos support=new BlockPos(x,surface-2,z);
     if(level.getBlockState(support).isAir())throw new AssertionError("Floating gateway paving");
    }
    var p=server.getPlayerList().getPlayers().getFirst();p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
    p.teleportTo(level,17,surface+13,22,java.util.Set.of(),142,25,true);
   });
   context.waitTicks(40);context.runOnClient(c->c.options.hideGui=true);context.takeScreenshot("gateway-ruins-sloped-ground");
  }
 }
}
