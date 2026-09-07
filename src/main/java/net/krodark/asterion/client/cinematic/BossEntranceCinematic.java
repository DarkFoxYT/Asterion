package net.krodark.asterion.client.cinematic;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurAnimationTiming;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.block.MinotaurDoorMotion;
import net.krodark.asterion.network.BossEntrancePayload;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class BossEntranceCinematic {
    private static final int APPROACH_TICKS = MinotaurAnimationTiming.ENTRY_CAMERA_TICKS;
    private static final int BREAK_TICK = MinotaurAnimationTiming.ENTRY_BREAK_TICK;
    private static final int[] SOUND_BEATS = {APPROACH_TICKS + 14, APPROACH_TICKS + 44, APPROACH_TICKS + 78};
    private static final int[] IMPACT_BEATS = {SOUND_BEATS[0], SOUND_BEATS[1], SOUND_BEATS[2], BREAK_TICK, MinotaurAnimationTiming.ENTRY_LAND_TICK};
    private static boolean active, showShot, finished;
    private static int ticks, duration, lastSoundTick;
    private static Direction door;
    private static CameraType previousCamera;
    private static boolean previousCull;
    private static float returnYaw, returnPitch;
    private static Vec3 openingEye;
    private static net.krodark.asterion.entity.MinotaurEntity cinematicBoss;
    private BossEntranceCinematic() { }

    public static void register() {
        net.krodark.asterion.client.ReplayCompatibility.addHud(Asterion.id("boss_entrance"), (graphics, tracker) -> {
            if (!active || !showShot) return;
            float fade = Math.min(MinotaurDoorMotion.ease(ticks / 7F), MinotaurDoorMotion.ease((duration - ticks) / 14F));
            int height = Math.round(graphics.guiHeight() * .09F * fade);
            graphics.fill(0, 0, graphics.guiWidth(), height, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - height, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        });
    }

    public static void receive(BossEntrancePayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (net.krodark.asterion.client.AsterionClient.isPlayback(client)) return;
        finish(client);
        if (payload.duration() <= 0 || client.player == null || client.level == null) return;
        door = payload.bossDoor();
        if (!door.getAxis().isHorizontal()) return;
        duration = Math.clamp(payload.duration(), 1, net.krodark.asterion.worldgen.BossArenaEncounter.INTRO_TICKS);
        finished = false;
        ticks = Math.clamp(payload.elapsed(), 0, duration);
        lastSoundTick = ticks - 1;
        active = true;
        showShot = AsterionConfig.INSTANCE.cinematicsEnabled;
        returnYaw = client.player.getYRot(); returnPitch = client.player.getXRot();
        openingEye = client.player.getEyePosition();
        if (!showShot) return;
        previousCamera = client.options.getCameraType();
        previousCull = client.smartCull;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.smartCull = false;
        CinematicHud.begin(client);
        client.levelRenderer.getSectionOcclusionGraph().invalidate();
    }

    public static boolean isActive() { return active; }
    public static boolean hasFinished() { return finished; }
    public static void tick(Minecraft client) {
        if (net.krodark.asterion.client.AsterionClient.isPlayback(client)) {
            if (isActive()) finish(client);
            return;
        }
        if (!active) return;
        if (client.player == null || client.level == null || !client.player.isAlive()
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL) || ++ticks >= duration + 10) {
            finish(client); return;
        }
        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setYRot(returnYaw); client.player.setXRot(returnPitch);
        if (cinematicBoss == null || cinematicBoss.isRemoved())
            for (var entity : client.level.entitiesForRendering())
                if (entity instanceof net.krodark.asterion.entity.MinotaurEntity boss && boss.doorEntryTicks() > 0) {
                    cinematicBoss = boss; break;
                }
        playCinematicSounds(client);
        if (showShot) {
            CinematicHud.maintain(client);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.smartCull = false;
        }
    }

    private static void playCinematicSounds(Minecraft client) {
        Vec3 source = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(door)).add(0, 2.5, 0);
        for (int beat : SOUND_BEATS) if (lastSoundTick < beat && ticks >= beat)
            client.level.playLocalSound(source.x, source.y, source.z, Asterion.METAL_HIT,
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    beat == SOUND_BEATS[2] ? 3.4F : beat == SOUND_BEATS[1] ? 2.45F : 1.8F,
                    beat == SOUND_BEATS[2] ? 0.40F : beat == SOUND_BEATS[1] ? .49F : 0.58F, false);
        if (lastSoundTick < BREAK_TICK && ticks >= BREAK_TICK)
            client.level.playLocalSound(source.x, source.y, source.z, Asterion.MINOTAUR_DOOR_OPENCLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 2.6F, 0.72F, false);
        cue(client, MinotaurAnimationTiming.ENTRY_CROUCH_TICK, Asterion.MINOTAUR_LAND_LIGHT, .65F, .85F);
        cue(client, MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK, net.minecraft.sounds.SoundEvents.GOAT_LONG_JUMP, .75F, .65F);
        cue(client, MinotaurAnimationTiming.ENTRY_LAND_TICK, Asterion.MINOTAUR_LAND_SLAM, 1F, .9F);
        lastSoundTick = ticks;
    }

    private static final java.util.List<CueSound> cues = new java.util.ArrayList<>();
    private static void cue(Minecraft client, int beat, net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        if (lastSoundTick >= beat || ticks < beat) return;
        CueSound sound = new CueSound(event, volume, pitch);
        cues.add(sound);
        client.getSoundManager().play(sound);
    }
    private static final class CueSound extends net.minecraft.client.resources.sounds.AbstractSoundInstance {
        CueSound(net.minecraft.sounds.SoundEvent event, float loudness, float speed) {
            super(event, net.minecraft.sounds.SoundSource.HOSTILE, net.minecraft.util.RandomSource.create());
            volume = loudness; pitch = speed; relative = true; attenuation = Attenuation.NONE;
        }
    }

    public static CameraPose cameraPose(Vec3 playerEye, float partial) {
        if (!active || !showShot) return null;
        float time = ticks + partial;
        Vec3 inward = door.getOpposite().getUnitVec3();
        Vec3 doorway = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(door));
        Vec3 across = door.getClockWise().getUnitVec3();
        double width = cinematicBoss == null ? 4 : cinematicBoss.getBbWidth();
        Vec3 subject = net.krodark.asterion.entity.MinotaurEntranceMotion.point(time, width);
        Vec3 doorShot = doorway.add(inward.scale(13)).add(0, 2.1, 0);
        float approach = smootherStep(time / APPROACH_TICKS);
        Vec3 opening = (openingEye == null ? playerEye : openingEye).lerp(doorShot, approach);
        // Pull aside as the doors fly past, then track the leap from a clear three-quarter angle.
        Vec3 tracking = subject.add(across.scale(2.5)).add(inward.scale(16)).add(0, 4, 0);
        float flightBlend = smootherStep((time - BREAK_TICK) / 26F);
        Vec3 camera = opening.lerp(tracking, flightBlend);
        Vec3 focus = doorway.add(0, 3.2, 0).lerp(subject.add(0, 3.5, 0), flightBlend);
        float settle = smootherStep((time - MinotaurAnimationTiming.ENTRY_LAND_TICK) / 24F);
        Vec3 landing = net.krodark.asterion.entity.MinotaurEntranceMotion.point(MinotaurAnimationTiming.ENTRY_LAND_TICK, width);
        Vec3 frontal = landing.add(across.scale(2.5 - settle * .5))
                .add(inward.scale(16 - settle * 2)).add(0, 4 - settle * 1.2, 0);
        if (time >= MinotaurAnimationTiming.ENTRY_LAND_TICK) camera = frontal;
        float impact = 0;
        for (int beat : IMPACT_BEATS) {
            float age = time - beat;
            if (age >= 0 && age < 16) impact += (beat == BREAK_TICK || beat == MinotaurAnimationTiming.ENTRY_LAND_TICK ? .13F
                    : beat == SOUND_BEATS[2] ? .065F : .03F) * (float)Math.pow(Math.sin(Math.PI * age / 16F), 2);
        }
        float landingAge = time - MinotaurAnimationTiming.ENTRY_LAND_TICK;
        if (landingAge >= 0 && landingAge < 24)
            impact += .34F * (float)Math.pow(1 - landingAge / 24, 2);
        camera = camera.add(across.scale(Math.sin(time * .7) * impact)).add(0, Math.cos(time * .9) * impact * .6, 0);
        float returning = smootherStep((time - (duration - 30)) / 30F);
        camera = camera.lerp(playerEye, returning);
        Vec3 delta = focus.subtract(camera);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        return new CameraPose(camera, Mth.rotLerp(returning, Mth.rotLerp(approach, returnYaw, yaw), returnYaw),
                Mth.lerp(returning, Mth.lerp(approach, returnPitch, pitch), returnPitch),
                (float)(Math.sin(flightBlend * Math.PI) * -3 + settle * 1.5) * (1 - returning));
    }

    /** One client clock drives camera, entity position and animation despite packet spacing. */
    public static double visualTime(net.krodark.asterion.entity.MinotaurEntity boss, float partial) {
        return active && cinematicBoss == boss ? ticks + partial : Double.NaN;
    }

    public static float fov(float original, float partial) {
        if (!active || !showShot) return original;
        float time = ticks + partial;
        float flight = smootherStep((time - MinotaurAnimationTiming.ENTRY_TAKEOFF_TICK) / 10F)
                * (1 - smootherStep((time - MinotaurAnimationTiming.ENTRY_LAND_TICK) / 16F));
        float shotFov = 90 + flight * 6;
        float weight = smootherStep(time / 8F) * (1 - smootherStep((time - (duration - 30)) / 30F));
        return Mth.lerp(weight, original, shotFov);
    }

    private static float smootherStep(float value) {
        float t = Math.clamp(value, 0F, 1F);
        return t * t * t * (t * (t * 6F - 15F) + 10F);
    }

    public static void finish(Minecraft client) {
        if (!active) return;
        active = false;
        for (var sound : cues) client.getSoundManager().stop(sound);
        cues.clear();
        finished = true;
        if (showShot) {
            if (previousCamera != null) client.options.setCameraType(previousCamera);
            client.smartCull = previousCull;
            CinematicHud.end(client);
            if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        }
        showShot = false; previousCamera = null; openingEye = null; cinematicBoss = null;
    }

    public record CameraPose(Vec3 position, float yaw, float pitch, float roll) { }
}
