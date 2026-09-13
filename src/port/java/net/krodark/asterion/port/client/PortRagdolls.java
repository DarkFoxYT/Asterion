package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.network.ragdoll.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Compact 1.21.1 rigid-body ragdoll. The downgrade originally replaced the
 * simulation with a fixed 86 degree render transform; this keeps ten physical
 * nodes, solves anatomical constraints, collides every node with world blocks,
 * and uses the solved pose for both local and relayed players.
 */
public final class PortRagdolls {
    private static final int TORSO = 1, HEAD = 0;
    private static final int RIGHT_ELBOW = 2, LEFT_ELBOW = 3;
    private static final int RIGHT_KNEE = 4, LEFT_KNEE = 5;
    private static final int RIGHT_HAND = 9, LEFT_HAND = 10;
    private static final int RIGHT_FOOT = 11, LEFT_FOOT = 12;
    private static final int CHEST = 20;
    private static final Map<Integer, Ragdoll> ACTIVE = new HashMap<>();
    private static CameraType previousCamera;
    private static int poseSequence;
    private static boolean recoveryWasDown;
    private static int recoveryPresses;
    private static int recoveryLastPressTick;
    private static int recoveryHoldTicks;
    private static int localElapsed;
    private static Vec3 smoothCamera;
    private static int corpseScanTicker;

    private PortRagdolls() {}

    public static void initialize() {
        HudRenderCallback.EVENT.register((graphics, delta) -> renderRecovery(graphics));
        ClientPlayNetworking.registerGlobalReceiver(RagdollImpulsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().player == null) return;
                    Ragdoll ragdoll = activate(context.client().player, 100);
                    ragdoll.impulse(payload.source(), payload.impulse(), Math.max(.2F, payload.force()));
                }));
        ClientPlayNetworking.registerGlobalReceiver(RagdollExplosionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level == null) return;
                    double radius = Math.max(1.0D, payload.radius());
                    for (LivingEntity entity : context.client().level.getEntitiesOfClass(LivingEntity.class,
                            new AABB(payload.center(), payload.center()).inflate(radius + 3.0D))) {
                        Ragdoll ragdoll = activate(entity, 90);
                        Vec3 away = ragdoll.center().subtract(payload.center());
                        double falloff = Mth.clamp(1.0D - away.length() / (radius + 3.0D), 0.0D, 1.0D);
                        ragdoll.impulse(payload.center(), away.normalize().scale(.35D + falloff * 1.8D),
                                (float)(.5D + falloff));
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(RagdollStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().level == null) return;
                    if (!payload.active()) { ACTIVE.remove(payload.entityId()); return; }
                    Entity entity = context.client().level.getEntity(payload.entityId());
                    if (entity instanceof LivingEntity living) activate(living, 260).remote =
                            context.client().player == null || living.getId() != context.client().player.getId();
                }));
        ClientPlayNetworking.registerGlobalReceiver(RagdollPosePayload.TYPE, (payload, context) ->
                context.client().execute(() -> receivePose(context.client(), payload)));
        ClientPlayNetworking.registerGlobalReceiver(RagdollAuthorityPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().player == null) return;
                    Ragdoll ragdoll = activate(context.client().player, 80);
                    Vec3 correction = payload.position().subtract(ragdoll.center());
                    ragdoll.translate(correction);
                    for (Body body : ragdoll.bodies.values()) body.velocity = payload.velocity();
                }));
    }

    public static void tick(Minecraft client) {
        if (client.level == null) {
            ACTIVE.clear();
            restoreCamera(client);
            resetRecovery();
            return;
        }
        // Dead bodies get the same solver even when no explicit network state was needed.
        int scanInterval = AsterionConfig.INSTANCE.ragdollPhysicsQuality <= 0 ? 10
                : AsterionConfig.INSTANCE.ragdollPhysicsQuality == 1 ? 7 : 5;
        if (client.player != null && ++corpseScanTicker % scanInterval == 0)
            for (LivingEntity entity : client.level.getEntitiesOfClass(
                    LivingEntity.class, client.player.getBoundingBox().inflate(64.0D),
                    entity -> !entity.isAlive() && entity.deathTime > 0 && !ACTIVE.containsKey(entity.getId())))
                activate(entity, 600);

        Iterator<Map.Entry<Integer, Ragdoll>> entries = ACTIVE.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Integer, Ragdoll> entry = entries.next();
            Entity raw = client.level.getEntity(entry.getKey());
            if (!(raw instanceof LivingEntity entity) || --entry.getValue().remaining <= 0) {
                entries.remove();
                continue;
            }
            Ragdoll ragdoll = entry.getValue();
            if (!ragdoll.remote || client.player != null && entity.getId() == client.player.getId())
                ragdoll.step(entity);
            else ragdoll.interpolateRemote();
        }

        if (client.player != null && ACTIVE.containsKey(client.player.getId())) {
            Ragdoll local = ACTIVE.get(client.player.getId());
            localElapsed++;
            if (previousCamera == null) previousCamera = client.options.getCameraType();
            if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK)
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            lockInput(client);
            Vec3 feet = safeFeet(client.player, local);
            Vec3 solvedVelocity = local.part(TORSO).velocity.lerp(local.part(CHEST).velocity, .5D);
            client.player.setPos(feet);
            client.player.setDeltaMovement(Vec3.ZERO);
            if (client.player.tickCount % 2 == 0 && ClientPlayNetworking.canSend(TumbleExitPayload.TYPE))
                ClientPlayNetworking.send(new TumbleExitPayload(feet.x, feet.y, feet.z, 0, 0, 0, false));
            handleRecovery(client, local, feet, solvedVelocity);
            if (client.player.tickCount % 3 == 0 && ClientPlayNetworking.canSend(RagdollPosePayload.TYPE))
                ClientPlayNetworking.send(local.payload(client.player.getId(), ++poseSequence));
        } else {
            restoreCamera(client);
            resetRecovery();
        }
    }

    public static boolean localMovementLocked() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && ACTIVE.containsKey(client.player.getId());
    }

    /** Smooth third-person camera translated from the vanilla eye anchor to the solved head. */
    public static Vec3 cameraPosition(Vec3 vanillaPosition, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;
        Ragdoll ragdoll = ACTIVE.get(client.player.getId());
        if (ragdoll == null) return null;
        Vec3 head = ragdoll.part(HEAD).render(partialTick);
        Vec3 eye = new Vec3(Mth.lerp(partialTick, client.player.xo, client.player.getX()),
                Mth.lerp(partialTick, client.player.yo, client.player.getY()) + client.player.getEyeHeight(),
                Mth.lerp(partialTick, client.player.zo, client.player.getZ()));
        Vec3 target = head.add(vanillaPosition.subtract(eye));
        if (smoothCamera == null || smoothCamera.distanceToSqr(target) > 144.0D) smoothCamera = target;
        else smoothCamera = smoothCamera.lerp(target, .30D);
        return smoothCamera;
    }

    public static boolean isRagdolled(LivingEntity entity) {
        return ACTIVE.containsKey(entity.getId());
    }

    public static RenderPose renderPose(LivingEntity entity, float partialTick) {
        Ragdoll ragdoll = ACTIVE.get(entity.getId());
        if (ragdoll == null) return null;
        Vec3 pelvis = ragdoll.part(TORSO).render(partialTick);
        Vec3 chest = ragdoll.part(CHEST).render(partialTick);
        Vec3 pivot = pelvis.lerp(chest, .52D);
        Vec3 up = safe(chest.subtract(pelvis), new Vec3(0, 1, 0));
        Vector3f rightHint = new Vector3f((float)Math.cos(Math.toRadians(entity.getYRot())), 0,
                (float)Math.sin(Math.toRadians(entity.getYRot())));
        Quaternionf orientation = new Quaternionf().rotationTo(new Vector3f(0, 1, 0),
                new Vector3f((float)up.x, (float)up.y, (float)up.z));
        // Retain a stable facing around the solved spine instead of allowing the skin to corkscrew.
        Vector3f solvedRight = orientation.transform(new Vector3f(1, 0, 0));
        if (solvedRight.dot(rightHint) < 0) orientation.rotateY((float)Math.PI);
        return new RenderPose(pivot, orientation);
    }

    public static void applyHumanoidPose(LivingEntity entity, HumanoidModel<?> model, float partialTick) {
        Ragdoll ragdoll = ACTIVE.get(entity.getId());
        RenderPose pose = renderPose(entity, partialTick);
        if (ragdoll == null || pose == null) return;
        Quaternionf inverse = new Quaternionf(pose.orientation()).conjugate();
        Vec3 pelvis = ragdoll.part(TORSO).render(partialTick);
        Vec3 chest = ragdoll.part(CHEST).render(partialTick);
        Vec3 right = vector(pose.orientation(), new Vec3(1, 0, 0));
        double shoulder = entity.getBbWidth() * .42D;
        Vec3 rightShoulder = chest.add(right.scale(shoulder));
        Vec3 leftShoulder = chest.subtract(right.scale(shoulder));
        double hip = entity.getBbWidth() * .20D;
        Vec3 rightHip = pelvis.add(right.scale(hip));
        Vec3 leftHip = pelvis.subtract(right.scale(hip));
        posePart(model.rightArm, inverse, ragdoll.part(RIGHT_ELBOW).render(partialTick).subtract(rightShoulder));
        posePart(model.leftArm, inverse, ragdoll.part(LEFT_ELBOW).render(partialTick).subtract(leftShoulder));
        posePart(model.rightLeg, inverse, ragdoll.part(RIGHT_KNEE).render(partialTick).subtract(rightHip));
        posePart(model.leftLeg, inverse, ragdoll.part(LEFT_KNEE).render(partialTick).subtract(leftHip));
        Vec3 neck = chest.add(safe(chest.subtract(pelvis), new Vec3(0, 1, 0)).scale(entity.getBbHeight() * .13D));
        posePart(model.head, inverse, ragdoll.part(HEAD).render(partialTick).subtract(neck));
        model.hat.copyFrom(model.head);
        model.body.xRot = model.body.yRot = model.body.zRot = 0;
    }

    private static void posePart(ModelPart part, Quaternionf inverse, Vec3 worldDirection) {
        Vec3 local = vector(inverse, safe(worldDirection, new Vec3(0, -1, 0)));
        // Humanoid limbs are authored down the model's positive Y axis.
        part.xRot = (float)Math.atan2(local.z, -local.y);
        part.zRot = (float)Math.atan2(local.x, -local.y);
        part.yRot = 0;
    }

    private static Vec3 vector(Quaternionf rotation, Vec3 input) {
        Vector3f result = rotation.transform(new Vector3f((float)input.x, (float)input.y, (float)input.z));
        return new Vec3(result.x, result.y, result.z);
    }

    private static void receivePose(Minecraft client, RagdollPosePayload payload) {
        if (client.level == null || client.player != null && payload.entityId() == client.player.getId()) return;
        Entity raw = client.level.getEntity(payload.entityId());
        if (!(raw instanceof LivingEntity entity)) return;
        Ragdoll ragdoll = activate(entity, 70);
        if (payload.sequence() <= ragdoll.remoteSequence) return;
        ragdoll.remoteSequence = payload.sequence();
        ragdoll.remote = true;
        for (RagdollPosePayload.Part part : payload.parts()) {
            Body body = ragdoll.bodies.get(part.region());
            if (body == null) continue;
            body.target = new Vec3(part.x(), part.y(), part.z());
            body.velocity = new Vec3(part.vx(), part.vy(), part.vz());
        }
    }

    private static Ragdoll activate(LivingEntity entity, int ticks) {
        Ragdoll result = ACTIVE.computeIfAbsent(entity.getId(), ignored -> new Ragdoll(entity));
        result.remaining = Math.max(result.remaining, ticks);
        return result;
    }

    private static void restoreCamera(Minecraft client) {
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
        smoothCamera = null;
    }

    private static Vec3 safeFeet(LivingEntity entity, Ragdoll ragdoll) {
        Vec3 desired = ragdoll.part(TORSO).position.add(0, -entity.getBbHeight() * .43D, 0);
        AABB original = entity.getBoundingBox();
        Vec3 current = entity.position();
        for (int ring = 0; ring <= 5; ring++) {
            double radius = ring == 0 ? 0 : .16D + ring * .12D;
            int count = ring == 0 ? 1 : 8;
            for (int i = 0; i < count; i++) {
                double angle = Mth.TWO_PI * i / count;
                for (int up = 0; up <= 4; up++) {
                    Vec3 candidate = desired.add(Math.cos(angle) * radius, up * .18D, Math.sin(angle) * radius);
                    if (entity.level().noCollision(entity, original.move(candidate.subtract(current)).deflate(.002D)))
                        return candidate;
                }
            }
        }
        return current;
    }

    private static void lockInput(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keyShift.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyAttack.setDown(false);
        client.options.keyUse.setDown(false);
        client.player.setSprinting(false);
    }

    private static void handleRecovery(Minecraft client, Ragdoll ragdoll, Vec3 feet, Vec3 velocity) {
        boolean down = client.screen == null && GLFW.glfwGetKey(client.getWindow().getWindow(), GLFW.GLFW_KEY_SPACE)
                == GLFW.GLFW_PRESS;
        boolean recover = false;
        if (AsterionConfig.INSTANCE.ragdollMashRecovery) {
            if (client.player.tickCount - recoveryLastPressTick > 24) recoveryPresses = 0;
            if (down && !recoveryWasDown) {
                recoveryLastPressTick = client.player.tickCount;
                recoveryPresses++;
            }
            recover = recoveryPresses >= 4 && localElapsed >= 8;
        } else {
            recoveryHoldTicks = down ? Math.min(32, recoveryHoldTicks + 1) : Math.max(0, recoveryHoldTicks - 2);
            recover = recoveryHoldTicks >= 32 && localElapsed >= 8;
        }
        recoveryWasDown = down;
        if (!recover) return;
        Vec3 exitVelocity = velocity.length() > 2.8D ? velocity.normalize().scale(2.8D) : velocity;
        ACTIVE.remove(client.player.getId());
        client.player.setPos(feet);
        client.player.setDeltaMovement(exitVelocity);
        if (ClientPlayNetworking.canSend(TumbleExitPayload.TYPE))
            ClientPlayNetworking.send(new TumbleExitPayload(feet.x, feet.y, feet.z,
                    exitVelocity.x, exitVelocity.y, exitVelocity.z, true));
        restoreCamera(client);
        resetRecovery();
    }

    private static void resetRecovery() {
        recoveryWasDown = false;
        recoveryPresses = 0;
        recoveryLastPressTick = 0;
        recoveryHoldTicks = 0;
        localElapsed = 0;
    }

    private static void renderRecovery(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || !localMovementLocked()) return;
        boolean mash = AsterionConfig.INSTANCE.ragdollMashRecovery;
        float progress = mash ? recoveryPresses / 4.0F : recoveryHoldTicks / 32.0F;
        Component text = Component.literal(mash ? "MASH SPACE  —  GET UP" : "HOLD SPACE  —  GET UP");
        int center = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - 54;
        int half = Math.max(72, client.font.width(text) / 2 + 13);
        graphics.fill(center - half, y - 8, center + half, y + 16, 0xA0080606);
        graphics.fill(center - half, y + 13, center + half, y + 16, 0xB0251715);
        graphics.fill(center - half, y + 13, center - half + Math.round(half * 2 * Mth.clamp(progress, 0, 1)),
                y + 16, 0xE09E3028);
        graphics.drawCenteredString(client.font, text, center, y, 0xFFF4E6D8);
    }

    private static Vec3 safe(Vec3 value, Vec3 fallback) {
        return value.lengthSqr() < 1.0E-8D ? fallback : value.normalize();
    }

    public record RenderPose(Vec3 pivot, Quaternionf orientation) {}

    private static final class Body {
        Vec3 previous, position, velocity, target;
        final double radius;
        Body(Vec3 position, Vec3 velocity, double radius) {
            this.previous = this.position = position;
            this.velocity = velocity;
            this.radius = radius;
        }
        Vec3 render(float partial) { return previous.lerp(position, Mth.clamp(partial, 0, 1)); }
    }

    private record Link(int a, int b, double length) {}

    private static final class Ragdoll {
        final Map<Integer, Body> bodies = new HashMap<>();
        final List<Link> links = new ArrayList<>();
        int remaining;
        int remoteSequence = -1;
        boolean remote;

        Ragdoll(LivingEntity entity) {
            Vec3 feet = entity.position();
            double height = Math.max(1.0D, entity.getBbHeight());
            double width = Math.max(.35D, entity.getBbWidth());
            Vec3 facing = safe(entity.getLookAngle().multiply(1, 0, 1), new Vec3(0, 0, 1));
            Vec3 right = new Vec3(-facing.z, 0, facing.x);
            Vec3 velocity = entity.getDeltaMovement();
            put(TORSO, feet.add(0, height * .43, 0), velocity, width * .28);
            put(CHEST, feet.add(0, height * .70, 0), velocity, width * .31);
            put(HEAD, feet.add(0, height * .91, 0), velocity, width * .24);
            put(RIGHT_ELBOW, feet.add(right.scale(width * .70)).add(0, height * .64, 0), velocity, width * .16);
            put(LEFT_ELBOW, feet.subtract(right.scale(width * .70)).add(0, height * .64, 0), velocity, width * .16);
            put(RIGHT_HAND, feet.add(right.scale(width * .82)).add(0, height * .42, 0), velocity, width * .14);
            put(LEFT_HAND, feet.subtract(right.scale(width * .82)).add(0, height * .42, 0), velocity, width * .14);
            put(RIGHT_KNEE, feet.add(right.scale(width * .22)).add(0, height * .25, 0), velocity, width * .18);
            put(LEFT_KNEE, feet.subtract(right.scale(width * .22)).add(0, height * .25, 0), velocity, width * .18);
            put(RIGHT_FOOT, feet.add(right.scale(width * .22)).add(facing.scale(.08)).add(0, height * .06, 0), velocity, width * .18);
            put(LEFT_FOOT, feet.subtract(right.scale(width * .22)).add(facing.scale(.08)).add(0, height * .06, 0), velocity, width * .18);
            link(TORSO, CHEST); link(CHEST, HEAD);
            link(CHEST, RIGHT_ELBOW); link(RIGHT_ELBOW, RIGHT_HAND);
            link(CHEST, LEFT_ELBOW); link(LEFT_ELBOW, LEFT_HAND);
            link(TORSO, RIGHT_KNEE); link(RIGHT_KNEE, RIGHT_FOOT);
            link(TORSO, LEFT_KNEE); link(LEFT_KNEE, LEFT_FOOT);
            link(RIGHT_ELBOW, LEFT_ELBOW); link(RIGHT_KNEE, LEFT_KNEE);
            // A small asymmetric launch prevents the assembly from remaining a rigid mannequin.
            double sign = (entity.getId() & 1) == 0 ? 1 : -1;
            for (Body body : bodies.values()) body.velocity = body.velocity.add(sign * .035, .025, -sign * .018);
        }

        Body part(int id) { return bodies.get(id); }
        Vec3 center() { return part(TORSO).position.lerp(part(CHEST).position, .5D); }
        void put(int id, Vec3 position, Vec3 velocity, double radius) {
            bodies.put(id, new Body(position, velocity, Mth.clamp(radius, .08D, .36D)));
        }
        void link(int a, int b) { links.add(new Link(a, b, bodies.get(a).position.distanceTo(bodies.get(b).position))); }

        void translate(Vec3 delta) {
            for (Body body : bodies.values()) {
                body.position = body.position.add(delta);
                body.previous = body.previous.add(delta);
                if (body.target != null) body.target = body.target.add(delta);
            }
        }

        void impulse(Vec3 source, Vec3 impulse, float force) {
            Vec3 bounded = impulse.length() > 2.8D ? impulse.normalize().scale(2.8D) : impulse;
            for (Body body : bodies.values()) {
                double distance = Math.sqrt(body.position.distanceToSqr(source));
                double weight = .38D + .62D / (1.0D + distance * .7D);
                body.velocity = body.velocity.add(bounded.scale(weight * Mth.clamp(force, .2F, 3.0F)));
            }
        }

        void step(LivingEntity entity) {
            for (Body body : bodies.values()) {
                body.previous = body.position;
                body.velocity = body.velocity.add(0, -.078D, 0).scale(.985D);
                move(entity, body, body.velocity);
            }
            for (int iteration = 0; iteration < 8; iteration++) {
                for (Link link : links) solve(link);
                for (Body body : bodies.values()) collide(entity, body);
            }
            for (Body body : bodies.values()) {
                Vec3 displacement = body.position.subtract(body.previous);
                body.velocity = body.velocity.scale(.28D).add(displacement.scale(.72D)).scale(.985D);
            }
        }

        void interpolateRemote() {
            for (Body body : bodies.values()) {
                body.previous = body.position;
                if (body.target != null) body.position = body.position.lerp(body.target, .42D);
                else body.position = body.position.add(body.velocity.scale(.5D));
            }
        }

        private void move(LivingEntity entity, Body body, Vec3 delta) {
            Vec3 start = body.position;
            Vec3 candidate = start.add(delta);
            if (clear(entity, body, candidate)) { body.position = candidate; return; }
            Vec3 x = start.add(delta.x, 0, 0);
            if (clear(entity, body, x)) start = x; else body.velocity = body.velocity.multiply(-.22D, 1, 1);
            Vec3 y = start.add(0, delta.y, 0);
            if (clear(entity, body, y)) start = y; else body.velocity = body.velocity.multiply(1, -.18D, 1);
            Vec3 z = start.add(0, 0, delta.z);
            if (clear(entity, body, z)) start = z; else body.velocity = body.velocity.multiply(1, 1, -.22D);
            body.position = start;
        }

        private void collide(LivingEntity entity, Body body) {
            if (clear(entity, body, body.position)) return;
            for (int i = 1; i <= 8; i++) {
                Vec3 raised = body.position.add(0, i * .035D, 0);
                if (clear(entity, body, raised)) { body.position = raised; body.velocity = body.velocity.multiply(.72D, -.12D, .72D); return; }
            }
            body.position = body.previous;
            body.velocity = body.velocity.scale(.25D);
        }

        private boolean clear(LivingEntity entity, Body body, Vec3 center) {
            double r = body.radius;
            return entity.level().noCollision(entity, new AABB(center.x-r, center.y-r, center.z-r,
                    center.x+r, center.y+r, center.z+r));
        }

        private void solve(Link link) {
            Body a = bodies.get(link.a), b = bodies.get(link.b);
            Vec3 delta = b.position.subtract(a.position);
            double distance = delta.length();
            if (distance < 1.0E-7D) return;
            Vec3 correction = delta.scale((distance - link.length) / distance * .5D);
            a.position = a.position.add(correction);
            b.position = b.position.subtract(correction);
        }

        RagdollPosePayload payload(int entityId, int sequence) {
            List<RagdollPosePayload.Part> parts = new ArrayList<>(bodies.size());
            for (Map.Entry<Integer, Body> entry : bodies.entrySet()) {
                Body body = entry.getValue();
                parts.add(new RagdollPosePayload.Part(entry.getKey(), (float)body.position.x,
                        (float)body.position.y, (float)body.position.z, 0, 0, 0, 1,
                        (float)body.velocity.x, (float)body.velocity.y, (float)body.velocity.z));
            }
            return new RagdollPosePayload(entityId, sequence, List.copyOf(parts));
        }
    }
}
