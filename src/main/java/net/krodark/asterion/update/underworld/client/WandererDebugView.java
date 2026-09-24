package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.ReplayCompatibility;
import net.krodark.asterion.entity.WandererEntity;
import net.krodark.asterion.network.WandererDebugPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** F3+J: inspect the dead person under the crosshair and its server navigation path. */
public final class WandererDebugView {
    private static boolean enabled, chordDown;
    private static WandererDebugPayload snapshot;
    private static long receivedAt;
    private WandererDebugView() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(WandererDebugView::tick);
        ClientPlayNetworking.registerGlobalReceiver(WandererDebugPayload.TYPE, (payload, context) ->
                context.client().execute(() -> { snapshot = payload; receivedAt = context.client().level == null
                        ? 0 : context.client().level.getGameTime(); }));
        ReplayCompatibility.addHud(Asterion.id("wanderer_debug"), WandererDebugView::hud);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (!enabled || snapshot == null || client.level == null
                    || client.level.getGameTime() - receivedAt > 20 || snapshot.nodes().isEmpty()) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            var out = context.bufferSource().getBuffer(RenderTypes.linesTranslucent());
            var pose = context.poseStack().last();
            List<BlockPos> nodes = snapshot.nodes();
            Vec3 previous = client.level.getEntity(snapshot.entityId()) instanceof WandererEntity dead
                    ? dead.position().add(0, .18, 0) : Vec3.atCenterOf(nodes.getFirst());
            for (int i = Math.min(snapshot.next(), nodes.size() - 1); i < nodes.size(); i++) {
                Vec3 next = Vec3.atBottomCenterOf(nodes.get(i)).add(0, .2, 0);
                line(out, pose, previous.subtract(camera), next.subtract(camera), i == snapshot.next());
                previous = next;
            }
        });
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null || !client.level.dimension().equals(Asterion.LIMBO_LEVEL)) {
            enabled = false; snapshot = null; chordDown = false; return;
        }
        long window = client.getWindow().handle();
        boolean chord = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS
                && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS;
        if (chord && !chordDown && client.screen == null) { enabled = !enabled; snapshot = null; }
        chordDown = chord;
        if (!enabled || client.level.getGameTime() % 5 != 0 || !ClientPlayNetworking.canSend(WandererDebugPayload.TYPE)) return;
        WandererEntity target = null;
        double best = .965;
        Vec3 eye = client.player.getEyePosition(), look = client.player.getLookAngle();
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof WandererEntity dead) || dead.distanceToSqr(client.player) > 32 * 32) continue;
            Vec3 delta = dead.getEyePosition().subtract(eye);
            double score = look.dot(delta.normalize()) - delta.length() * .0001;
            if (score > best) { best = score; target = dead; }
        }
        if (target == null) { snapshot = null; return; }
        ClientPlayNetworking.send(new WandererDebugPayload(target.getId(), -1, 0, List.of()));
    }

    private static void hud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled || client.level == null || client.screen != null) return;
        String line = "Dead [F3+J]: ";
        if (snapshot != null && client.level.getGameTime() - receivedAt <= 20) {
            int index = Math.clamp(snapshot.state(), 0, WandererEntity.State.values().length - 1);
            line += WandererEntity.State.values()[index] + " | path "
                    + snapshot.next() + "/" + snapshot.nodes().size();
        } else line += "look at a wanderer";
        graphics.text(client.font, Component.literal(line), 8, 8, 0xFFFFFFFF, true);
    }

    private static void line(com.mojang.blaze3d.vertex.VertexConsumer out,
                             com.mojang.blaze3d.vertex.PoseStack.Pose pose, Vec3 a, Vec3 b, boolean next) {
        int r = next ? 255 : 180, g = next ? 235 : 255;
        out.addVertex(pose, (float)a.x, (float)a.y, (float)a.z)
                .setColor(r, g, 255, 220).setNormal(pose, 0, 1, 0).setLineWidth(2.5F);
        out.addVertex(pose, (float)b.x, (float)b.y, (float)b.z)
                .setColor(r, g, 255, 220).setNormal(pose, 0, 1, 0).setLineWidth(2.5F);
    }
}
