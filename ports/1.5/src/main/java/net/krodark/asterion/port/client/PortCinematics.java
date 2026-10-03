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
        initializeVisuals();
        initializeNetworking();
    }

    public static void initializeVisuals() {
        PortBossFinaleOverlay.register();
        PortCursedBrazierCinematic.register();
        PortDimensionTransitionOverlay.register();
        PortMazeObjectiveOverlay.register();
        HudRenderCallback.EVENT.register((graphics, delta) -> render(graphics));
    }

    public static void initializeNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(DimensionTransitionPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortDimensionTransitionOverlay.begin(payload.fadeInTicks(), payload.holdTicks(), payload.deathMessage())));
        ClientPlayNetworking.registerGlobalReceiver(BossEntrancePayload.TYPE, (payload, context) ->
                context.client().execute(() -> beginBoss(payload)));
        ClientPlayNetworking.registerGlobalReceiver(CursedBrazierAwakeningPayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortCursedBrazierCinematic.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RoofCollapsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> PortRoofCollapseCinematic.begin(payload)));
        ClientPlayNetworking.registerGlobalReceiver(BossFinalePayload.TYPE, (payload, context) ->
                context.client().execute(PortBossFinaleOverlay::begin));
    }

    public static void tick(Minecraft client) {
        PortDimensionTransitionOverlay.tick(client);
        PortMazeObjectiveOverlay.tick(client);
        PortDeadSunEntryCinematic.tick(client);
        PortCursedBrazierCinematic.tick(client);
        PortRoofCollapseCinematic.tick(client);
        PortBossFinaleOverlay.tick(client);
        if (scene == Scene.NONE) return;
        elapsed++;
        maintainPresentation(client);
        if (scene == Scene.BOSS_ENTRANCE) playBossSounds(client);
        if (client.player != null && client.screen == null) updateCamera(client);
        if (scene == Scene.TRANSITION && elapsed >= fadeIn + hold) {
            if (ClientPlayNetworking.canSend(TransitionReadyPayload.TYPE))
                ClientPlayNetworking.send(TransitionReadyPayload.INSTANCE);
            boolean arrival = deathMessage == 0;
            finish(client);
            if (arrival) PortDeadSunEntryCinematic.begin();
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
        int APPROACH_TICKS = MinotaurAnimationTiming.ENTRY_CAMERA_TICKS;
        int BREAK_TICK = MinotaurAnimationTiming.ENTRY_BREAK_TICK;
        int[] SOUND_BEATS = {APPROACH_TICKS + 14, APPROACH_TICKS + 44, APPROACH_TICKS + 78};
        int[] IMPACT_BEATS = {SOUND_BEATS[0], SOUND_BEATS[1], SOUND_BEATS[2], BREAK_TICK, MinotaurAnimationTiming.ENTRY_WALK_END_TICK};
        Vec3 inward = Vec3.atLowerCornerOf(door.getOpposite().getNormal());
        Vec3 doorway = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(door));
        Vec3 across = Vec3.atLowerCornerOf(door.getClockWise().getNormal());
        double width = cinematicBoss == null ? 4 : cinematicBoss.getBbWidth();
        Vec3 subject = net.krodark.asterion.entity.MinotaurEntranceMotion.point(time, width);
        Vec3 doorShot = doorway.add(inward.scale(13)).add(0, 2.1, 0);
        float approach = smootherStep(time / APPROACH_TICKS);
        Vec3 opening = (openingEye == null ? playerEye : openingEye).lerp(doorShot, approach);
        // Briefly follow the outgoing door fragments, then hand focus to the charging boss.
        float reveal = smootherStep((time - BREAK_TICK) / 28F);
        float settle = smootherStep((time - MinotaurAnimationTiming.ENTRY_WALK_END_TICK) / 22F);
        Vec3 tracking = subject.add(across.scale(3 - settle * 1.2))
                .add(inward.scale(11 - settle * 2.5)).add(0, 2.8 - settle * .5, 0);
        Vec3 camera = opening.lerp(tracking, reveal);
        float doorFlight = smootherStep((time - BREAK_TICK) / 12F);
        Vec3 doorFocus = doorway.add(inward.scale(doorFlight * 4)).add(across.scale(doorFlight * 1.5)).add(0, 3.2, 0);
        float handoff = smootherStep((time - BREAK_TICK - 5) / 23F);
        Vec3 focus = doorFocus.lerp(subject.add(0, 3.5, 0), handoff);
        float portrait = smootherStep((time - MinotaurAnimationTiming.ENTRY_WALK_END_TICK) / 24F);
        float portraitDrift = smootherStep((time - MinotaurAnimationTiming.ENTRY_WALK_END_TICK - 24) / 65F);
        Vec3 heroCamera = subject.add(across.scale(width * (1.5 - portraitDrift * .12)))
                .add(inward.scale(width * (2.25 - portraitDrift * .15))).add(0, 1.45, 0);
        camera = camera.lerp(heroCamera, portrait);
        // Aim left of the subject so the silhouette sits beside the title.
        focus = focus.lerp(subject.add(0, 3.5, 0).subtract(across.scale(width * .55)), portrait);
        float roarAge = time - MinotaurAnimationTiming.ENTRY_ROAR.roarSoundTick();
        float roar = smootherStep(roarAge / 5F)
                * (1 - smootherStep((time - (MinotaurAnimationTiming.ENTRY_END_TICK - 16)) / 16F));
        float closeMove = smootherStep((time - MinotaurAnimationTiming.ENTRY_WALK_END_TICK) / 65F);
        // A shallow dolly and lateral drift keep the close-up alive without circling the boss.
        camera = camera.add(across.scale(Math.sin(closeMove * Math.PI) * .75))
                .add(inward.scale(-closeMove * .65)).add(0, Math.sin(closeMove * Math.PI) * .18, 0);
        float impact = 0;
        for (int beat : IMPACT_BEATS) {
            float age = time - beat;
            if (age >= 0 && age < 16) impact += (beat == BREAK_TICK || beat == MinotaurAnimationTiming.ENTRY_WALK_END_TICK ? .13F
                    : beat == SOUND_BEATS[2] ? .065F : .03F) * (float)Math.pow(Math.sin(Math.PI * age / 16F), 2);
        }
        float plantAge = time - MinotaurAnimationTiming.ENTRY_WALK_END_TICK;
        if (plantAge >= 0 && plantAge < 24)
            impact += .16F * (float)Math.pow(1 - plantAge / 24, 2);
        float breachAge = time - BREAK_TICK;
        float breach = smootherStep(breachAge / 1.5F) * (1 - smootherStep((breachAge - 3) / 18F));
        impact += breach * .20F + roar * .045F;
        // Fixed-frequency, timeline-based vibration remains identical at every frame rate.
        camera = camera.add(across.scale((Math.sin(time * 1.9) + Math.sin(time * 3.1) * .28) * impact))
                .add(inward.scale(breach * .65 + Math.sin(roarAge * 1.4) * roar * .08))
                .add(0, Math.cos(time * 2.3) * impact * .65, 0);
        float returning = smootherStep((time - (duration - 30)) / 30F);
        camera = camera.lerp(playerEye, returning);
        var level = Minecraft.getInstance().level;
        if (level != null && cinematicBoss != null) {
            var hit = level.clip(new net.minecraft.world.level.ClipContext(focus, camera,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, cinematicBoss));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
                camera = hit.getLocation().add(focus.subtract(camera).normalize().scale(.3));
        }
        Vec3 delta = focus.subtract(camera);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z)) + (float)Math.sin(time * 1.7) * impact * 1.2F;
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())) + (float)Math.cos(time * 2.1) * impact;
        return new CameraPose(camera, Mth.rotLerp(returning, Mth.rotLerp(approach, returnYaw, yaw), returnYaw),
                Mth.lerp(returning, Mth.lerp(approach, returnPitch, pitch), returnPitch),
                (float)(Math.sin(reveal * Math.PI) * -3 + settle * 1.2 + Math.sin(time * 1.6) * impact * 2) * (1 - returning));
    }


    public static double bossVisualTime(MinotaurEntity boss, float partial) {
        if (scene == Scene.BOSS_ENTRANCE && boss.doorEntryTicks() > 0
                && (cinematicBoss == null || cinematicBoss == boss)) return elapsed + partial;
        return boss.doorEntryTicks() > 0 ? boss.doorEntryTicks() - 1 + partial : Double.NaN;
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
        // Cinematics are camera-only. Only dimension transitions draw a clean
        // black fade; no letterbox bars, titles or captions are overlaid.
        if (scene != Scene.TRANSITION) return;
        int fadeOut = Math.max(4, Math.min(12, hold / 3));
        float opacity;
        if (elapsed < fadeIn) opacity = smootherStep(elapsed / (float)fadeIn);
        else {
            int remaining = duration - elapsed;
            opacity = remaining > fadeOut ? 1.0F : smootherStep(remaining / (float)fadeOut);
        }
        int alpha = Math.round(Mth.clamp(opacity, 0, 1) * 255.0F);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
    }
}
