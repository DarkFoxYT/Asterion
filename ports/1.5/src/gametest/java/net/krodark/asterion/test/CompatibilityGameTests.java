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
 public void omegaLockOpeningTiming(GameTestHelper test) {
  var level = test.getLevel();
  var root = test.absolutePos(new net.minecraft.core.BlockPos(2,1,2));
  var lockPos = root.above(2);
  var gate = Asterion.MAZESTEEL_GATE.defaultBlockState().setValue(net.krodark.asterion.block.DirectionalGateBlock.OPEN,false);
  for(int row=0;row<4;row++)for(int side=-1;side<=1;side++)level.setBlock(root.offset(side,row,0),gate,2);
  var unrelated = root.offset(4,0,3);
  level.setBlock(unrelated,gate,2);
  var locked = Asterion.OMEGA_LOCK.defaultBlockState();
  level.setBlock(lockPos,locked,2);
  var lock = (net.krodark.asterion.block.OmegaLockBlockEntity)level.getBlockEntity(lockPos);
  test.assertTrue(((net.krodark.asterion.block.OmegaLockBlock)Asterion.OMEGA_LOCK).getTicker(level,locked,Asterion.OMEGA_LOCK_BLOCK_ENTITY)==null,"Idle locks must not tick");
  test.assertTrue(lock!=null&&lock.unlock(null),"Omega lock must accept its connected gate");
  var invisible = level.getBlockState(lockPos);
  test.assertTrue(invisible.getValue(net.krodark.asterion.block.OmegaLockBlock.UNLOCKED)
    && invisible.getRenderShape()==net.minecraft.world.level.block.RenderShape.INVISIBLE,"Lock must be invisible on the unlocking tick");
  test.assertTrue(invisible.getShape(level,lockPos).isEmpty()&&invisible.getCollisionShape(level,lockPos).isEmpty(),"Invisible lock must not retain an outline or collision");
  for(int side=-1;side<=1;side++)test.assertTrue(level.getBlockState(root.offset(side,0,0)).getValue(net.krodark.asterion.block.DirectionalGateBlock.OPEN),"Bottom row must rise in the same tick as the lock disappears");
  test.assertTrue(!level.getBlockState(root.above(3)).getValue(net.krodark.asterion.block.DirectionalGateBlock.OPEN),"Upper rows must keep their lift sequence");
  for(int tick=0;tick<50;tick++)net.krodark.asterion.block.OmegaLockBlockEntity.tick(level,lockPos,level.getBlockState(lockPos),lock);
  for(int row=0;row<4;row++)for(int side=-1;side<=1;side++)if(!root.offset(side,row,0).equals(lockPos))
   test.assertTrue(level.getBlockState(root.offset(side,row,0)).getValue(net.krodark.asterion.block.DirectionalGateBlock.OPEN),"Every connected gate row must finish opening");
  test.assertTrue(level.getBlockState(lockPos).isAir()&&!lock.isOpening(),"Finished lock controller must be removed");
  test.assertTrue(!level.getBlockState(unrelated).getValue(net.krodark.asterion.block.DirectionalGateBlock.OPEN),"Nearby unrelated gates must not be animated");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void centralFloorRoofSheet(GameTestHelper test) {
  var level = test.getLevel();
  var chunk = level.getChunk(6,0);
  int y = net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y;
  var stone = Asterion.ANCIENT_STONE.defaultBlockState();
  var brick = Asterion.ANCIENT_BRICKS.defaultBlockState();
  var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
  for(int x=98;x<=103;x++) for(int z=2;z<=7;z++) {
   level.setBlock(new net.minecraft.core.BlockPos(x,y,z),stone,2);
   level.setBlock(new net.minecraft.core.BlockPos(x,y+1,z),Math.floorMod(x*17L+z*31L+3,9)<3?Asterion.ANCIENT_MOSSY_BRICKS.defaultBlockState():brick,2);
   level.setBlock(new net.minecraft.core.BlockPos(x,y+2,z),air,2);
  }
  for(int x=98;x<=101;x++)for(int z=10;z<=13;z++) {
   level.setBlock(new net.minecraft.core.BlockPos(x,y,z),stone,2);
   level.setBlock(new net.minecraft.core.BlockPos(x,y+1,z),brick,2);
   level.setBlock(new net.minecraft.core.BlockPos(x,y+2,z),air,2);
  }
  var wall = new net.minecraft.core.BlockPos(106,y+1,2);
  var chest = new net.minecraft.core.BlockPos(107,y+1,4);
  var isolated = new net.minecraft.core.BlockPos(108,y+1,10);
  for(var pos:java.util.List.of(wall,chest,isolated))level.setBlock(pos.below(),stone,2);
  level.setBlock(wall,brick,2); level.setBlock(wall.above(),brick,2);
  level.setBlock(chest,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(),2);
  level.setBlock(isolated,brick,2); level.setBlock(isolated.above(),air,2);
  try {
   var repair = net.krodark.asterion.worldgen.GeneratedPhysicsChains.class.getDeclaredMethod("repairCenterDecorations",net.minecraft.server.level.ServerLevel.class,net.minecraft.world.level.chunk.LevelChunk.class);
   repair.setAccessible(true); repair.invoke(null,level,chunk); repair.invoke(null,level,chunk);
   for(int x=98;x<=103;x++)for(int z=2;z<=7;z++) {
    test.assertTrue(level.getBlockState(new net.minecraft.core.BlockPos(x,y,z)).is(Asterion.ANCIENT_STONE),"Ground must remain solid");
    test.assertTrue(level.getBlockState(new net.minecraft.core.BlockPos(x,y+1,z)).isAir(),"Every raised roof tile must be removed, including the last tile");
   }
   for(int x=98;x<=101;x++)for(int z=10;z<=13;z++)
    test.assertTrue(level.getBlockState(new net.minecraft.core.BlockPos(x,y+1,z)).is(Asterion.ANCIENT_BRICKS),"Plain masonry without the old pattern must survive");
   test.assertTrue(level.getBlockState(wall).is(Asterion.ANCIENT_BRICKS)&&level.getBlockState(wall.above()).is(Asterion.ANCIENT_BRICKS),"Taller walls must survive");
   test.assertTrue(level.getBlockEntity(chest)!=null,"Block entities must survive");
   test.assertTrue(level.getBlockState(isolated).is(Asterion.ANCIENT_BRICKS),"Isolated placed bricks must survive");
   test.assertTrue(net.krodark.asterion.worldgen.CatacombLayout.roofAt(100,100)<=y,"New roof must stop at the maze floor");
  } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
  test.succeed();
 }
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
  var resource=server.getResourceManager().getResource(Asterion.id("dimension/labyrinth.json")).orElseThrow();
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
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void arenaDrainPreservesLowWater(GameTestHelper test) {
  var water = net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
  var wet = net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS.defaultBlockState()
    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
  test.assertTrue(net.krodark.asterion.worldgen.AuthoredCatacombs.drainedArenaState(water, 54) == water,
    "Arena drainage must preserve water at Y=54 and below");
  test.assertTrue(net.krodark.asterion.worldgen.AuthoredCatacombs.drainedArenaState(water, 55).isAir(),
    "Arena drainage must remove standing water above Y=54");
  var dry = net.krodark.asterion.worldgen.AuthoredCatacombs.drainedArenaState(wet, 55);
  test.assertTrue(dry.is(wet.getBlock()) && !dry.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED),
    "Arena drainage must dry waterlogged blocks without removing their model");
  test.assertTrue(net.krodark.asterion.worldgen.AuthoredCatacombs.drainedArenaState(wet, 54) == wet,
    "Low waterlogged blocks must be preserved too");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void dimensionMigrationPreservesTerrainAndPlayers(GameTestHelper test) throws java.io.IOException {
  var root=java.nio.file.Files.createTempDirectory("asterion-dimension-migration-");
  try {
   var tag=new net.minecraft.nbt.CompoundTag();
   tag.putString("Dimension","asterion:asterion_dimension");
   tag.putString("SpawnDimension","asterion:asterion_dimension");
   tag.putString("Other","minecraft:overworld");
   var dimensions=new net.minecraft.nbt.CompoundTag();
   dimensions.put("asterion:asterion_dimension",new net.minecraft.nbt.CompoundTag());
   tag.put("dimensions",dimensions);
   try(var out=java.nio.file.Files.newOutputStream(root.resolve("level.dat"))) { net.minecraft.nbt.NbtIo.writeCompressed(tag,out); }
   var player=root.resolve("playerdata/test.dat");java.nio.file.Files.createDirectories(player.getParent());
   java.nio.file.Files.copy(root.resolve("level.dat"),player);
   var terrain=root.resolve("dimensions/asterion/asterion_dimension/region/r.0.0.mca");
   java.nio.file.Files.createDirectories(terrain.getParent());java.nio.file.Files.writeString(terrain,"existing terrain");
   net.krodark.asterion.worldgen.LabyrinthDimensionMigration.migrate(root);
   test.assertTrue(java.nio.file.Files.readString(root.resolve("dimensions/asterion/labyrinth/region/r.0.0.mca")).equals("existing terrain"),"Existing Labyrinth terrain must be moved intact");
   test.assertTrue(java.nio.file.Files.exists(player.resolveSibling("test.dat.asterion-dimension-backup")),"Original player data must be backed up");
   var migrated=net.krodark.asterion.worldgen.LabyrinthDimensionMigration.readCompressed(player);
   test.assertTrue(migrated.get("Dimension").equals(net.minecraft.nbt.StringTag.valueOf("asterion:labyrinth")),"Player location must remain in the renamed Labyrinth");
   test.assertTrue(migrated.get("SpawnDimension").equals(net.minecraft.nbt.StringTag.valueOf("asterion:labyrinth")),"Respawn dimension must migrate");
   test.assertTrue(migrated.get("Other").equals(net.minecraft.nbt.StringTag.valueOf("minecraft:overworld")),"Unrelated dimensions must remain unchanged");
   var checksum=java.nio.file.Files.readAllBytes(player);
   net.krodark.asterion.worldgen.LabyrinthDimensionMigration.migrate(root);
   test.assertTrue(java.util.Arrays.equals(checksum,java.nio.file.Files.readAllBytes(player)),"Migration must be idempotent");
  } finally {
   try(var paths=java.nio.file.Files.walk(root)) {
    for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);
   }
  }
  test.succeed();
 }

 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void arenaMazeSeamRestoresMissingFloor(GameTestHelper test) {
  var level=test.getLevel();
  var chunk=level.getChunk(3,0);
  int roof=net.krodark.asterion.worldgen.LabyrinthLevels.ARENA_ROOF_Y;
  var gap=new net.minecraft.core.BlockPos(62,roof,5);
  var lowRoof=new net.minecraft.core.BlockPos(61,roof,5);
  var opening=new net.minecraft.core.BlockPos(60,roof,6);
  level.setBlockAndUpdate(gap,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
  try {
   var clear=net.krodark.asterion.worldgen.AuthoredCatacombs.class.getDeclaredMethod("clearOldArenaChunk",net.minecraft.server.level.ServerLevel.class,BoundingBox.class);
   clear.setAccessible(true);
   clear.invoke(null,level,new BoundingBox(60,roof,4,63,roof,6));
  } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
  test.assertTrue(level.getBlockState(gap).is(net.minecraft.world.level.block.Blocks.STONE),"Arena chunk clearing must preserve neighboring maze columns");
  level.setBlockAndUpdate(gap,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
  level.setBlockAndUpdate(gap.above(),Asterion.ANCIENT_BRICKS.defaultBlockState());
  level.setBlockAndUpdate(gap.above(2),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
  level.setBlockAndUpdate(lowRoof,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
  level.setBlockAndUpdate(lowRoof.below(),Asterion.ANCIENT_BRICKS.defaultBlockState());
  level.setBlockAndUpdate(opening,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
  level.setBlockAndUpdate(opening.below(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
  net.krodark.asterion.worldgen.ArenaSurfaceSeam.repair(level,chunk,true);
  test.assertTrue(level.getBlockState(gap).is(Asterion.ANCIENT_STONE),"Erased columns outside the arena must reconnect to its roof");
  test.assertTrue(level.getBlockState(gap.above()).isAir(),"The old raised floor must not leave a one-block step");
  test.assertTrue(level.getBlockState(lowRoof).is(Asterion.ANCIENT_STONE),"Supported low roof edges must reach the maze floor");
  test.assertTrue(level.getBlockState(opening).isAir(),"Collapsed unsupported roof openings must remain open");
  for(long seed:new long[]{0,1,99181}) for(int x:new int[]{-85,-62,62,85})
   test.assertTrue(net.krodark.asterion.worldgen.WorldGenerator.mazeFloorHeight(seed,x,85)==roof,"All four arena approach corners must be level for every seed");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void scorchedWallRepairRemovesFloatingFoliage(GameTestHelper test) {
  var level=test.getLevel();
  var chunk=level.getChunk(4,0);
  var floating=new net.minecraft.core.BlockPos(70,150,5);
  var floatingStone=floating.east();
  var attached=new net.minecraft.core.BlockPos(70,97,5);
  var leaves=Asterion.TAINTED_LEAVES.defaultBlockState()
    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true);
  level.setBlockAndUpdate(floating,leaves);
  level.setBlockAndUpdate(floatingStone,Asterion.ANCIENT_BRICKS.defaultBlockState());
  level.setBlockAndUpdate(attached,leaves);
  try {
   var repair=net.krodark.asterion.worldgen.GeneratedPhysicsChains.class.getDeclaredMethod("repairCenterDecorations",net.minecraft.server.level.ServerLevel.class,net.minecraft.world.level.chunk.LevelChunk.class);
   repair.setAccessible(true);
   repair.invoke(null,level,chunk);
  } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
  test.assertTrue(level.getBlockState(floating).isAir(),"Foliage above a destroyed wall must not remain floating");
  test.assertTrue(level.getBlockState(floatingStone).isAir(),"Foliage and masonry must use the same repaired ceiling");
  test.assertTrue(level.getBlockState(attached).is(Asterion.TAINTED_LEAVES),"Surviving low wall foliage must be retained");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void catacombLandingPreservesItsControls(GameTestHelper test) {
  var level=test.getLevel();
  var origin=new net.minecraft.core.BlockPos(0,net.krodark.asterion.worldgen.AuthoredCatacombs.BASE_Y,0);
  int floor=net.krodark.asterion.worldgen.LabyrinthLevels.MAZE_FLOOR_Y;
  var lever=new net.minecraft.core.BlockPos(10,floor+1,7);
  level.setBlockAndUpdate(lever.below(),Asterion.ANCIENT_BRICKS.defaultBlockState());
  var control=net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED,true);
  level.setBlockAndUpdate(lever,control);
  var obstruction=new net.minecraft.core.BlockPos(13,floor+1,9);
  level.setBlockAndUpdate(obstruction,Asterion.ANCIENT_BRICKS.defaultBlockState());
  net.krodark.asterion.worldgen.AuthoredCatacombs.surfaceApproach(level,new net.minecraft.world.level.ChunkPos(0,0),origin,0);
  test.assertTrue(level.getBlockState(lever).equals(control),"Entrance shaping must preserve the lever and its powered state");
  test.assertTrue(level.getBlockState(obstruction).isAir(),"The landing must clear maze walls from the approach");
  test.assertTrue(level.getBlockState(obstruction.below()).is(Asterion.ANCIENT_STONE),"The approach must match the maze stone floor");
  test.assertTrue(!net.krodark.asterion.worldgen.CatacombProtection.waterproofLever(level,control),"Normal Overworld levers must retain vanilla fluid behavior");
  test.succeed();
 }
 @GameTest(template=FabricGameTest.EMPTY_STRUCTURE)
 public void leverWaterProtectionIsDimensionScoped(GameTestHelper test) {
  var maze=test.getLevel();
  var pos=new net.minecraft.core.BlockPos(62,55,5);
  maze.setBlockAndUpdate(pos.below(),Asterion.ANCIENT_BRICKS.defaultBlockState());
  var control=net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ATTACH_FACE,net.minecraft.world.level.block.state.properties.AttachFace.FLOOR);
  maze.setBlockAndUpdate(pos,control);
  test.assertTrue(net.krodark.asterion.worldgen.CatacombProtection.waterproofLever(Asterion.ASTERION_LEVEL,control),"Labyrinth entrance levers must resist ordinary water");
  test.assertTrue(!net.krodark.asterion.worldgen.CatacombProtection.waterproofLever(Asterion.ASTERION_LEVEL,Asterion.ANCIENT_BRICKS.defaultBlockState()),"The protection must only apply to lever controls");
  try {
   var spread=net.minecraft.world.level.material.FlowingFluid.class.getDeclaredMethod("spreadTo",net.minecraft.world.level.LevelAccessor.class,net.minecraft.core.BlockPos.class,net.minecraft.world.level.block.state.BlockState.class,net.minecraft.core.Direction.class,net.minecraft.world.level.material.FluidState.class);
   spread.setAccessible(true);
   spread.invoke(net.minecraft.world.level.material.Fluids.FLOWING_WATER,maze,pos,control,net.minecraft.core.Direction.DOWN,net.minecraft.world.level.material.Fluids.WATER.defaultFluidState());
   test.assertTrue(maze.getBlockState(pos).getFluidState().getType()==net.minecraft.world.level.material.Fluids.WATER,"Actual Overworld water flow must retain vanilla lever destruction");
  } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
  finally {maze.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());}
  test.succeed();
 }
}
