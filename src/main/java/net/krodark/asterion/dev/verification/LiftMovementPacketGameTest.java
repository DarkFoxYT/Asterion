package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public final class LiftMovementPacketGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(10);
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                var player = server.getPlayerList().getPlayers().getFirst();
                BlockPos anchor = new BlockPos(0, 180, 0);
                level.setBlock(anchor, ChainLiftContent.ANCHOR.defaultBlockState(), 18);
                var lift = ChainLiftContent.LIFT.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                lift.configure(anchor, 210); level.addFreshEntity(lift);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.teleportTo(level, .5, lift.getY() + .5, .5, java.util.Set.of(), 0, 0, true);
                player.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(teleportId(player)));
                player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
                player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
                lift.tick();
                try {
                    check(lift.carries(player), "Fixture did not register rider");
                    for (double staleHeight : new double[]{1.08, -1.08, .54, -.54}) {
                        double x = player.getX() == .5 ? .7 : .5;
                        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                                x, lift.getY() + .5 + staleHeight, .5, 35, 0, true, false));
                        check(Math.abs(player.getY() - lift.getY() - .5) < .00001, "Delayed packet overwrote server deck height: " + staleHeight);
                        check(Math.abs(player.getX() - x) < .00001, "Grounded packet lost horizontal walking");
                        lift.tick();
                        check(lift.carries(player), "Delayed packet dropped rider membership");
                    }
                    double deck = lift.getY() + .5;
                    player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                            player.getX(), deck + .42, .5, 35, 0, false, false));
                    check(player.getY() > deck + .3, "Jump was snapped back onto lift");
                    player.setPos(.5, deck, .5); player.setDeltaMovement(Vec3.ZERO);
                    player.connection.resetPosition();
                    player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(4, deck, .5, 0, 0, true, false));
                    check(player.getX() > 3, "Walking off was blocked");
                    lift.tick();
                    check(!lift.carries(player), "Walking off retained server rider membership");
                } finally { lift.discard(); }
            });
            Asterion.LOGGER.info("PASS: actual server movement handler rejects stale lift heights in both directions, preserves walking/jumping, and retains rider sync");
        }
    }
    private static int teleportId(net.minecraft.server.level.ServerPlayer player) {
        try {
            var field = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
            field.setAccessible(true); return field.getInt(player.connection);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
