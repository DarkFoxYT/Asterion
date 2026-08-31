package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.*;
import net.krodark.asterion.block.*;
import net.krodark.asterion.game.*;
import net.krodark.asterion.effect.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public final class GameplayGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("tp @a 0 122 -10 0 8");
            server.runOnServer(mc -> {
                var level = mc.overworld(); var player = mc.getPlayerList().getPlayers().getFirst();
                for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++) level.setBlock(new BlockPos(x,120,z), Blocks.STONE.defaultBlockState(),3);
                player.setGameMode(GameType.SURVIVAL);
                for (var block : new net.minecraft.world.level.block.Block[]{Asterion.MAZESTEEL_BLOCK,Asterion.MAZESTEEL_BARS,Asterion.MAZESTEEL_CHAIN,Asterion.LAMENTER}) {
                    for (var tool : new Item[]{Items.AIR,Items.WOODEN_PICKAXE,Items.NETHERITE_PICKAXE}) {
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(tool));
                        check(Math.abs(block.defaultBlockState().getDestroyProgress(player,level,BlockPos.ZERO)-1F/1600) < 1e-8,"Mazesteel tool changed 80-second mining duration");
                    }
                }
                int distance = AsterionConfig.INSTANCE.gatewayDistance; AsterionConfig.INSTANCE.gatewayDistance=5000;
                for(int seed=0;seed<100;seed++) check(Vec3.atLowerCornerOf(WorldGenerator.gatewayPosition(seed)).horizontalDistance() <=1000,"Portal farther than 1000 blocks");
                AsterionConfig.INSTANCE.gatewayDistance=distance;

                BlockPos trapPos=new BlockPos(0,121,0);
                var state=GameplayContent.FIRE_BURST_TRAP.defaultBlockState().setValue(TimedTrapBlock.FACING,Direction.SOUTH);
                level.setBlock(trapPos,state,3);
                var trap=(TimedTrapBlockEntity)level.getBlockEntity(trapPos); trap.setPeriodSeconds(3);
                for(int i=0;i<55;i++)TimedTrapBlockEntity.tick(level,trapPos,state,trap);
                var saved=trap.saveWithFullMetadata(level.registryAccess());
                var restored=(TimedTrapBlockEntity)BlockEntity.loadStatic(trapPos,state,saved,level.registryAccess());
                check(restored!=null&&restored.periodSeconds()==3,"Trap period lost on reload");
                var victim=EntityType.COW.create(level,EntitySpawnReason.COMMAND);victim.setPos(.5,121,4.5);
                victim.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);victim.setHealth(100);victim.setNoAi(true);level.addFreshEntity(victim);
                for(int i=0;i<4;i++)TimedTrapBlockEntity.tick(level,trapPos,state,restored);
                check(victim.getHealth()==100,"Trap phase changed on reload");
                TimedTrapBlockEntity.tick(level,trapPos,state,restored);
                check(victim.getHealth()==86&&victim.hasEffect(SingedEffect.TYPE),"Fire burst failed damage/Singed contact");
                for(int i=0;i<7;i++){victim.invulnerableTime=0;TimedTrapBlockEntity.tick(level,trapPos,state,restored);}
                check(victim.getHealth()==86,"Single burst damaged more than once");
                level.setBlock(trapPos.south(2),Blocks.STONE.defaultBlockState(),3);victim.invulnerableTime=0;victim.clearFire();
                for(int i=0;i<60;i++)TimedTrapBlockEntity.tick(level,trapPos,state,restored);
                check(victim.getHealth()==86,"Trap damaged through solid wall");victim.discard();
                level.setBlock(trapPos.south(2),Blocks.AIR.defaultBlockState(),3);
                restored.setPeriodSeconds(0);check(restored.periodSeconds()==1,"Minimum trap period not clamped");
                restored.setPeriodSeconds(90);check(restored.periodSeconds()==60,"Maximum trap period not clamped");

                BlockPos support = new BlockPos(6,121,0);
                for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.STONE_SLAB,Blocks.STONE_STAIRS}){
                    level.setBlock(support,block.defaultBlockState(),3);check(net.krodark.asterion.entity.BugSurfaces.allowed(level,support),"Legal bug support rejected");
                }
                for(var block:new net.minecraft.world.level.block.Block[]{Blocks.OAK_FENCE,Blocks.IRON_BARS,Blocks.OAK_LEAVES,Blocks.SHORT_GRASS}){
                    level.setBlock(support,block.defaultBlockState(),3);
                    if(block!=Blocks.OAK_LEAVES) check(!net.krodark.asterion.entity.BugSurfaces.allowed(level,support),"Thin bug support accepted");
                }
                for(var type:new EntityType<?>[]{Asterion.BOMBARDIER_BEETLE,Asterion.RUNE_BEETLE,Asterion.SCARLET_CENTIPEDE})
                    check(((LivingEntity)type.create(level,EntitySpawnReason.COMMAND)).canBreatheUnderwater(),"Bug can drown");

                var scars=SingedScars.get(mc);
                int before=scars.lostHearts(player);scars.scar(player);
                check(scars.lostHearts(player)==before+1&&player.getMaxHealth()==18,"Permanent heart scar not applied");
                var value=SingedScars.class.getDeclaredField("CODEC");value.setAccessible(true);
                @SuppressWarnings("unchecked") var codec=(com.mojang.serialization.Codec<SingedScars>)value.get(null);
                var encoded=codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE,scars).getOrThrow();
                var reload=codec.parse(net.minecraft.nbt.NbtOps.INSTANCE,encoded).getOrThrow();
                check(reload.lostHearts(player)==scars.lostHearts(player),"Scars lost when saved");
                for(int i=0;i<20;i++)scars.scar(player);
                check(player.getMaxHealth()==2,"Scars reduced health below one heart");
                player.setGameMode(GameType.CREATIVE);
                check(Asterion.RUNE_BEETLE.create(level,EntitySpawnReason.COMMAND)!=null,"Rune beetle not registered");
                var beetle=Asterion.RUNE_BEETLE.create(level,EntitySpawnReason.COMMAND);beetle.setRuneIndex(17);beetle.setPos(-2,121,-1);level.addFreshEntity(beetle);
                var brazier=GameplayContent.CURSED_BRAZIER.create(level,EntitySpawnReason.COMMAND);brazier.setPos(4,121,2);level.addFreshEntity(brazier);
                level.setBlock(new BlockPos(-3,121,2),GameplayContent.SPEWER.defaultBlockState(),3);
                level.setBlock(new BlockPos(2,121,-2),GameplayContent.BEAR_TRAP.defaultBlockState(),3);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(GameplayContent.FLAMETHROWER));
                Asterion.LOGGER.info("PASS: fixed mining time, portal range, saved trap phase, damage/occlusion, bug support/breathing and persistent Singed scars");
            });
            context.waitTicks(20);
            context.takeScreenshot("new-traps-rune-beetle-cursed-brazier");
            var gasTarget = new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.animal.cow.Cow>();
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.MOB_DROPS, true, mc);
                var beetle = level.getEntitiesOfClass(net.krodark.asterion.entity.RuneBeetleEntity.class,
                        player.getBoundingBox().inflate(30)).getFirst();
                beetle.hurtServer(level, level.damageSources().playerAttack(player), 100);
                check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        beetle.getBoundingBox().inflate(3)).stream().anyMatch(item -> item.getItem().is(Asterion.RUNE_TABLETS[17])),
                        "Rune beetle did not drop its matching tablet");
                GasClouds.clear();
                var cow = EntityType.COW.create(level, EntitySpawnReason.COMMAND);
                cow.setPos(0, 121, -5); cow.setNoAi(true);
                cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                cow.setHealth(100); level.addFreshEntity(cow); gasTarget.set(cow);
            });
            server.runCommand("tp @a 0 121 -10 0 0");
            context.waitTicks(5);
            context.getInput().holdKey(options -> options.keyUse);
            context.waitTicks(24);
            context.runOnClient(client -> check(client.player.isUsingItem(), "Right click did not start gas spray"));
            context.getInput().pressKey(options -> options.keyAttack);
            context.getInput().releaseKey(options -> options.keyUse);
            context.waitTicks(18);
            server.runOnServer(mc -> {
                check(gasTarget.get().getHealth() < 100 && gasTarget.get().hasEffect(SingedEffect.TYPE),
                        "Left click while spraying did not ignite gas and damage the target");
                Asterion.LOGGER.info("PASS: matching rune-beetle loot and actual right-click spray / left-click ignition");
            });
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
