package net.krodark.asterion.client.ragdoll;

import java.util.ArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.client.AsterionClient;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public final class RagdollPlaybackGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(5);
            context.runOnClient(c -> {
                var camera = new net.minecraft.world.entity.decoration.ArmorStand(c.level, c.player.getX(), c.player.getY() + 3, c.player.getZ());
                camera.setId(900109);
                c.level.addEntity(camera);
                c.setCameraEntity(camera);
            });
            context.waitTicks(2);
            context.runOnClient(c -> {
                check(AsterionClient.isPlayback(c), "Replay camera was not detected");
                var engine = DismembermentEngine.INSTANCE;
                int id = c.player.getId();
                var start = new RagdollStatePayload(id, c.player.getUUID(), true);
                engine.applyRemoteState(c, start);
                check(engine.isRagdolled(id), "Replay actor did not become a ragdoll");
                Vec3 origin = engine.pieces().stream().filter(p -> p.entityId == id && p.region == 1).findFirst().orElseThrow().position;
                double actorX = c.player.getX();
                c.player.move(MoverType.SELF, new Vec3(.2, 0, 0));
                check(c.player.getX() > actorX + .15, "Ragdoll input lock froze the replay actor");
                Vec3 actor = c.player.position();
                engine.followPlayerTumble(c);
                check(c.player.position().equals(actor), "Replay physics moved the recorded actor");
                var packets = new ArrayList<RagdollPosePayload.Part>();
                for (int region = 0; region < 6; region++) packets.add(new RagdollPosePayload.Part(region,
                        (float)origin.x + 2, (float)origin.y, (float)origin.z, 0, 0, 0, 1, 0, 0, 0));
                engine.applyRemotePose(c, new RagdollPosePayload(id, 50, packets));
                var torso = engine.pieces().stream().filter(p -> p.entityId == id && p.region == 1).findFirst().orElseThrow();
                check(Math.abs(torso.position.x - origin.x - 2) < .001, "Recorder's own pose was ignored");
                Vec3 paused = torso.position;
                for (int tick = 0; tick < 100; tick++) engine.tickPlayback(c);
                check(engine.isRagdolled(id) && torso.position.equals(paused), "Paused replay expired or drifted");
                packets.set(1, new RagdollPosePayload.Part(1, (float)origin.x, (float)origin.y, (float)origin.z, 0, 0, 0, 1, 0, 0, 0));
                engine.applyRemotePose(c, new RagdollPosePayload(id, 1, packets));
                check(Math.abs(torso.position.x - origin.x) < .001, "Seek discarded a lower sequence number");
                check(torso.previous.equals(torso.position), "Seek interpolated from a future pose");
                engine.applyRemoteState(c, new RagdollStatePayload(id, c.player.getUUID(), false));
                check(!engine.isRagdolled(id), "Replay recovery kept the actor hidden");
                engine.applyRemoteState(c, start);
                var fallback = engine.pieces().stream().filter(p -> p.entityId == id && p.region == 1).findFirst().orElseThrow();
                double before = fallback.position.x;
                c.player.setPos(c.player.position().add(3, 0, 0));
                engine.tickPlayback(c);
                check(Math.abs(fallback.position.x - before - 3) < .001, "Legacy recording left a frozen body behind");
                engine.clear();
                c.setCameraEntity(c.player);
                c.level.removeEntity(900109, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            });
            net.krodark.asterion.Asterion.LOGGER.info("PASS: replay actor poses, paused playback, sequence rewind, recovery, legacy movement and camera ownership");
        }
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
