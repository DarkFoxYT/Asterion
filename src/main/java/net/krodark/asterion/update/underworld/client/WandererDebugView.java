package net.krodark.asterion.update.underworld.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.ReplayCompatibility;
import net.krodark.asterion.entity.WandererEntity;
import net.krodark.asterion.update.underworld.entity.LimboSpiderEntity;
import net.minecraft.world.entity.Entity;
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
    private static int selected = -1;
    private static net.minecraft.client.multiplayer.ClientLevel debugLevel;
    private WandererDebugView() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(WandererDebugView::tick);
        ClientPlayNetworking.registerGlobalReceiver(WandererDebugPayload.TYPE, (payload, context) ->
                context.client().execute(() -> { if(payload.entityId()!=selected)return; snapshot = payload; receivedAt = context.client().level == null
                        ? 0 : context.client().level.getGameTime(); }));
        ReplayCompatibility.addHud(Asterion.id("wanderer_debug"), WandererDebugView::hud);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (enabled && client.level != null && client.level.getEntity(selected) instanceof LimboSpiderEntity spider) {
                Vec3 camera = context.levelState().cameraRenderState.pos;
                var out = context.bufferSource().getBuffer(RenderTypes.linesTranslucent());
                var pose = context.poseStack().last();
                var box = spider.getBoundingBox();
                Vec3[] corners = new Vec3[8];
                for (int i=0;i<8;i++) corners[i] = new Vec3((i&1)==0?box.minX:box.maxX,
                        (i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ).subtract(camera);
                for (int i=0;i<8;i++) for (int bit=1;bit<=4;bit*=2)
                    if ((i&bit)==0) line(out,pose,corners[i],corners[i|bit],false);
                Vec3 center = spider.position().add(0, spider.getBbHeight()*.5, 0);
                line(out,pose,center.subtract(camera),center.add(spider.attachmentNormal()).subtract(camera),true);
                if (spider.hasSmoothSupport()) {
                    Vec3 normal = spider.attachmentNormal();
                    Vec3 u = normal.cross(Math.abs(normal.y)<.9 ? new Vec3(0,1,0) : new Vec3(1,0,0)).normalize();
                    Vec3 v = normal.cross(u).normalize();
                    Vec3 point = spider.supportPoint().subtract(camera);
                    for (int i=-2;i<=2;i++) {
                        greenLine(out,pose,point.add(u.scale(i*.5)).subtract(v),point.add(u.scale(i*.5)).add(v));
                        greenLine(out,pose,point.add(v.scale(i*.5)).subtract(u),point.add(v.scale(i*.5)).add(u));
                    }
                }
                line(out,pose,center.subtract(camera),center.add(spider.getDeltaMovement().scale(5)).subtract(camera),false);
                var debug = SpiderLegIK.debug(spider);
                if (debug != null && Math.abs(spider.tickCount-debug.age()) < 3) for (var leg : debug.legs()) {
                    for (int i=1;i<leg.joints().size();i++)
                        line(out,pose,leg.joints().get(i-1).subtract(camera),leg.joints().get(i).subtract(camera),leg.contact());
                    Vec3 target = leg.target().subtract(camera);
                    line(out,pose,target.add(-.08,0,0),target.add(.08,0,0),leg.contact());
                    line(out,pose,target.add(0,-.08,0),target.add(0,.08,0),leg.contact());
                    line(out,pose,leg.joints().getLast().subtract(camera),target,false);
                }
            }
            if (!enabled || snapshot == null || client.level == null
                    || client.level.getGameTime() - receivedAt > 20 || snapshot.nodes().isEmpty()) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            var out = context.bufferSource().getBuffer(RenderTypes.linesTranslucent());
            var pose = context.poseStack().last();
            List<BlockPos> nodes = snapshot.nodes();
            Vec3 previous = client.level.getEntity(snapshot.entityId()) instanceof net.minecraft.world.entity.PathfinderMob dead
                    ? dead.position().add(0, .18, 0) : Vec3.atCenterOf(nodes.getFirst());
            for (int i = Math.min(snapshot.next(), nodes.size() - 1); i < nodes.size(); i++) {
                Vec3 next = Vec3.atBottomCenterOf(nodes.get(i)).add(0, .2, 0);
                line(out, pose, previous.subtract(camera), next.subtract(camera), i == snapshot.next());
                previous = next;
            }
        });
    }

    private static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            enabled = false; snapshot = null; chordDown = false; selected = -1; return;
        }
        if(debugLevel!=client.level) { debugLevel=client.level; selected=-1; snapshot=null; }
        long window = client.getWindow().handle();
        boolean chord = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS
                && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_J) == GLFW.GLFW_PRESS;
        if (chord && !chordDown && client.screen == null) { enabled = !enabled; snapshot = null; selected = -1; }
        chordDown = chord;
        if (!enabled || client.level.getGameTime() % 5 != 0) return;
        Entity target = null;
        double best = .965;
        Vec3 eye = client.player.getEyePosition(), look = client.player.getLookAngle();
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof WandererEntity || entity instanceof LimboSpiderEntity) || entity.distanceToSqr(client.player) > 32 * 32) continue;
            Vec3 delta = entity.getEyePosition().subtract(eye);
            double score = look.dot(delta.normalize()) - delta.length() * .0001;
            if (score > best) { best = score; target = entity; }
        }
        // Hold the inspected entity while circling it to inspect individual legs.
        Entity previous = client.level.getEntity(selected);
        if (target == null && previous != null && previous.isAlive() && previous.distanceToSqr(client.player)<=32*32) return;
        if (target == null) { snapshot = null; selected = -1; return; }
        if (selected != target.getId()) snapshot = null;
        selected = target.getId();
        if (ClientPlayNetworking.canSend(WandererDebugPayload.TYPE))
            ClientPlayNetworking.send(new WandererDebugPayload(target.getId(), -1, 0, List.of()));
    }

    private static void hud(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled || client.level == null || client.screen != null) return;
        if (client.level.getEntity(selected) instanceof LimboSpiderEntity spider) {
            var debug = SpiderLegIK.debug(spider);
            int contacts = debug == null ? 0 : (int)debug.legs().stream().filter(SpiderLegIK.Leg::contact).count();
            double error = debug == null ? 0 : debug.legs().stream().mapToDouble(SpiderLegIK.Leg::error).max().orElse(0);
            graphics.text(client.font, Component.literal("Spider [F3+J]: " + spider.state()
                    + " | NoAI " + spider.isNoAi() + " | support " + spider.hasSurfaceSupport()
                    + " | surface " + spider.attachedSurface() + " | slope " + spider.hasSmoothSupport() + " | silk " + spider.onWeb()), 8, 8, 0xFFFFFFFF, true);
            graphics.text(client.font, Component.literal(String.format(java.util.Locale.ROOT,
                    "Speed %.2f blocks/s | feet %d/8 | max IK error %.3f", spider.getDeltaMovement().length()*20, contacts, error)),
                    8, 20, 0xFFFFFFFF, true);
            graphics.text(client.font, Component.literal("Leg chains + target crosses | yellow: contact/normal | blue: free/error/velocity"),
                    8, 32, 0xFFFFFFFF, true);
            return;
        }
        String line = "Entity [F3+J]: ";
        if (snapshot != null && client.level.getGameTime() - receivedAt <= 20) {
            int index = Math.clamp(snapshot.state(), 0, WandererEntity.State.values().length - 1);
            line += WandererEntity.State.values()[index] + " | path "
                    + snapshot.next() + "/" + snapshot.nodes().size();
        } else line += "look at a wanderer or spider";
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
    private static void greenLine(com.mojang.blaze3d.vertex.VertexConsumer out,
                                  com.mojang.blaze3d.vertex.PoseStack.Pose pose,Vec3 a,Vec3 b) {
        out.addVertex(pose,(float)a.x,(float)a.y,(float)a.z).setColor(70,255,60,230)
                .setNormal(pose,0,1,0).setLineWidth(3F);
        out.addVertex(pose,(float)b.x,(float)b.y,(float)b.z).setColor(70,255,60,230)
                .setNormal(pose,0,1,0).setLineWidth(3F);
    }
}
