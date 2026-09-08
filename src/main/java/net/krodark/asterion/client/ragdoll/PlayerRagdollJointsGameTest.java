package net.krodark.asterion.client.ragdoll;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public final class PlayerRagdollJointsGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(5);
            context.runOnClient(c -> {
                var engine = DismembermentEngine.INSTANCE;
                var player = new RemotePlayer(c.level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "JointSubject"));
                player.setId(900106);
                player.setPos(c.player.position().add(0, 8, 3));
                c.level.addEntity(player);
                engine.ragdoll(player, 1, player.position(), new Vec3(1, .5, 0), 1, false);
                for (var part : engine.pieces()) if (part.entityId == player.getId()) {
                    part.velocity = new Vec3(.6, .8, 0);
                    part.angularVelocity = new Vec3(.2, .3, -.15);
                    if (part.anchoredJoint && part.region <= 5) {
                        check(Math.abs(part.childJointAnchor.x) <= part.halfExtents.x + .10
                                && Math.abs(part.childJointAnchor.y) <= part.halfExtents.y + .10
                                && Math.abs(part.childJointAnchor.z) <= part.halfExtents.z + .10,
                                "Joint is outside visible limb " + part.region + ": " + part.childJointAnchor);
                    }
                }
                for (int tick = 0; tick < 120; tick++) {
                    engine.tick(c.level, c.player);
                    for (var part : engine.pieces()) {
                        if (part.entityId != player.getId() || !part.anchoredJoint || part.parentRegion < 0) continue;
                        var parent = engine.pieces().stream().filter(p -> p.entityId == part.entityId && p.region == part.parentRegion).findFirst().orElseThrow();
                        for (float partial : new float[]{0, .25F, .5F, .75F, 1}) {
                            var childSocket = new Quaternionf(part.previousOrientation).slerp(part.orientation, partial).transform(part.childJointAnchor.toVector3f());
                            var parentSocket = new Quaternionf(parent.previousOrientation).slerp(parent.orientation, partial).transform(part.parentJointAnchor.toVector3f());
                            Vec3 a = engine.renderCenter(part, partial).add(childSocket.x, childSocket.y, childSocket.z);
                            Vec3 b = engine.renderCenter(parent, partial).add(parentSocket.x, parentSocket.y, parentSocket.z);
                            check(a.distanceTo(b) < .0001, "Rendered joint separated at tick " + tick + ", partial " + partial);
                        }
                    }
                }
                engine.clear();
                c.level.removeEntity(player.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            });
            net.krodark.asterion.Asterion.LOGGER.info("PASS: visible limb pivots and connected render sockets through 120 ticks of tumbling and five interpolation fractions");
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
