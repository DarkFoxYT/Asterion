package net.krodark.asterion.dev.verification;

import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.krodark.asterion.game.ArenaDeathRecovery;
import net.krodark.asterion.worldgen.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;

public final class ArenaParticipationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("execute in asterion:asterion_dimension run tp @a 800 150 800");
            context.waitTicks(40);
            world.getServer().runOnServer(server -> {
                var level = server.getLevel(Asterion.ASTERION_LEVEL);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.teleportTo(level, 800, 150, 800, Set.of(), 0, 0, true);
                player.setGameMode(GameType.SURVIVAL);
                var teammate = new ServerPlayer(server, level,
                        new GameProfile(UUID.randomUUID(), "BrazierTeammate"), ClientInformation.createDefault());
                teammate.connection = player.connection;
                teammate.setGameMode(GameType.SURVIVAL);
                teammate.setPos(803, 150, 800);
                level.addNewPlayer(teammate);
                var boss = net.krodark.asterion.game.GameplayContent.CURSED_BRAZIER.create(level, EntitySpawnReason.COMMAND);
                boss.setPos(800, 150, 802);
                level.getChunkAt(boss.blockPosition());
                check(level.addFreshEntity(boss), "Brazier test entity was not spawned");
                check(level.getEntity(boss.getUUID()) == boss, "Brazier not visible to death handler");
                try {
                    var begin = CursedBrazierEntity.class.getDeclaredMethod("beginAwakening", ServerLevel.class);
                    begin.setAccessible(true);
                    begin.invoke(boss, level);
                    check(boss.isParticipant(player), "Brazier intro did not record participant");
                    check(boss.hasSurvivingParticipant(player), "Inside teammate excluded");
                    teammate.setPos(803, 180, 800);
                    check(!boss.hasSurvivingParticipant(player), "Other layer kept Brazier alive");
                    teammate.setPos(900, 150, 800);
                    check(!boss.hasSurvivingParticipant(player), "Outside player kept Brazier alive");
                    teammate.setPos(803, 150, 800);
                    float health = boss.getHealth();
                    player.setHealth(0);
                    check(!ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(player,
                            level.damageSources().generic(), 100), "Brazier death not intercepted");
                    check(ArenaDeathRecovery.isRecovering(player), "Brazier recovery missing");
                    check(!boss.isParticipant(player), "Defeated Brazier player not eliminated");
                    check(boss.getHealth() == health && boss.isParticipant(teammate), "Teammate fight reset");
                    level.removePlayerImmediately(teammate, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                    for (int i = 0; i < 100; i++) boss.tick();
                    check(!boss.isParticipant(player) && boss.getHealth() == boss.getMaxHealth(),
                            "Abandoned Brazier did not reset");
                    var phase = CursedBrazierEntity.class.getDeclaredMethod("phase");
                    phase.setAccessible(true);
                    check(phase.invoke(boss).toString().equals("DORMANT"), "Empty arena still active");
                } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
                finally { teammate.discard(); boss.discard(); }
                Asterion.LOGGER.info("PASS: Brazier awakening membership, survivor boundaries, death isolation and disconnect abandonment");
            });
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
