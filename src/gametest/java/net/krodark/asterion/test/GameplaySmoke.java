package net.krodark.asterion.test;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.item.ItemEntity;
import java.util.*;

final class GameplaySmoke {
 static volatile Throwable failure;
 static volatile int bossId=-1;
 static final Set<CursedBrazierEntity.Attack> attacks=java.util.concurrent.ConcurrentHashMap.newKeySet();
 static final List<BlockPos> braziers=new ArrayList<>();
 static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
 static void server(net.minecraft.client.Minecraft client,java.util.function.Consumer<ServerPlayer> action) {
  client.getSingleplayerServer().execute(() -> {
   try { action.accept(client.getSingleplayerServer().getPlayerList().getPlayers().get(0)); }
   catch(Throwable error){failure=error;Asterion.LOGGER.error("ASTERION_GAMEPLAY failed",error);}
  });
 }
 static void weaponMode(ServerPlayer player,int mode) {
  try {
   var type=net.krodark.asterion.entity.MinotaurEntity.class;
   var field=type.getDeclaredField("DATA_WEAPON");field.setAccessible(true);
   var accessor=(net.minecraft.network.syncher.EntityDataAccessor<Integer>)field.get(null);
   for(var boss:player.serverLevel().getEntitiesOfClass(type,player.getBoundingBox().inflate(25))) boss.getEntityData().set(accessor,mode);
  } catch(ReflectiveOperationException error){throw new AssertionError(error);}
 }
 static void forge(ServerPlayer player) {
  var level=player.serverLevel();
  for(var pos:BlockPos.betweenClosed(10,178,-2,30,190,9)) level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
  for(var pos:BlockPos.betweenClosed(10,177,-2,30,177,9)) level.setBlock(pos,Blocks.STONE.defaultBlockState(),2);
  Item[] casts={Asterion.SWORD_BLADE_CAST,Asterion.SWORD_GUARD_CAST,Asterion.SWORD_POMMEL_CAST};
  Item[] metals={Asterion.CELESTIAL_STEEL_INGOT,Asterion.TARNISHED_GOLD_INGOT,Items.IRON_INGOT};
  Item[] outputs={Asterion.FORGED_SWORD_BLADE,Asterion.FORGED_SWORD_GUARD,Asterion.FORGED_SWORD_POMMEL};
  List<ItemStack> parts=new ArrayList<>();
  for(int i=0;i<3;i++) {
   BlockPos pos=new BlockPos(14+i*6,178,3);
   level.setBlock(pos.below(),Blocks.CAMPFIRE.defaultBlockState(),3);
   var state=Asterion.CRUCIBLE.defaultBlockState(); level.setBlock(pos,state,3);
   Asterion.CRUCIBLE.setPlacedBy(level,pos,state,player,new ItemStack(Asterion.CRUCIBLE));
   var forge=(CrucibleBlockEntity)level.getBlockEntity(pos);
   require(forge!=null,"Forge root block entity is missing");
   require(forge.insert(player,new ItemStack(casts[i])),"Cast insertion failed");
   require(forge.insert(player,new ItemStack(metals[i])),"Metal insertion failed");
   int units=forge.materialUnits(); forge.control(player,net.krodark.asterion.network.CrucibleControlPayload.POUR);
   require(forge.materialUnits()==units,"Cold pouring consumed metal");
   for(int tick=0;tick<5000 && !forge.calibrated();tick++) CrucibleBlockEntity.tick(level,pos,state,forge);
   require(forge.calibrated(),"Campfire failed to heat the cast to its target");
   forge.control(player,net.krodark.asterion.network.CrucibleControlPayload.POUR);
   var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4),e->e.getItem().is(outputs[parts.size()]));
   require(drops.size()==1,"Pour must create exactly one forged part");
   parts.add(drops.get(0).getItem().copy());drops.get(0).discard();
   require(forge.materialUnits()==0,"Pour must consume all inserted metal");
  }
  var sword=VersionGameplay.craft(player,parts);
  require(sword.is(Asterion.FORGED_SWORD),"Forged parts did not assemble into a sword");
  require(sword.getMaxDamage()>250,"Forged sword lost its material durability");
  sword.setDamageValue(9);
  var ops=net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,level.registryAccess());
  var saved=ItemStack.CODEC.encodeStart(ops,sword).result().orElseThrow();
  var restored=ItemStack.CODEC.parse(ops,saved).result().orElseThrow();
  require(restored.getDamageValue()==9 && restored.getMaxDamage()==sword.getMaxDamage(),"Forged durability failed the saved-item round trip");
  VersionGameplay.verifySword(restored);
  player.getInventory().setItem(0,restored);player.getInventory().selected=0;
  player.getInventory().add(new ItemStack(Asterion.TARNISHED_GOLD_INGOT));
  player.inventoryMenu.broadcastChanges();
  Asterion.LOGGER.info("ASTERION_GAMEPLAY forging, mixed-material sword, crafting and persistence PASSED");
 }
 static void spawnEncounter(ServerPlayer player) {
  var level=player.serverLevel();var commands=level.getServer().getCommands();var source=level.getServer().createCommandSourceStack();
  for(String command:List.of("execute in asterion:asterion_dimension run fill -15 178 -15 15 190 15 minecraft:air",
     "execute in asterion:asterion_dimension run fill -15 177 -15 15 177 15 minecraft:stone",
     "execute in asterion:asterion_dimension run tp @a 0 178 -5 0 0")) commands.performPrefixedCommand(source,command);
  // Remove the visual fixture without triggering the Minotaur's world-ending finale.
  for(var minotaur:level.getEntitiesOfClass(net.krodark.asterion.entity.MinotaurEntity.class,player.getBoundingBox().inflate(40))) minotaur.discard();
  player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
  player.getAbilities().invulnerable=true;player.onUpdateAbilities();
  var boss=net.krodark.asterion.game.GameplayContent.CURSED_BRAZIER.create(level);
  require(boss!=null,"Cursed Brazier entity is missing");boss.moveTo(0,178,3,180,0);level.addFreshEntity(boss);bossId=boss.getId();
  for(int i=0;i<7;i++) {
   double angle=i*Math.PI*2/7;BlockPos pos=new BlockPos((int)Math.round(Math.cos(angle)*11),178,3+(int)Math.round(Math.sin(angle)*11));
   braziers.add(pos);level.setBlock(pos,Asterion.GREEK_BRAZIER.defaultBlockState(),3);
  }
 }
 static CursedBrazierEntity boss(ServerPlayer player) {return (CursedBrazierEntity)player.serverLevel().getEntity(bossId);}
 static void diagnose(ServerPlayer player) {
  var b=boss(player);
  Asterion.LOGGER.info("ASTERION_GAMEPLAY waiting: phase={}, age={}, player={}, boss={}, safeRune={}, room={}/{}",b==null?null:b.phase(),b==null?0:b.phaseAge(0),player.position(),b==null?null:b.position(),net.krodark.asterion.worldgen.WorldGenerator.isNearSafeRune(player.serverLevel(),player.blockPosition()),net.krodark.asterion.worldgen.AuthoredCatacombs.cursedBrazierRoomIndex(player.blockPosition()),b==null?-1:net.krodark.asterion.worldgen.AuthoredCatacombs.cursedBrazierRoomIndex(b.blockPosition()));
 }
 static void verifyShield(ServerPlayer player) {
  var boss=boss(player);require(boss!=null && boss.phase()==CursedBrazierEntity.Phase.ACTIVE,"Awakening did not reach combat: phase="+(boss==null?null:boss.phase())+", age="+(boss==null?0:boss.phaseAge(0))+", player="+player.position()+", boss="+(boss==null?null:boss.position()));
  require(boss.shielded(),"Seven lit braziers did not activate the shield");
  require(!boss.hurt(player.damageSources().playerAttack(player),10),"Shield must reject damage");
  for(var pos:braziers) player.serverLevel().setBlock(pos,Asterion.GREEK_BRAZIER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,false),3);
  Asterion.LOGGER.info("ASTERION_GAMEPLAY awakening, shield and blocked damage PASSED");
 }
 static void sampleCombat(ServerPlayer player) {
  var boss=boss(player);if(boss!=null && boss.attack()!=CursedBrazierEntity.Attack.NONE) attacks.add(boss.attack());
 }
 static void defeat(ServerPlayer player) {
  var boss=boss(player);require(boss!=null && !boss.shielded(),"Extinguishing braziers did not release the shield");
  require(!attacks.isEmpty(),"Miniboss never began an attack");
  require(boss.hurt(player.damageSources().playerAttack(player),10000),"Unshielded boss must accept damage");
  require(!boss.isAlive(),"Boss defeat failed");
  VersionGameplay.advancement(player,"defeat_brazier");
  player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
  Asterion.LOGGER.info("ASTERION_GAMEPLAY combat, defeat and advancement PASSED; attacks={}",attacks);
 }
}
