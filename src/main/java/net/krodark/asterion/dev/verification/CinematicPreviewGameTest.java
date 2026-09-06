package net.krodark.asterion.dev.verification;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.CinematicDebugCommands;
public final class CinematicPreviewGameTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context) {
  context.runOnClient(c->org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
  try(var world=context.worldBuilder().create()) {
   world.getServer().runOnServer(server->{
    var p=server.getPlayerList().getPlayers().getFirst();
    p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    p.teleportTo(server.getLevel(Asterion.ASTERION_LEVEL),.5,205,80.5,java.util.Set.of(),180,0,true);
   });
   context.waitTicks(30);
   context.runOnClient(c->preview("entry"));context.waitTicks(85);context.takeScreenshot("entry-sun-reveal");
   context.waitTicks(100);context.takeScreenshot("entry-curved-dive");
   context.runOnClient(c->preview("stop"));
   context.runOnClient(c->{if(net.krodark.asterion.client.CinematicControls.locked())throw new AssertionError("Preview did not release controls");});
   context.runOnClient(c->preview("ending"));context.waitTicks(100);context.takeScreenshot("ending-sun-wide-shot");
   context.runOnClient(c->preview("stop"));
   context.runOnClient(c->{if(net.krodark.asterion.client.CinematicControls.locked())throw new AssertionError("Ending preview did not stop");});
  }
 }
 private static void preview(String name){try{var m=CinematicDebugCommands.class.getDeclaredMethod("preview",String.class);m.setAccessible(true);m.invoke(null,name);}catch(Exception e){throw new AssertionError(e);}}
}
