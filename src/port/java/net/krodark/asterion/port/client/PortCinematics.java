package net.krodark.asterion.port.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.krodark.asterion.network.*;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.entity.MinotaurAnimationTiming;
import net.krodark.asterion.entity.MinotaurEntranceMotion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
    private static Vec3 openingEye;
    private static float returnYaw, returnPitch;
    private static CameraType previousCamera;
    private static MinotaurEntity cinematicBoss;
    private static boolean showShot;
    private static Boolean previousHideGui;
    private static Boolean previousCull;
    private static int lastSoundTick;
    private static final java.util.List<CueSound> cues = new java.util.ArrayList<>();

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
        maintainPresentation(client);
        if (scene == Scene.BOSS_ENTRANCE) playBossSounds(client);
        if (client.player != null && client.screen == null) updateCamera(client);
        if (scene == Scene.TRANSITION && elapsed >= fadeIn + hold) {
            if (ClientPlayNetworking.canSend(TransitionReadyPayload.TYPE))
                ClientPlayNetworking.send(TransitionReadyPayload.INSTANCE);
            finish(client);
        } else if (scene != Scene.TRANSITION && elapsed >= duration) finish(client);
    }

    private static void beginTransition(DimensionTransitionPayload payload) {
        begin(Scene.TRANSITION, Math.max(9, payload.fadeInTicks() + payload.holdTicks()));
        fadeIn = Math.max(1, payload.fadeInTicks());
        hold = Math.max(8, payload.holdTicks());
        duration = fadeIn + hold;
        deathMessage = payload.deathMessage();
        target = null;
        targetEntity = -1;
    }

    private static void beginBoss(BossEntrancePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (payload.duration() <= 0) { finish(client); return; }
        begin(Scene.BOSS_ENTRANCE, Math.max(1, payload.duration()));
        elapsed = Mth.clamp(payload.elapsed(), 0, duration - 1);
        lastSoundTick = elapsed - 1;
        door = payload.bossDoor();
        if (client.player != null) {
            openingEye = client.player.getEyePosition();
            returnYaw = client.player.getYRot();
            returnPitch = client.player.getXRot();
            showShot = AsterionConfig.INSTANCE.cinematicsEnabled && door.getAxis().isHorizontal();
            if (showShot) {
                previousCamera = client.options.getCameraType();
                client.options.setCameraType(CameraType.FIRST_PERSON);
            } else {
                restorePresentation(client);
            }
        }
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
        Minecraft client = Minecraft.getInstance();
        finish(client);
        scene = next;
        elapsed = 0;
        duration = ticks;
        target = null;
        targetEntity = -1;
        door = null;
        openingEye = null;
        cinematicBoss = null;
        showShot = false;
        if (AsterionConfig.INSTANCE.cinematicsEnabled || next == Scene.TRANSITION) {
            previousHideGui = client.options.hideGui;
            previousCull = client.smartCull;
            client.options.hideGui = true;
            client.smartCull = false;
        }
    }

    private static void maintainPresentation(Minecraft client) {
        if (previousHideGui != null) client.options.hideGui = true;
        if (previousCull != null) client.smartCull = false;
    }

    private static void playBossSounds(Minecraft client) {
        if (client.level == null || door == null) return;
        int approach = MinotaurAnimationTiming.ENTRY_CAMERA_TICKS;
        int[] beats = {approach + 14, approach + 44, approach + 78};
        Vec3 source = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(door)).add(0, 2.5D, 0);
        for (int index = 0; index < beats.length; index++) {
            int beat = beats[index];
            if (lastSoundTick < beat && elapsed >= beat) {
                float[] volumes = {1.8F, 2.45F, 3.4F};
                float[] pitches = {.58F, .49F, .40F};
                client.level.playLocalSound(source.x, source.y, source.z, net.krodark.asterion.Asterion.METAL_HIT,
                        SoundSource.BLOCKS, volumes[index], pitches[index], false);
            }
        }
        if (lastSoundTick < MinotaurAnimationTiming.ENTRY_BREAK_TICK
                && elapsed >= MinotaurAnimationTiming.ENTRY_BREAK_TICK)
            client.level.playLocalSound(source.x, source.y, source.z,
                    net.krodark.asterion.Asterion.MINOTAUR_DOOR_OPENCLOSE,
                    SoundSource.BLOCKS, 2.6F, .72F, false);
        if (lastSoundTick < MinotaurAnimationTiming.ENTRY_WALK_END_TICK
                && elapsed >= MinotaurAnimationTiming.ENTRY_WALK_END_TICK) {
            CueSound sound = new CueSound(net.krodark.asterion.Asterion.MINOTAUR_LAND_LIGHT, .85F, .8F);
            cues.add(sound);
            client.getSoundManager().play(sound);
        }
        lastSoundTick = elapsed;
    }

    private static void updateCamera(Minecraft client) {
        if (scene == Scene.BOSS_ENTRANCE) {
            client.player.setDeltaMovement(Vec3.ZERO);
            client.player.setYRot(returnYaw);
            client.player.setXRot(returnPitch);
            if (cinematicBoss == null || cinematicBoss.isRemoved())
                for (Entity candidate : client.level.entitiesForRendering())
                    if (candidate instanceof MinotaurEntity boss && boss.doorEntryTicks() > 0) {
                        cinematicBoss = boss;
                        break;
                    }
            if (showShot && client.options.getCameraType() != CameraType.FIRST_PERSON)
                client.options.setCameraType(CameraType.FIRST_PERSON);
            return;
        }
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

    /** Camera track ported from the original entrance cinematic. */
    public static CameraPose cameraPose(Vec3 playerEye, float partial) {
        if (scene != Scene.BOSS_ENTRANCE || !showShot || door == null) return null;
        float time = elapsed + partial;
        int approachTicks = MinotaurAnimationTiming.ENTRY_CAMERA_TICKS;
        int breakTick = MinotaurAnimationTiming.ENTRY_BREAK_TICK;
        Vec3 inward = Vec3.atLowerCornerOf(door.getOpposite().getNormal());
        Vec3 doorway = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(door));
        Vec3 across = Vec3.atLowerCornerOf(door.getClockWise().getNormal());
        double width = cinematicBoss == null ? 4.0D : cinematicBoss.getBbWidth();
        Vec3 subject = MinotaurEntranceMotion.point(time, width);
        Vec3 doorShot = doorway.add(inward.scale(13.0D)).add(0, 2.1D, 0);
        float approach = smootherStep(time / Math.max(1.0F, approachTicks));
        Vec3 camera = (openingEye == null ? playerEye : openingEye).lerp(doorShot, approach);
        float reveal = smootherStep((time - breakTick) / 28.0F);
        float settle = smootherStep((time - MinotaurAnimationTiming.ENTRY_WALK_END_TICK) / 22.0F);
        Vec3 tracking = subject.add(across.scale(3.0D - settle * 1.2D))
                .add(inward.scale(11.0D - settle * 2.5D)).add(0, 2.8D - settle * .5D, 0);
        camera = camera.lerp(tracking, reveal);
        float doorFlight = smootherStep((time - breakTick) / 12.0F);
        Vec3 doorFocus = doorway.add(inward.scale(doorFlight * 4.0D))
                .add(across.scale(doorFlight * 1.5D)).add(0, 3.2D, 0);
        Vec3 focus = doorFocus.lerp(subject.add(0, 3.5D, 0), smootherStep((time - breakTick - 5) / 23.0F));
        float closeMove = smootherStep((time - MinotaurAnimationTiming.ENTRY_WALK_END_TICK) / 65.0F);
        camera = camera.add(across.scale(Math.sin(closeMove * Math.PI) * .75D))
                .add(inward.scale(-closeMove * .65D)).add(0, Math.sin(closeMove * Math.PI) * .18D, 0);
        float impact = 0;
        int[] beats = {approachTicks + 14, approachTicks + 44, approachTicks + 78,
                breakTick, MinotaurAnimationTiming.ENTRY_WALK_END_TICK};
        for (int beat : beats) {
            float age = time - beat;
            if (age >= 0 && age < 16) impact += (beat == breakTick
                    || beat == MinotaurAnimationTiming.ENTRY_WALK_END_TICK ? .13F : .045F)
                    * (float)Math.pow(Math.sin(Math.PI * age / 16.0F), 2);
        }
        camera = camera.add(across.scale((Math.sin(time * 1.9D) + Math.sin(time * 3.1D) * .28D) * impact))
                .add(0, Math.cos(time * 2.3D) * impact * .65D, 0);
        float returning = smootherStep((time - (duration - 30)) / 30.0F);
        camera = camera.lerp(playerEye, returning);
        Vec3 delta = focus.subtract(camera);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        return new CameraPose(camera,
                Mth.rotLerp(returning, Mth.rotLerp(approach, returnYaw, yaw), returnYaw),
                Mth.lerp(returning, Mth.lerp(approach, returnPitch, pitch), returnPitch),
                (float)(Math.sin(reveal * Math.PI) * -3.0D + settle * 1.2D) * (1.0F - returning));
    }

    private static float smootherStep(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static void finish(Minecraft client) {
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        for (CueSound sound : cues) client.getSoundManager().stop(sound);
        cues.clear();
        restorePresentation(client);
        previousCamera = null;
        scene = Scene.NONE;
        showShot = false;
        openingEye = null;
        cinematicBoss = null;
    }

    private static void restorePresentation(Minecraft client) {
        if (previousHideGui != null) client.options.hideGui = previousHideGui;
        if (previousCull != null) client.smartCull = previousCull;
        previousHideGui = null;
        previousCull = null;
    }

    private static final class CueSound extends AbstractSoundInstance {
        private CueSound(SoundEvent event, float volume, float pitch) {
            super(event, SoundSource.HOSTILE, RandomSource.create());
            this.volume = volume;
            this.pitch = pitch;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }
    }

    public record CameraPose(Vec3 position, float yaw, float pitch, float roll) {}

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
