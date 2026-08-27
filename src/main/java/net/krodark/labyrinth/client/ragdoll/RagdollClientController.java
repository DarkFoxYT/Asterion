package net.krodark.labyrinth.client.ragdoll;

import net.krodark.labyrinth.Labyrinth;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

public final class RagdollClientController {
    private static boolean tumbleWasDown;
    private static boolean shiftWasDown;
    private static boolean rightWasDown;
    private static int scanTicker;
    private static CameraType cameraBeforeTumble;
    private static boolean thirdPersonLocked;

    private RagdollClientController() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(RagdollClientController::tick);
    }

    private static void tick(Minecraft client) {
        var engine = DismembermentEngine.INSTANCE;
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Labyrinth.LABYRINTH_LEVEL)) {
            restoreCamera(client);
            engine.clear();
            tumbleWasDown = false;
            shiftWasDown = false;
            rightWasDown = false;
            return;
        }

        boolean input = client.screen == null;
        long window = client.getWindow().handle();
        boolean tumble = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
        if (input && tumble && !tumbleWasDown) {
            engine.togglePlayerTumble(client);
        }
        tumbleWasDown = tumble;

        if (input && client.player.fallDistance >= 6.0f && client.player.getDeltaMovement().y < -0.42
                && !engine.isPlayerTumbling(client.player.getId())) {
            engine.forcePlayerTumble(client, client.player.getBoundingBox().getCenter().add(0, 2, 0), Vec3.ZERO, .55f);
        }

        boolean shift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        if (input && shift && !shiftWasDown && engine.isPlayerTumbling(client.player.getId())) {
            engine.releaseRagdoll(client.player.getId());
        }
        shiftWasDown = shift;
        syncRagdollCamera(client, engine);

        boolean right = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        engine.handleRightClick(client, input && right, input && right && !rightWasDown);
        rightWasDown = right;

        engine.applyPlayerTumbleInput(client, axis(window, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_D),
                axis(window, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_W));

        if (++scanTicker % 5 == 0) {
            for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class,
                    client.player.getBoundingBox().inflate(64.0), entity -> !entity.isAlive())) {
                if (!engine.isRagdolled(entity.getId())) {
                    Vec3 motion = entity.getDeltaMovement();
                    Vec3 direction = motion.lengthSqr() > 1.0e-6 ? motion.normalize() : entity.getLookAngle();
                    engine.ragdoll(entity, 1, entity.getBoundingBox().getCenter(), direction,
                            Math.max(0.35, motion.length()), false);
                }
            }
        }

        engine.tick(client.level, client.player);
        engine.followPlayerTumble(client);
        syncRagdollCamera(client, engine);
    }

    private static float axis(long window, int negative, int positive) {
        boolean n = GLFW.glfwGetKey(window, negative) == GLFW.GLFW_PRESS;
        boolean p = GLFW.glfwGetKey(window, positive) == GLFW.GLFW_PRESS;
        return n == p ? 0 : p ? 1 : -1;
    }

    private static void syncRagdollCamera(Minecraft client, DismembermentEngine engine) {
        boolean tumbling = client.player != null && engine.isPlayerTumbling(client.player.getId());
        if (!tumbling) {
            restoreCamera(client);
            return;
        }
        if (!thirdPersonLocked) {
            cameraBeforeTumble = client.options.getCameraType();
            thirdPersonLocked = true;
        }
        if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void restoreCamera(Minecraft client) {
        if (!thirdPersonLocked) return;
        if (cameraBeforeTumble != null) client.options.setCameraType(cameraBeforeTumble);
        cameraBeforeTumble = null;
        thirdPersonLocked = false;
    }
}
