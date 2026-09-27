package net.krodark.asterion.client.cinematic;

import net.krodark.asterion.client.audio.BiomeMusic;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BossFinaleOverlay {
    private static final int CREDIT_CARD_TICKS = 140;
    private static final int RETURN_FADE_TICKS = 52;
    private static final String[][] CREDITS = {
            {"ASTERION", ""},
            {"CREATED BY", "Darkfox & Kronoz"},
            {"DEVELOPED BY", "Darkfox"},
            {"ART / GAME DESIGN", "Kronoz"},
            {"SOUND EFFECTS", "Flubburr"},
            {"THANKS FOR PLAYING!", ""}
    };
    private static final int CREDITS_TICKS = CREDIT_CARD_TICKS * CREDITS.length;
    private static boolean active;
    private static boolean overworldReady;
    private static int ticks;
    private static int fadeTicks;
    private static float returnYaw;
    private static float returnPitch;
    private static CameraType previousCamera;
    private static Boolean previousSmartCull;

    private BossFinaleOverlay() { }

    public static void register() {
        net.krodark.asterion.client.ReplayCompatibility.addHud(Asterion.id("boss_finale"), BossFinaleOverlay::render);
    }

    public static void begin() {
        Minecraft client = Minecraft.getInstance();
        if (net.krodark.asterion.client.AsterionClient.isPlayback(client)) return;
        if (client.player != null) {
            returnYaw = client.player.getYRot();
            returnPitch = client.player.getXRot();
        }
        previousCamera = client.options.getCameraType();
        previousSmartCull = client.smartCull;
        client.smartCull = false;
        client.options.setCameraType(CameraType.FIRST_PERSON);
        CinematicHud.begin(client);
        if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        BiomeMusic.beginCredits();
        active = true;
        overworldReady = false;
        ticks = 0;
        fadeTicks = 0;
    }

    public static void tick(Minecraft client) {
        if (net.krodark.asterion.client.AsterionClient.isPlayback(client)) {
            if (isActive()) finish(client);
            return;
        }
        if (!active) return;
        if (client.player == null || client.level == null) {
            finish(client);
            return;
        }
        CinematicHud.maintain(client);
        client.smartCull = false;
        ticks++;
        if (!overworldReady && client.player != null && client.level != null
                && client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            client.options.keyUp.setDown(false);
            client.options.keyDown.setDown(false);
            client.options.keyLeft.setDown(false);
            client.options.keyRight.setDown(false);
            client.options.keyJump.setDown(false);
            client.options.keyShift.setDown(false);
            client.player.setYRot(returnYaw);
            client.player.setXRot(returnPitch);
            client.player.yHeadRot = returnYaw;
            client.player.yBodyRot = returnYaw;
            if (client.options.getCameraType() != CameraType.FIRST_PERSON)
                client.options.setCameraType(CameraType.FIRST_PERSON);
            if (ticks >= 205) client.player.setDeltaMovement(Vec3.ZERO);
        }
        if (ticks >= 216) client.options.hideGui = false;
        if (ticks > 305 && client.player != null && client.level != null
                && client.level.dimension().equals(Level.OVERWORLD)
                && client.level.hasChunk(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4)
                && client.level.isLoaded(client.player.blockPosition())) {
            overworldReady = true;
            if (++fadeTicks >= CREDITS_TICKS + RETURN_FADE_TICKS) finish(client);
        }
    }

    public static float sunDetonationStrength() {
        if (!active || overworldReady) return 0.0F;
        return smoother(Mth.clamp((ticks - 18.0F) / 208.0F, 0.0F, 1.0F));
    }

    public static boolean isActive() { return active; }

    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        if (!active || overworldReady || ticks >= 265) return null;
        float time = ticks + partialTick;
        float progress = smoother(Mth.clamp(time / 255.0F, 0.0F, 1.0F));
        double angle = -2.48D + progress * .82D;
        double radius = Mth.lerp(progress, 104.0D, 150.0D)
                + Math.sin(progress * Math.PI) * 34.0D;
        Vec3 position = new Vec3(Math.cos(angle) * radius + 0.5D,
                205.0D + progress * 36.0D,
                Math.sin(angle) * radius + 0.5D);
        net.krodark.asterion.AsterionConfig config = net.krodark.asterion.AsterionConfig.INSTANCE;
        Vec3 sun = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ);
        Vec3 maze = new Vec3(0.5D, 54.0D, 0.5D);
        Vec3 focus = sun.lerp(maze, 0.18D + progress * 0.48D);
        Vec3 delta = focus.subtract(position);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float)(Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
        float chaos = sunDetonationStrength();
        double shakeX = (Math.sin(time * 1.73D) + Math.sin(time * 0.37D + 1.8D) * 0.45D)
                * chaos * 0.055D;
        double shakeY = (Math.sin(time * 2.11D + 0.6D) + Math.sin(time * 0.51D) * 0.36D)
                * chaos * 0.035D;
        position = position.add(shakeX, shakeY, -shakeX * 0.62D);
        yaw += (float)(shakeX * 0.42D);
        pitch += (float)(shakeY * 0.34D);
        return new CameraPose(position, yaw, pitch);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!active || ticks < 220) return;
        if (overworldReady) {
            float fade = smoother(Mth.clamp((fadeTicks - CREDITS_TICKS)
                    / (float)RETURN_FADE_TICKS, 0.0F, 1.0F));
            int alpha = Math.round((1.0F - fade) * 255.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
            if (fadeTicks < CREDITS_TICKS) {
                float time = fadeTicks + tracker.getGameTimeDeltaPartialTick(false);
                int card = Math.min(CREDITS.length - 1, fadeTicks / CREDIT_CARD_TICKS);
                float local = time - card * CREDIT_CARD_TICKS;
                float opacity = smoother(local / 24.0F)
                        * smoother((CREDIT_CARD_TICKS - local) / 28.0F);
                int textAlpha = Math.round(opacity * 255.0F);
                if (textAlpha > 3) {
                    int centerX = graphics.guiWidth() / 2;
                    int centerY = graphics.guiHeight() / 2;
                    boolean titleCard = CREDITS[card][1].isEmpty();
                    if (titleCard) {
                        var font = Minecraft.getInstance().font;
                        float scale = Math.min(3.0F,
                                (graphics.guiWidth() - 32.0F) / Math.max(1, font.width(CREDITS[card][0])));
                        graphics.pose().pushMatrix();
                        graphics.pose().translate(centerX, centerY);
                        graphics.pose().scale(scale, scale);
                        graphics.centeredText(font,
                                net.minecraft.network.chat.Component.literal(CREDITS[card][0]),
                                0, -font.lineHeight / 2, textAlpha << 24 | 0xD6B579);
                        graphics.pose().popMatrix();
                    } else {
                        graphics.centeredText(Minecraft.getInstance().font,
                                net.minecraft.network.chat.Component.literal(CREDITS[card][0]),
                                centerX, centerY - 20, textAlpha << 24 | 0xD6B579);
                        graphics.fill(centerX - 28, centerY - 3, centerX + 28, centerY - 2,
                                textAlpha << 24 | 0x756344);
                        graphics.centeredText(Minecraft.getInstance().font,
                                net.minecraft.network.chat.Component.literal(CREDITS[card][1]),
                                centerX, centerY + 12, textAlpha << 24 | 0xEEE7DC);
                    }
                }
            }
            return;
        }
        float blackout = smoother(Mth.clamp((ticks - 220.0F) / 45.0F, 0.0F, 1.0F));
        int red = Mth.floor(Mth.lerp(blackout, 52.0F, 2.0F));
        int green = Mth.floor(Mth.lerp(blackout, 1.0F, 0.0F));
        int alpha = Math.round(blackout * 255.0F);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                alpha << 24 | red << 16 | green << 8);
        float flash = 1.0F - Mth.clamp(Math.abs(ticks - 224.0F) / 7.0F, 0.0F, 1.0F);
        if (flash > 0.0F) {
            int flashAlpha = Math.round(smoother(flash) * 190.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    flashAlpha << 24 | 0xD43A24);
        }
    }

    public static void finish(Minecraft client) {
        if (active) BiomeMusic.endCredits();
        active = false;
        overworldReady = false;
        fadeTicks = 0;
        ticks = 0;
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
        if (previousSmartCull != null) client.smartCull = previousSmartCull;
        previousSmartCull = null;
        if (client.level != null) client.levelRenderer.getSectionOcclusionGraph().invalidate();
        CinematicHud.end(client);
        DeadSunClientEvents.clearTransientEffects();
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
