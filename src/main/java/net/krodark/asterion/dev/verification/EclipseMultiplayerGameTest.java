package net.krodark.asterion.dev.verification;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public final class EclipseMultiplayerGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                var maze = server.getLevel(Asterion.ASTERION_LEVEL);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                player.teleportTo(maze, 200, 40, 200, Set.of(), 0, 0, true);
                var other = new ServerPlayer(server, maze,
                        new GameProfile(UUID.randomUUID(), "OtherLayer"), ClientInformation.createDefault());
                other.connection = player.connection;
                other.setGameMode(GameType.CREATIVE);
                other.setPos(240, 120, 200);
                maze.addNewPlayer(other);
                try {
                    check(DeadSunEventSystem.trigger(maze, DeadSunEventSystem.ECLIPSE), "Split-layer eclipse rejected");
                    Object active = active(server);
                    check(notified(active).containsAll(Set.of(player.getUUID(), other.getUUID())), "A layer missed eclipse sync");
                    other.setPos(240, 20, 200);
                    DeadSunEventSystem.tick(server);
                    check(DeadSunEventSystem.isEclipseActive(maze), "Eclipse stopped when everyone went underground");
                    check(active(server) == active, "Layer transition restarted eclipse");
                    check(notified(active).containsAll(Set.of(player.getUUID(), other.getUUID())), "Layer transition cleared recipient");
                    DeadSunEventSystem.stop(maze);
                    check(DeadSunEventSystem.trigger(maze, DeadSunEventSystem.ECLIPSE), "All-underground eclipse rejected");
                    active = active(server);
                    notified(active).remove(other.getUUID());
                    DeadSunEventSystem.tick(server);
                    check(notified(active).contains(other.getUUID()), "Underground late join did not sync");
                    for (int i = 0; i < 101; i++) DeadSunEventSystem.tick(server);
                    check(DeadSunEventSystem.isEclipseActive(maze), "Periodic resync ended underground eclipse");
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                } finally {
                    DeadSunEventSystem.stop(maze);
                    maze.removePlayerImmediately(other, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                }
            });
            Asterion.LOGGER.info("PASS: shared eclipse across layers, all-underground start, transitions and late-join synchronization");
        }
    }

    private static Object active(net.minecraft.server.MinecraftServer server) throws ReflectiveOperationException {
        var states = DeadSunEventSystem.class.getDeclaredField("STATES");
        states.setAccessible(true);
        Object state = ((Map<?, ?>) states.get(null)).get(server);
        var active = state.getClass().getDeclaredField("active");
        active.setAccessible(true);
        return active.get(state);
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> notified(Object active) throws ReflectiveOperationException {
        var field = active.getClass().getDeclaredField("notifiedPlayers");
        field.setAccessible(true);
        return (Set<UUID>) field.get(active);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
