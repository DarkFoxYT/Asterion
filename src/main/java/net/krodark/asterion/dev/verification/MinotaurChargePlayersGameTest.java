package net.krodark.asterion.dev.verification;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;

public final class MinotaurChargePlayersGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
                player.setPos(0, 200, 0);
                var other = new ServerPlayer(server, level,
                        new GameProfile(UUID.randomUUID(), "ChargeBystander"), ClientInformation.createDefault());
                other.connection = player.connection;
                other.setGameMode(GameType.SURVIVAL);
                other.setPos(1, 200, 0);
                level.addNewPlayer(other);
                var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
                boss.setPos(0, 200, -2);
                boss.setTarget(player);
                var area = new AABB(-2, 199, -1, 2, 203, 1);
                var impulse = new Vec3(0, .7, 3);
                var hit = MinotaurEntity.class.getDeclaredMethod("hitChargePlayers", ServerLevel.class,
                        AABB.class, float.class, Vec3.class, float.class);
                hit.setAccessible(true);
                try {
                    float before = other.getHealth();
                    check((boolean)hit.invoke(boss, level, area, 4F, impulse, 1.6F), "No collision detected");
                    check(other.getHealth() < before, "Non-target player took no damage");
                    check(RagdollServerNetworking.isRagdolled(other), "Non-target player was not ragdolled");
                    check(RagdollServerNetworking.isRagdolled(player), "Target was skipped after another hit");
                    check(other.getDeltaMovement().equals(impulse), "Non-target player missed charge impulse");
                    other.invulnerableTime = 0;
                    before = other.getHealth();
                    check(!(boolean)hit.invoke(boss, level, area, 4F, impulse, 1.6F), "Same charge hit twice");
                    check(other.getHealth() == before, "Contact repeated damage");
                    var finish = MinotaurEntity.class.getDeclaredMethod("finishCorridorCharge");
                    finish.setAccessible(true);
                    finish.invoke(boss);
                    other.setPos(8, 200, 0);
                    player.setGameMode(GameType.CREATIVE);
                    check(!(boolean)hit.invoke(boss, level, area, 4F, impulse, 1.6F), "Hit outside lane or creative player");
                    other.setPos(1, 200, 0);
                    check((boolean)hit.invoke(boss, level, area, 4F, impulse, 1.6F), "New charge could not hit again");
                } finally {
                    level.removePlayerImmediately(other, Entity.RemovalReason.DISCARDED);
                }
                Asterion.LOGGER.info("PASS: charge hits target and bystander, ragdolls both, excludes outsiders and repeats only on a new charge");
            });
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
