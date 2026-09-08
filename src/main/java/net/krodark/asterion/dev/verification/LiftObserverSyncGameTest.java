package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.ChainLiftEntity;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;

public final class LiftObserverSyncGameTest implements FabricClientGameTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                try { ChainLiftCheck.run(server); }
                catch (Exception error) { throw new AssertionError(error); }
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                var lift = ChainLiftContent.LIFT.create(client.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                lift.setId(900101); lift.configure(new BlockPos(0, 100, 0), 150); client.level.addEntity(lift);
                var remote = new net.minecraft.client.player.RemotePlayer(client.level,
                        new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "LiftObserverSubject"));
                remote.setId(900102); client.level.addEntity(remote);
                try {
                    var field = ChainLiftEntity.class.getDeclaredField("RIDERS"); field.setAccessible(true);
                    @SuppressWarnings("unchecked") var key = (net.minecraft.network.syncher.EntityDataAccessor<String>)field.get(null);
                    lift.getEntityData().set(key, ",900102,");
                    net.minecraft.client.renderer.entity.EntityRenderer renderer = client.getEntityRenderDispatcher().getRenderer(remote);
                    for (int direction : new int[]{-1, 1}) for (int tick = 0; tick < 60; tick++) {
                        double y = 125 + direction * tick * .18;
                        lift.setPos(.5, y, .5); lift.yo = y - direction * .18;
                        // Simulate ten ticks of delayed remote movement packets.
                        remote.setPos(.5, y + .5 - direction * 1.8, .5); remote.yo = remote.getY() - direction * .18;
                        for (float partial : new float[]{0, .25F, .5F, .75F, 1}) {
                            var state = renderer.createRenderState();
                            renderer.extractRenderState(remote, state, partial);
                            if (state.passengerOffset == null || Math.abs(state.y + state.passengerOffset.y - lift.renderedDeckY(partial)) > .00001)
                                throw new AssertionError("Observer rider drifted from moving deck");
                        }
                    }
                    lift.getEntityData().set(key, "");
                    if (ChainLiftEntity.renderSupport(remote) != null) throw new AssertionError("Disembarked rider stayed attached");
                    lift.getEntityData().set(key, ",900102,");
                    remote.setPos(5, lift.getY() + .5, .5);
                    if (ChainLiftEntity.renderSupport(remote) != null) throw new AssertionError("Walking off retained deck attachment");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                finally { remote.discard(); lift.discard(); }
            });
            Asterion.LOGGER.info("PASS: observer feet aligned with deck in both directions under delayed packets; dismount/walk-off and server two-player lift checks");
        }
    }
}
