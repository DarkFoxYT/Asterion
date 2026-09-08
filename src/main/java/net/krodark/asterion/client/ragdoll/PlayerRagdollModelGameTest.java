package net.krodark.asterion.client.ragdoll;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.Vec3;

public final class PlayerRagdollModelGameTest implements FabricClientGameTest {
    private static final class Subject extends RemotePlayer {
        PlayerSkin skin = skin(PlayerModelType.WIDE, "wide/steve");
        Subject(ClientLevel level) { super(level, new GameProfile(UUID.randomUUID(), "RagdollModel")); }
        @Override public PlayerSkin getSkin() { return skin; }
    }
    private static PlayerSkin skin(PlayerModelType model, String name) {
        return new PlayerSkin(new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace(
                "entity/player/" + name)), null, null, model, true);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(5);
            context.runOnClient(c -> {
                var engine = DismembermentEngine.INSTANCE;
                var player = new Subject(c.level);
                player.setId(900105);
                player.setPos(c.player.position().add(0, 0, 3));
                player.setYRot(180);
                player.yBodyRot = player.yBodyRotO = 180;
                player.yHeadRot = player.yHeadRotO = 180;
                c.level.addEntity(player);
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features")) {
                    net.minecraft.client.renderer.entity.EntityRenderer renderer = c.getEntityRenderDispatcher().getRenderer(player);
                    var model = ((net.minecraft.client.renderer.entity.LivingEntityRenderer)renderer).getModel();
                    try {
                        var stateGetter = Class.forName("traben.entity_texture_features.features.state.ETFState").getMethod("state");
                        Object previous = stateGetter.invoke(null);
                        check(RagdollModelCompatibility.setup(model, renderer.createRenderState(player, 1)), "Fresh Moves face animation did not run");
                        check(previous == stateGetter.invoke(null), "Face animation leaked another player's render context");
                    } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                }
                // Seed a rendered wide-player pose, then simulate the real slim skin arriving.
                engine.captureRenderedPose(player.getId());
                player.skin = skin(PlayerModelType.SLIM, "slim/alex");
                check(engine.ragdoll(player, 1, player.position(), Vec3.ZERO, .7, false), "Ragdoll did not start");
                verify(engine, player, true);
                // A skin/model update during physics must update both the texture and arm geometry.
                player.skin = skin(PlayerModelType.WIDE, "wide/steve");
                engine.tick(c.level, c.player);
                verify(engine, player, false);
                engine.applyRemoteState(c, new net.krodark.asterion.network.ragdoll.RagdollStatePayload(
                        player.getId(), player.getUUID(), true));
                c.player.setYRot(0);
                c.player.setXRot(0);
            });
            context.waitTicks(2);
            context.takeScreenshot("player-ragdoll-model");
            context.runOnClient(c -> {
                DismembermentEngine.INSTANCE.clear();
                c.level.removeEntity(900105, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            });
            net.krodark.asterion.Asterion.LOGGER.info("PASS: player skin texture, six body regions, outer layers, stale pose and late slim/wide model updates");
        }
    }
    private static void verify(DismembermentEngine engine, Subject player, boolean slim) {
        var body = engine.pieces().stream().filter(p -> p.entityId == player.getId() && p.region <= 5).toList();
        check(body.size() == 6, "Missing player body regions");
        for (var part : body) {
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features"))
                check(!part.modelBoxes.isEmpty(), "Missing EMF model mesh for body region " + part.region);
            check(part.texture.equals(player.skin.body().texturePath()), "Wrong player skin texture");
            check(part.overlayFaceUvs != null, "Missing player outer skin layer");
            for (var face : part.faceUvs) check(face != null && face.length == 8, "Missing model UV face");
            if (part.region == 2 || part.region == 3)
                check(Math.abs(part.halfExtents.x - (slim ? .09 : .12)) < .001,
                        "Wrong " + (slim ? "slim" : "wide") + " arm geometry: " + part.halfExtents);
        }
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
