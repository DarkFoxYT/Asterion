package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 1.21.1 cinematic packet, camera and letterbox controller. */
public final class PortCinematics {
    private enum Scene { NONE, TRANSITION, BOSS_ENTRANCE, BRAZIER, ROOF_COLLAPSE, FINALE }
    private static Scene scene = Scene.NONE;
    private static int elapsed;
    private static int duration;
    private static int fadeIn;
    private static int hold;
    private static int deathMessage;
    private static int targetEntity = -1;
    private static Vec3 target;
    private static Direction door;

    private PortCinematics() {}

    public static void initialize() {
        HudRenderCallback.EVENT.register((graphics, delta) -> render(graphics));
        ClientPlayNetworking.registerGlobalReceiver(DimensionTransitionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> beginTransition(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BossEntrancePayload.TYPE, (payload, context) ->
                context.client().execute(() -> beginBoss(payload)));
        ClientPlayNetworking.registerGlobalReceiver(CursedBrazierAwakeningPayload.TYPE, (payload, context) ->
                context.client().execute(() -> beginBrazier(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RoofCollapsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> beginRoof(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BossFinalePayload.TYPE, (payload, context) ->
                context.client().execute(() -> begin(Scene.FINALE, 150)));
    }

    public static void tick(Minecraft client) {
        if (scene == Scene.NONE) return;
        elapsed++;
        if (client.player != null && client.screen == null) updateCamera(client);
        if (scene == Scene.TRANSITION && elapsed >= fadeIn + hold) {
            if (ClientPlayNetworking.canSend(TransitionReadyPayload.TYPE))
                ClientPlayNetworking.send(TransitionReadyPayload.INSTANCE);
            scene = Scene.NONE;
        } else if (scene != Scene.TRANSITION && elapsed >= duration) scene = Scene.NONE;
    }

    private static void beginTransition(DimensionTransitionPayload payload) {
        scene = Scene.TRANSITION;
        elapsed = 0;
        fadeIn = Math.max(1, payload.fadeInTicks());
        hold = Math.max(8, payload.holdTicks());
        duration = fadeIn + hold;
        deathMessage = payload.deathMessage();
        target = null;
        targetEntity = -1;
    }

    private static void beginBoss(BossEntrancePayload payload) {
        if (payload.duration() <= 0) { scene = Scene.NONE; return; }
        begin(Scene.BOSS_ENTRANCE, Math.max(1, payload.duration()));
        elapsed = Mth.clamp(payload.elapsed(), 0, duration - 1);
        door = payload.bossDoor();
    }

    private static void beginBrazier(CursedBrazierAwakeningPayload payload) {
        begin(Scene.BRAZIER, Math.max(20, payload.durationTicks()));
        targetEntity = payload.entityId();
    }

    private static void beginRoof(RoofCollapsePayload payload) {
        begin(Scene.ROOF_COLLAPSE, Math.max(20, payload.duration()));
        target = payload.center();
    }

    private static void begin(Scene next, int ticks) {
        scene = next;
        elapsed = 0;
        duration = ticks;
        target = null;
        targetEntity = -1;
        door = null;
    }

    private static void updateCamera(Minecraft client) {
        Vec3 look = target;
        if (targetEntity >= 0 && client.level != null && client.level.getEntity(targetEntity) != null)
            look = client.level.getEntity(targetEntity).getBoundingBox().getCenter();
        if (scene == Scene.BOSS_ENTRANCE && door != null)
            look = client.player.position().add(Vec3.atLowerCornerOf(door.getNormal()).scale(12)).add(0, 2, 0);
        if (look == null) return;
        Vec3 eye = client.player.getEyePosition();
        Vec3 delta = look.subtract(eye);
        float wantedYaw = (float)(Mth.atan2(delta.z, delta.x) * 180.0D / Math.PI) - 90.0F;
        float wantedPitch = (float)-(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180.0D / Math.PI);
        client.player.setYRot(Mth.rotLerp(.13F, client.player.getYRot(), wantedYaw));
        client.player.setXRot(Mth.lerp(.13F, client.player.getXRot(), wantedPitch));
    }

    private static void render(GuiGraphics graphics) {
        if (scene == Scene.NONE) return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float progress = duration <= 0 ? 1 : Mth.clamp(elapsed / (float)duration, 0, 1);
        int bar = Math.max(18, height / 9);
        int alpha = Math.round(230 * Mth.clamp(Math.min(progress * 6, (1 - progress) * 8), 0, 1));
        graphics.fill(0, 0, width, bar, alpha << 24);
        graphics.fill(0, height - bar, width, height, alpha << 24);

        Minecraft client = Minecraft.getInstance();
        Component caption = switch (scene) {
            case TRANSITION -> deathMessage == 0 ? Component.translatable("transition.asterion.descending")
                    : Component.translatable(deathMessage == 1 ? "death.asterion.you_died" : "death.asterion.teammate_died");
            case BOSS_ENTRANCE -> Component.literal("THE LABYRINTH STIRS");
            case BRAZIER -> Component.literal("AN ANCIENT FLAME AWAKENS");
            case ROOF_COLLAPSE -> Component.literal("THE SANCTUARY COLLAPSES");
            case FINALE -> Component.literal("THE DEAD SUN FADES");
            default -> Component.empty();
        };
        int textAlpha = Math.max(0x30, alpha) << 24;
        graphics.drawCenteredString(client.font, caption, width / 2, height - bar + 6,
                textAlpha | (scene == Scene.TRANSITION ? 0xD63A32 : 0xE7C88B));

        if (scene == Scene.TRANSITION) {
            float fade = elapsed < fadeIn ? elapsed / (float)fadeIn
                    : 1.0F - Mth.clamp((elapsed - fadeIn) / (float)Math.max(1, hold), 0, 1);
            int darkness = Math.round(Mth.clamp(fade, 0, 1) * 245);
            graphics.fill(0, 0, width, height, darkness << 24);
            graphics.drawCenteredString(client.font, caption, width / 2, height / 2,
                    Math.max(80, darkness) << 24 | 0xD63A32);
        }
    }
}
