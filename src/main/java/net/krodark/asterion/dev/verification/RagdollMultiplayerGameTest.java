package net.krodark.asterion.dev.verification;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.*;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native packet delivery to an observer, plus server lifecycle and room-miniboss checks. */
public final class RagdollMultiplayerGameTest implements FabricClientGameTest {
    private static final int REMOTE_ID = 900001;
    private static final UUID REMOTE_UUID = UUID.fromString("8e293820-3204-4aca-b794-0b80b4878eba");
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("tp @a 0 121 8 180 0");
            server.runOnServer(mc -> {
                var level = mc.overworld(); var player = mc.getPlayerList().getPlayers().getFirst();
                for (int x=-12;x<=12;x++) for (int z=-12;z<=12;z++) level.setBlock(new BlockPos(x,120,z),Blocks.STONE.defaultBlockState(),3);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.setInvulnerable(true);
                var boss = GameplayContent.CURSED_BRAZIER.create(level,net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0,121,0); level.addFreshEntity(boss);
                check(boss.getMaxHealth()==140,"Brazier still has ordinary-mob health");
                EnumSet<CursedBrazierEntity.Attack> attacks=EnumSet.noneOf(CursedBrazierEntity.Attack.class);
                for(int i=0;i<900;i++){ boss.tick(); attacks.add(boss.attack()); }
                check(attacks.containsAll(List.of(CursedBrazierEntity.Attack.FLOOR_JETS,
                        CursedBrazierEntity.Attack.FIRE_BEAM,CursedBrazierEntity.Attack.SPIN_TORNADO,
                        CursedBrazierEntity.Attack.CARDINAL_DASH)),
                        "Miniboss did not use all four fire attacks; observed " + attacks);
                var field=CursedBrazierEntity.class.getDeclaredField("bossBar");field.setAccessible(true);
                var bar=(net.minecraft.server.level.ServerBossEvent)field.get(boss);
                check(bar.getPlayers().contains(player),"Miniboss health bar missing");
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);boss.tick();
                check(bar.getPlayers().isEmpty(),"Miniboss bar remained on ineligible player");
                boss.die(level.damageSources().generic());
                var clouds=GasClouds.class.getDeclaredField("CLOUDS");clouds.setAccessible(true);
                var map=(Map<?,?>)clouds.get(null);
                check(((List<?>)map.getOrDefault(level,null)) == null || ((List<?>)map.get(level)).isEmpty(),"Dead miniboss left controlled flames behind");
                boss.discard(); GasClouds.clear();
                Asterion.LOGGER.info("PASS: Cursed Brazier miniboss health, four fire attacks, boss-bar cleanup and owned-flame cleanup");
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 200 121 200 180 0");
            context.waitTicks(10);
            server.runOnServer(mc -> {
                var player=mc.getPlayerList().getPlayers().getFirst(); var level=player.level();
                for(int x=194;x<=206;x++)for(int z=190;z<=206;z++) {
                    level.setBlock(new BlockPos(x,120,z),Blocks.STONE.defaultBlockState(),3);
                    for(int y=121;y<=125;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                }
                player.teleportTo(200.5,121,200.5);
                var method=RagdollServerNetworking.class.getDeclaredMethod("exitTumble",net.minecraft.server.level.ServerPlayer.class,TumbleExitPayload.class);method.setAccessible(true);
                RagdollServerNetworking.markRagdolled(player,80);
                Vec3 p=player.position();
                method.invoke(null,player,new TumbleExitPayload(p.x,p.y,p.z,0,0,0,false));
                check(RagdollServerNetworking.isRagdolled(player),"Movement packet ended the server ragdoll");
                method.invoke(null,player,new TumbleExitPayload(p.x,p.y,p.z,0,0,0,true));
                check(!RagdollServerNetworking.isRagdolled(player),"Get-up packet did not end the server ragdoll");
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                var remote=new net.minecraft.client.player.RemotePlayer(client.level,new com.mojang.authlib.GameProfile(REMOTE_UUID,"RagdollSubject"));
                remote.setId(REMOTE_ID);remote.setPos(200,121,196);client.level.addEntity(remote);
            });
            server.runOnServer(mc -> {
                var viewer=mc.getPlayerList().getPlayers().getFirst();
                ServerPlayNetworking.send(viewer,new RagdollStatePayload(REMOTE_ID,REMOTE_UUID,true));
                var parts=new ArrayList<RagdollPosePayload.Part>();
                for(int region=0;region<6;region++)parts.add(new RagdollPosePayload.Part(region,200,122,196,0,0,0,1,0,0,0));
                ServerPlayNetworking.send(viewer,new RagdollPosePayload(REMOTE_ID,1,parts));
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var engine=DismembermentEngine.INSTANCE;
                check(engine.isRagdolled(REMOTE_ID)&&engine.isPlayerTumbling(REMOTE_ID),"Observer did not hide the remote player's normal body");
                check(!engine.isPlayerTumbling(client.player.getId()),"Remote throw ragdolled the observer instead");
            });
            server.runOnServer(mc -> ServerPlayNetworking.send(mc.getPlayerList().getPlayers().getFirst(),new RagdollStatePayload(REMOTE_ID,REMOTE_UUID,false)));
            context.waitTicks(3);
            context.runOnClient(client -> {
                var engine=DismembermentEngine.INSTANCE;
                check(!engine.isRagdolled(REMOTE_ID)&&!engine.isPlayerTumbling(REMOTE_ID)&&engine.hiddenRegions(REMOTE_ID).isEmpty(),"Stand-up left a duplicate remote body");
                engine.applyRemoteState(client,new RagdollStatePayload(REMOTE_ID,REMOTE_UUID,true));
                for(int i=0;i<65;i++)engine.tick(client.level,client.player);
                check(!engine.isRagdolled(REMOTE_ID),"Expired remote stream left an orphan ragdoll");
                client.level.removeEntity(REMOTE_ID,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            });
            Asterion.LOGGER.info("PASS: server tumble movement/get-up, observer packet identity, normal-body hiding and remote-body cleanup");
        } catch(Exception e){throw new AssertionError(e);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
