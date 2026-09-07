package net.krodark.asterion.dev.verification;

import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.EncounterProximity;
import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public final class EncounterProximityGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        Vec3 origin = new Vec3(100, 40, 100);
        check(EncounterProximity.isNear(origin.add(12, 5, 0), origin), "Entrance boundary excluded");
        check(!EncounterProximity.isNear(origin.add(12.01, 0, 0), origin), "Distant player included");
        check(!EncounterProximity.isNear(origin.add(0, 5.01, 0), origin), "Different floor included");
        check(!EncounterProximity.isNear(origin.add(10, 0, 10), origin), "Range must be radial");
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var level = server.getLevel(Asterion.ASTERION_LEVEL);
                var trigger = server.getPlayerList().getPlayers().getFirst();
                var entrance = MinotaurArenaEntrances.PLAYER_ENTRANCE;
                Vec3 door = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(entrance));
                trigger.setGameMode(GameType.SURVIVAL);
                trigger.teleportTo(level, door.x, door.y, door.z + 3, Set.of(), 180, 0, true);
                var outsider = new ServerPlayer(server, level,
                        new GameProfile(UUID.randomUUID(), "DistantObserver"), ClientInformation.createDefault());
                outsider.connection = trigger.connection;
                outsider.setGameMode(GameType.SURVIVAL);
                Vec3 outside = door.add(35, 0, -20);
                outsider.setPos(outside);
                level.addNewPlayer(outsider);
                var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
                try {
                    BossArenaEncounter.clear();
                    BossArenaEncounter.begin(level, outsider, boss, entrance);
                    check(!BossArenaEncounter.isSealed(level), "Far player started encounter");
                    BossArenaEncounter.begin(level, trigger, boss, entrance);
                    check(BossArenaEncounter.isParticipant(trigger), "Nearby trigger not admitted");
                    check(BossArenaEncounter.isMovementLocked(trigger), "Nearby trigger missed intro lock");
                    check(!BossArenaEncounter.isParticipant(outsider), "Distant observer became participant");
                    check(!BossArenaEncounter.isMovementLocked(outsider), "Distant observer was locked");
                    check(outsider.position().equals(outside), "Distant observer was teleported");
                    outsider.setPos(door.add(0, 8, 0));
                    BossArenaEncounter.begin(level, outsider, boss, entrance);
                    check(!BossArenaEncounter.isParticipant(outsider), "Other floor joined active encounter");
                } finally {
                    BossArenaEncounter.clear();
                    level.removePlayerImmediately(outsider, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            });
            Asterion.LOGGER.info("PASS: encounter proximity boundaries, nearby admission and distant-player isolation");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
