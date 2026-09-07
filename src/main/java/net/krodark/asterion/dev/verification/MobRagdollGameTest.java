package net.krodark.asterion.dev.verification;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

public final class MobRagdollGameTest implements FabricClientGameTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("execute in asterion:asterion_dimension run tp @a 200 200 200");
            context.waitTicks(20);
            context.runOnClient(client -> {
                var engine = DismembermentEngine.INSTANCE;
                engine.clear();
                var brazier = net.krodark.asterion.game.GameplayContent.CURSED_BRAZIER
                        .create(client.level, EntitySpawnReason.COMMAND);
                check(!engine.ragdoll(brazier, 1, client.player.position(), Vec3.ZERO, .35, false),
                        "Cursed Brazier must not ragdoll");
                for (var type : List.of(Asterion.CONSTRUCT,
                        net.krodark.asterion.game.AncientContent.SKELETON,
                        Asterion.BOMBARDIER_BEETLE, Asterion.RUNE_BEETLE,
                        Asterion.QUEEN_BEETLE, Asterion.SCARLET_CENTIPEDE)) {
                var mob = type.create(client.level, EntitySpawnReason.COMMAND);
                check(mob != null, "Could not create " + type);
                mob.setId(900002);
                mob.setPos(client.player.position().add(8, 12, 0));
                client.level.addEntity(mob);
                mob.setHealth(0);
                check(engine.ragdoll(mob, 1, mob.position(), Vec3.ZERO, .35, false), type + " did not ragdoll");
                // A replaced body must never reach the render collector (vanilla or GeckoLib).
                net.minecraft.client.renderer.entity.EntityRenderer renderer =
                        client.getEntityRenderDispatcher().getRenderer(mob);
                renderer.submit(renderer.createRenderState(mob, 1.0F),
                        new com.mojang.blaze3d.vertex.PoseStack(), null, null);
                try {
                    var piecesField = DismembermentEngine.class.getDeclaredField("pieces");
                    piecesField.setAccessible(true);
                    List<?> pieces = (List<?>) piecesField.get(engine);
                    check(pieces.size() == 6, "Incomplete mob body");
                    Object torso = pieces.getFirst();
                    var position = torso.getClass().getDeclaredField("position");
                    position.setAccessible(true);
                    double startY = ((Vec3) position.get(torso)).y;
                    client.level.removeEntity(mob.getId(), Entity.RemovalReason.KILLED);
                    for (int tick = 0; tick < 30; tick++) engine.tick(client.level, client.player);
                    check(((Vec3) position.get(torso)).y < startY - 1, "Corpse froze in midair after entity removal");
                    check(pieces.size() == 6, "Corpse disappeared after one second");
                    for (int tick = 30; tick < 599; tick++) engine.tick(client.level, client.player);
                    check(pieces.size() == 6, "Corpse expired before 30 seconds");
                    engine.tick(client.level, client.player);
                    check(pieces.isEmpty() && !engine.isRagdolled(mob.getId()), "Corpse did not expire at 600 ticks");
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                } finally {
                    engine.clear();
                }
                Asterion.LOGGER.info("PASS: {} corpse gravity, entity removal and 600-tick lifetime", type);
                }
            });
            Asterion.LOGGER.info("PASS: mob corpse rendering, gravity, lifetime and Cursed Brazier exclusion");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
