package net.krodark.asterion.test;
import net.krodark.asterion.Asterion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
final class VersionGameplay {
 static net.minecraft.world.item.crafting.CraftingInput input(int width,int height,java.util.List<ItemStack> items) { return net.minecraft.world.item.crafting.CraftingInput.of(width,height,items); }
 static ItemStack craft(ServerPlayer player,java.util.List<ItemStack> parts) {
  var level=player.serverLevel();var recipe=new net.krodark.asterion.recipe.ForgedSwordRecipe();
  var items=new java.util.ArrayList<>(parts);items.add(new ItemStack(Asterion.DEADWOOD_STICK));
  var grid=input(2,2,items);GameplaySmoke.require(recipe.matches(grid,level),"Valid forged recipe did not match");
  var result=recipe.assemble(grid,level.registryAccess());
  var invalid=input(2,2,java.util.List.of(parts.get(0),parts.get(0),parts.get(2),new ItemStack(Asterion.DEADWOOD_STICK)));
  GameplaySmoke.require(!recipe.matches(invalid,level),"Duplicate blade must not replace a guard");
  var slab=level.getRecipeManager().byKey(Asterion.id("ancient_brick_slab")).orElseThrow();
  var bricks=input(3,1,java.util.List.of(new ItemStack(Asterion.ANCIENT_BRICKS),new ItemStack(Asterion.ANCIENT_BRICKS),new ItemStack(Asterion.ANCIENT_BRICKS)));
  var shaped=(net.minecraft.world.item.crafting.CraftingRecipe)slab.value();
  GameplaySmoke.require(shaped.matches(bricks,level),"Authored slab recipe did not match the crafting grid");
  GameplaySmoke.require(shaped.assemble(bricks,level.registryAccess()).getCount()==6,"Slab recipe output count changed");
  return result;
 }
 static void verifySword(ItemStack sword) {
  var tag=net.krodark.asterion.port.compat.ItemData.get(sword,net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag();
  GameplaySmoke.require(tag.getString("blade_material").equals("celestial_steel") && tag.getString("guard_material").equals("tarnished_gold") && tag.getString("pommel_material").equals("iron"),"Forged component materials changed");
  var model=net.krodark.asterion.port.compat.ItemData.get(sword,net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
  GameplaySmoke.require(model.value()==25,"Blade/guard/pommel model selection changed");
 }
 static void roof(ServerPlayer player) {
  net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new net.krodark.asterion.network.RoofCollapsePayload(new net.minecraft.world.phys.Vec3(0,178,3),180));
 }
 static void advancement(ServerPlayer player,String name) {
  var advancement=player.server.getAdvancements().get(Asterion.id(name));
  GameplaySmoke.require(advancement!=null,"Advancement did not load: "+name);
  GameplaySmoke.require(player.getAdvancements().getOrStartProgress(advancement).isDone(),"Advancement did not trigger: "+name);
 }
}
