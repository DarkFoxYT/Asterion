package net.krodark.asterion.test;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.compat.GeometryCompat;
public class CompatibilityGameTests implements FabricGameTest {
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void environmentRayWithoutEntity(GameTestHelper test) {
  var block=test.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
  test.getLevel().setBlockAndUpdate(block,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
  var start=net.minecraft.world.phys.Vec3.atCenterOf(block.above(2));
  var hit=net.krodark.asterion.event.RumbleSources.trace(test.getLevel(),start,start.add(0,-4,0));
  test.assertTrue(hit!=null && hit.block().equals(block) && hit.normal().y>.9,"Environment rays must hit the stone's top face without an entity");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void persistentItems(GameTestHelper test) {
  var stack = new ItemStack(Asterion.AFTERBLOW);
  test.assertTrue(stack.getMaxDamage()>0,"Afterblow must keep its default durability");
  stack.setDamageValue(7);
  var ops=RegistryOps.create(NbtOps.INSTANCE,test.getLevel().registryAccess());
  var encoded=ItemStack.CODEC.encodeStart(ops,stack).result().orElseThrow();
  var decoded=ItemStack.CODEC.parse(ops,encoded).result().orElseThrow();
  test.assertTrue(decoded.getItem()==stack.getItem() && decoded.getDamageValue()==7,"Item NBT/component round trip must preserve item and damage");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void contentLoaded(GameTestHelper test) {
  // GameTestServer creates its own fixed three-dimension world. Validate the
  // mod's loaded dimension type and decode its dimension definition independently.
  var server=test.getLevel().getServer();
  var resource=server.getResourceManager().getResource(Asterion.id("dimension/asterion_dimension.json")).orElseThrow();
  try(var reader=resource.openAsReader()) {
   var json=com.google.gson.JsonParser.parseReader(reader);
   var ops=RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,server.registryAccess());
   test.assertTrue(net.minecraft.world.level.dimension.LevelStem.CODEC.parse(ops,json).result().isPresent(),"Labyrinth dimension definition must decode against loaded registries");
  } catch(java.io.IOException error) { throw new RuntimeException(error); }
  test.assertTrue(test.getLevel().getRecipeManager().byKey(Asterion.id("ancient_brick_slab")).isPresent(),"Backported crafting recipe must load");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void leafLoot(GameTestHelper test) {
  var pos = test.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
  for(var block : java.util.List.of(Asterion.ANCIENT_LEAVES,Asterion.TAINTED_LEAVES)) {
   var drops=net.minecraft.world.level.block.Block.getDrops(block.defaultBlockState(),test.getLevel(),pos,null,null,new ItemStack(net.minecraft.world.item.Items.SHEARS));
   test.assertTrue(drops.stream().anyMatch(stack -> stack.is(block.asItem())),"Shears must preserve the authored leaf drop on both data formats");
  }
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void structureChunkBounds(GameTestHelper test) {
  var box=new BoundingBox(-17,3,-1,16,7,16);
  var expanded=GeometryCompat.inflate(box,5,0,5);
  test.assertTrue(expanded.minY()==3 && expanded.maxY()==7,"Horizontal structure reservations must preserve height");
  var chunks=GeometryCompat.chunks(box).toList();
  test.assertTrue(chunks.size()==12,"Chunk bounds must include negative coordinates and both edges");
  test.assertTrue(chunks.stream().distinct().count()==chunks.size(),"Chunk reservations must be unique");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void eclipseHunterIgnoresArenaBoss(GameTestHelper test) {
  var hunter = new net.krodark.asterion.entity.MinotaurEntity(Asterion.MINOTAUR, test.getLevel());
  var boss = new net.krodark.asterion.entity.MinotaurEntity(Asterion.MINOTAUR, test.getLevel());
  var corpse = new net.krodark.asterion.entity.MinotaurEntity(Asterion.MINOTAUR, test.getLevel());
  try {
   var phaseField = net.krodark.asterion.entity.MinotaurEntity.class.getDeclaredField("DATA_PHASE");
   var stageField = net.krodark.asterion.entity.MinotaurEntity.class.getDeclaredField("DATA_BOSS_STAGE");
   phaseField.setAccessible(true); stageField.setAccessible(true);
   @SuppressWarnings("unchecked") var phase = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) phaseField.get(null);
   @SuppressWarnings("unchecked") var stage = (net.minecraft.network.syncher.EntityDataAccessor<Integer>) stageField.get(null);
   boss.getEntityData().set(phase, net.krodark.asterion.entity.MinotaurEntity.BehaviorPhase.BOSS.ordinal());
   corpse.getEntityData().set(phase, net.krodark.asterion.entity.MinotaurEntity.BehaviorPhase.BOSS.ordinal());
   var stages = stageField.getDeclaringClass().getDeclaredClasses();
   for (var type : stages) if (type.getSimpleName().equals("BossStage"))
    for (var value : type.getEnumConstants()) if (((Enum<?>) value).name().equals("DEFEATED"))
     corpse.getEntityData().set(stage, ((Enum<?>) value).ordinal());
  } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
  var position = net.minecraft.world.phys.Vec3.atBottomCenterOf(test.absolutePos(new net.minecraft.core.BlockPos(1,1,1)));
  for (var entity : java.util.List.of(hunter, boss, corpse)) {
   entity.setPos(position.x, position.y, position.z);
   test.getLevel().addFreshEntity(entity);
  }
  try {
   var query = net.krodark.asterion.event.DeadSunEventSystem.class.getDeclaredMethod("eclipseMinotaurs", net.minecraft.server.level.ServerLevel.class);
   query.setAccessible(true);
   var result = (java.util.List<?>) query.invoke(null, test.getLevel());
   test.assertTrue(result.contains(hunter) && !result.contains(boss) && !result.contains(corpse),
     "Arena bosses and defeated remains must not claim an eclipse hunter slot, including at distant maze coordinates");
  } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
  finally { hunter.discard(); boss.discard(); corpse.discard(); }
  test.succeed();
 }
}
