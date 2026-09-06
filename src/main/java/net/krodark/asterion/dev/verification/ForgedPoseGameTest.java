package net.krodark.asterion.dev.verification;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
public final class ForgedPoseGameTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context) {
  context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
  try(var world=context.worldBuilder().create()) {
   world.getServer().runOnServer(server -> {
    var p=server.getPlayerList().getPlayers().getFirst();
    p.teleportTo(server.overworld(),.5,-60,.5,java.util.Set.of(),25,-12,true);
    try {
     var factory=Asterion.class.getDeclaredMethod("forgePart",net.minecraft.world.item.Item.class,int.class,String.class);factory.setAccessible(true);
     var parts=java.util.List.of((net.minecraft.world.item.ItemStack)factory.invoke(null,Asterion.FORGED_SWORD_BLADE,5,"Sword Blade"),(net.minecraft.world.item.ItemStack)factory.invoke(null,Asterion.FORGED_SWORD_GUARD,5,"Sword Guard"),(net.minecraft.world.item.ItemStack)factory.invoke(null,Asterion.FORGED_SWORD_POMMEL,5,"Sword Pommel"),new net.minecraft.world.item.ItemStack(Asterion.DEADWOOD_STICK));
     p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.krodark.asterion.recipe.ForgedSwordRecipe().assemble(net.minecraft.world.item.crafting.CraftingInput.of(2,2,parts)));
    }catch(Exception e){throw new AssertionError(e);}
   });
   context.waitTicks(30);
   context.runOnClient(c -> {c.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);c.options.hideGui=true;});
   context.waitTicks(10);context.takeScreenshot("forged-sword-third-person");
   context.runOnClient(c -> { c.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); c.options.hideGui=false; });
   context.waitTicks(10);context.takeScreenshot("forged-sword-first-person");
  }
 }
}
