package net.krodark.asterion.dev.verification;

import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class GrappleReleaseGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(server.overworld(), 0, 121, 0, Set.of(), 0, 0, true);
                player.setInvulnerable(true);
                for (BlockPos p : BlockPos.betweenClosed(-12,120,-12,12,120,12))
                    server.overworld().setBlock(p, Asterion.ANCIENT_BRICKS.defaultBlockState(), 18);
            });
            context.waitTicks(15);
            for (int scenario = 0; scenario < 3; scenario++) {
                final int mode = scenario;
                world.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().getFirst();
                    var level = server.overworld();
                    var boss = Asterion.MINOTAUR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    boss.setPos(0,121,3); boss.beginDebug(player); boss.setDebugRunning(false);
                    level.addFreshEntity(boss);
                    try {
                        var grabbed = MinotaurEntity.class.getDeclaredField("grabbedPlayer"); grabbed.setAccessible(true);
                        grabbed.set(boss, player.getUUID());
                        var thrown = MinotaurEntity.class.getDeclaredField("thrownPlayer"); thrown.setAccessible(true);
                        thrown.set(boss, player.getUUID());
                        var held = MinotaurEntity.class.getDeclaredField("DATA_HELD_PLAYER"); held.setAccessible(true);
                        @SuppressWarnings("unchecked") var key = (net.minecraft.network.syncher.EntityDataAccessor<Integer>)held.get(null);
                        boss.getEntityData().set(key, player.getId());
                        RagdollServerNetworking.markRagdolled(player, 200);
                        check(MinotaurEntity.isHeld(player) && MinotaurEntity.controlsPlayer(player), "Fixture did not lock player");
                        if (mode == 0) {
                            var defeat = MinotaurEntity.class.getDeclaredMethod("beginDefeated", net.minecraft.server.level.ServerLevel.class);
                            defeat.setAccessible(true); defeat.invoke(boss, level);
                        } else if (mode == 1) boss.die(level.damageSources().genericKill());
                        else boss.discard();
                        check(boss.heldPlayerId() == -1 && grabbed.get(boss) == null && thrown.get(boss) == null, "Owner retained grapple state");
                        check(!MinotaurEntity.isHeld(player) && !MinotaurEntity.controlsPlayer(player), "Player remained locked after defeat/death/removal");
                        check(!RagdollServerNetworking.isRagdolled(player), "Ragdoll recovery was not sent");
                        check(player.getDeltaMovement().equals(Vec3.ZERO), "Throw velocity survived release");
                        if (mode == 0) {
                            // Even a stale attachment on a harvestable corpse cannot relock the player.
                            boss.getEntityData().set(key, player.getId());
                            check(!MinotaurEntity.isHeld(player), "Corpse accepted a stale hand lock");
                            boss.getEntityData().set(key, -1);
                        }
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                });
                context.waitTicks(3);
                context.runOnClient(client -> check(!MinotaurEntity.isHeld(client.player), "Client retained hand attachment"));
            }
            Asterion.LOGGER.info("PASS: grapple cleanup on harvestable defeat, death and removal; stale corpse locks rejected on client/server");
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
