package net.krodark.asterion.client.cinematic;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
    private static final int[] IMPACT_BEATS = {SOUND_BEATS[0], SOUND_BEATS[1], SOUND_BEATS[2], BREAK_TICK};
    private static boolean active, showShot, finished;
    private static int ticks, duration, lastSoundTick;
    private static Direction door;
    private static CameraType previousCamera;
    private static boolean previousCull;
    private static float returnYaw, returnPitch;
    private static Vec3 openingEye;
    private BossEntranceCinematic() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("boss_entrance"), (graphics, tracker) -> {
            if (!active || !showShot) return;
            float fade = Math.min(MinotaurDoorMotion.ease(ticks / 7F), MinotaurDoorMotion.ease((duration - ticks) / 14F));
            int height = Math.round(graphics.guiHeight() * .09F * fade);
            graphics.fill(0, 0, graphics.guiWidth(), height, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - height, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        });
    }

    public static void receive(BossEntrancePayload payload) {
        Minecraft client = Minecraft.getInstance();
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
        if (!active) return;
        if (client.player == null || client.level == null || !client.player.isAlive()
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL) || ++ticks >= duration + 10) {
            finish(client); return;
        }
        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setYRot(returnYaw); client.player.setXRot(returnPitch);
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
        lastSoundTick = ticks;
    }

    public static CameraPose cameraPose(Vec3 playerEye, float partial) {
        if (!active || !showShot) return null;
        float time = ticks + partial;
        Vec3 inward = door.getOpposite().getUnitVec3();
        Vec3 doorway = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(door));
        float recoil = MinotaurDoorMotion.ease((time - (BREAK_TICK - 4)) / 32F);
        Vec3 doorShot = doorway.add(inward.scale(10.5 + recoil * 1.2)).add(0, 1.35 + recoil * .2, 0);
         
         
        float approach = smootherStep(time / APPROACH_TICKS);
        Vec3 camera = (openingEye == null ? playerEye : openingEye).lerp(doorShot, approach);
        float impact = 0;
        for (int beat : IMPACT_BEATS) {
            float age = time - beat;
            if (age >= 0 && age < 16) impact += (beat == BREAK_TICK ? .15F
                    : beat == SOUND_BEATS[2] ? .07F : .035F) * (float)Math.pow(Math.sin(Math.PI * age / 16F), 2);
        }
        camera = camera.add(Math.sin(time * .7) * impact, Math.cos(time * .9) * impact * .65, 0);
        Vec3 focus = doorway.add(inward.scale(1.2)).add(0, 3.15, 0);
        float returning = smootherStep((time - (duration - 76)) / 76F);
        camera = camera.lerp(playerEye, returning);
        Vec3 delta = focus.subtract(camera);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        return new CameraPose(camera, Mth.rotLerp(returning, Mth.rotLerp(approach, returnYaw, yaw), returnYaw),
                Mth.lerp(returning, Mth.lerp(approach, returnPitch, pitch), returnPitch));
    }

    private static float smootherStep(float value) {
        float t = Math.clamp(value, 0F, 1F);
        return t * t * t * (t * (t * 6F - 15F) + 10F);
    }

    public static void finish(Minecraft client) {
        if (!active) return;
        active = false;
        finished = true;
        if (showShot) {
            if (previousCamera != null) client.options.setCameraType(previousCamera);
            client.smartCull = previousCull;
            CinematicHud.end(client);
            if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        }
        showShot = false; previousCamera = null; openingEye = null;
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
