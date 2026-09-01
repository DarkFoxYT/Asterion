package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.network.CursedBrazierAwakeningPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** A short room-local reveal that remains safe when the future crypt room moves. */
public final class CursedBrazierCinematic {
    private static boolean active;
    private static boolean showShot;
    private static int bossId;
    private static int ticks;
    private static int duration;
    private static CameraType previousCamera;
    private static boolean previousCull;
    private static float returnYaw;
    private static float returnPitch;
    private static Vec3 openingEye;

    private CursedBrazierCinematic() {
    }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("cursed_brazier_awakening"), (graphics, tracker) -> {
            if (!active || !showShot) return;
            float fade = Math.min(smooth(ticks / 10F), smooth((duration - ticks) / 15F));
            int bar = Math.round(graphics.guiHeight() * 0.075F * fade);
            graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - bar,
                    graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        });
    }

    public static void receive(CursedBrazierAwakeningPayload payload) {
        Minecraft client = Minecraft.getInstance();
        finish(client);
        if (client.player == null || client.level == null || payload.durationTicks() <= 0) return;

        bossId = payload.entityId();
        duration = Math.clamp(payload.durationTicks(), 30, 160);
        ticks = 0;
        active = true;
        showShot = AsterionConfig.INSTANCE.cinematicsEnabled;
        returnYaw = client.player.getYRot();
        returnPitch = client.player.getXRot();
        openingEye = client.player.getEyePosition();
        if (!showShot) return;

        previousCamera = client.options.getCameraType();
        previousCull = client.smartCull;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        client.smartCull = false;
        CinematicHud.begin(client);
        client.levelRenderer.getSectionOcclusionGraph().invalidate();
    }

    public static boolean isActive() {
        return active;
    }

    public static void tick(Minecraft client) {
        if (!active) return;
        if (client.player == null || client.level == null || !client.player.isAlive()
                || ++ticks >= duration + 5 || client.level.getEntity(bossId) == null) {
            finish(client);
            return;
        }

        client.player.setDeltaMovement(Vec3.ZERO);
        client.player.setYRot(returnYaw);
        client.player.setXRot(returnPitch);
        if (showShot) {
            CinematicHud.maintain(client);
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.smartCull = false;
        }
    }

    public static CameraPose cameraPose(Vec3 playerEye, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (!active || !showShot || client.level == null) return null;
        Entity boss = client.level.getEntity(bossId);
        if (boss == null) return null;

        float time = ticks + partialTick;
        float reveal = smooth(time / 24F);
        float returning = smooth((time - (duration - 20F)) / 20F);
        Vec3 focus = boss.getPosition(partialTick).add(0, boss.getBbHeight() * 0.55, 0);
        Vec3 fromBoss = (openingEye == null ? playerEye : openingEye).subtract(focus);
        Vec3 horizontal = new Vec3(fromBoss.x, 0, fromBoss.z);
        if (horizontal.lengthSqr() < 0.001) horizontal = new Vec3(0, 0, 1);
        horizontal = horizontal.normalize();

        Vec3 side = new Vec3(-horizontal.z, 0, horizontal.x);
        float orbit = smooth((time - 18F) / Math.max(1F, duration - 38F));
        Vec3 revealPosition = focus.add(horizontal.scale(7.2 - orbit * 1.1))
                .add(side.scale((orbit - 0.5F) * 3.0F))
                .add(0, 1.5 + Mth.sin(orbit * Mth.PI) * 0.6, 0);
        Vec3 camera = (openingEye == null ? playerEye : openingEye).lerp(revealPosition, reveal);
        camera = camera.lerp(playerEye, returning);

        float shakeEnvelope = smooth((time - 30F) / 16F)
                * (1F - smooth((time - 62F) / 16F)) * (1F - returning);
        camera = camera.add(
                Mth.sin(time * 2.3F) * 0.055F * shakeEnvelope,
                Mth.sin(time * 3.1F) * 0.035F * shakeEnvelope,
                Mth.cos(time * 2.7F) * 0.055F * shakeEnvelope);

        Vec3 look = focus.subtract(camera);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
        return new CameraPose(camera,
                Mth.rotLerp(returning, yaw, returnYaw),
                Mth.lerp(returning, pitch, returnPitch));
    }

    public static void finish(Minecraft client) {
        if (!active) return;
        active = false;
        if (showShot) {
            if (previousCamera != null) client.options.setCameraType(previousCamera);
            client.smartCull = previousCull;
            CinematicHud.end(client);
            if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        }
        showShot = false;
        previousCamera = null;
        openingEye = null;
    }

    private static float smooth(float value) {
        value = Math.clamp(value, 0F, 1F);
        return value * value * (3F - 2F * value);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) {
    }
}
