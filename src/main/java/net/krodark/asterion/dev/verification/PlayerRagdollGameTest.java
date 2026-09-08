package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.world.phys.Vec3;

public final class PlayerRagdollGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("tp @a 200 121 200");
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                player.setInvulnerable(true);
                for (var pos : net.minecraft.core.BlockPos.betweenClosed(190, 120, 190, 210, 120, 210))
                    player.level().setBlock(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 18);
            });
            context.waitTicks(70);
            context.runOnClient(c -> {
                c.player.setPos(200, 200, 200);
                DismembermentEngine.INSTANCE.togglePlayerTumble(c);
                check(DismembermentEngine.INSTANCE.isPlayerTumbling(c.player.getId()), "Manual ragdoll did not start");
            });
            context.waitTicks(10);
            context.runOnClient(c -> {
                var engine = DismembermentEngine.INSTANCE;
                check(engine.isPlayerTumbling(c.player.getId()), "Manual ragdoll was immediately removed");
                check(c.player.getY() < 199, "Ragdoll gravity did not move the player");
                engine.releaseRagdoll(c.player.getId());
            });
            context.waitTicks(5);
            context.runOnClient(c -> {
                DismembermentEngine.INSTANCE.forcePlayerTumble(c, c.player.position(), new Vec3(1, .5, 0), 1);
            });
            context.waitTicks(10);
            context.runOnClient(c -> check(DismembermentEngine.INSTANCE.isPlayerTumbling(c.player.getId()), "Forced ragdoll was removed"));
            context.runOnClient(c -> DismembermentEngine.INSTANCE.releaseRagdoll(c.player.getId()));
            context.waitTicks(5);
            world.getServer().runCommand("tp @a 200 121 200");
            world.getServer().runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                player.setDeltaMovement(Vec3.ZERO);
                player.resetFallDistance();
            });
            context.waitTicks(45);
            context.runOnClient(c -> {
                c.player.setPos(200, 200, 200);
                c.player.getAbilities().flying = false;
                c.player.setOnGround(false);
                c.player.fallDistance = 5;
                c.player.setDeltaMovement(0, -.8, 0);
                check(net.krodark.asterion.client.ragdoll.RagdollClientController.shouldTumbleFromFall(c), "Long fall did not trigger ragdoll");
            });
            context.waitTicks(2);
            context.runOnClient(c -> check(DismembermentEngine.INSTANCE.isPlayerTumbling(c.player.getId()), "Fall trigger did not create a player ragdoll"));
            Asterion.LOGGER.info("PASS: local manual and forced ragdolls persist and simulate gravity");
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
