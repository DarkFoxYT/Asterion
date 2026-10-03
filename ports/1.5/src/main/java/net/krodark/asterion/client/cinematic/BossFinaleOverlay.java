package net.krodark.asterion.client.cinematic;

import net.krodark.asterion.client.audio.BiomeMusic;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.FinaleTimeline;
import net.krodark.asterion.client.event.DeadSunClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class BossFinaleOverlay {
    private static final int CREDIT_CARD_TICKS = 140;
    private static final int RETURN_FADE_TICKS = FinaleTimeline.RETURN_FADE_TICKS;
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
    private static final java.util.List<net.minecraft.client.resources.sounds.SoundInstance> cues = new java.util.ArrayList<>();

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
        if (active) return;
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
            // Dimension travel briefly removes the world/player; the finale must survive that gap.
            if(client.getConnection()==null)finish(client);
            return;
        }
        CinematicHud.maintain(client);
        client.smartCull = false;
        ticks++;
        if(ticks>=FinaleTimeline.DETONATION && !overworldReady && !(client.screen instanceof BossCreditsScreen))
            client.setScreen(new BossCreditsScreen());
        if (!overworldReady && client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            if (ticks == 18) cue(client, Asterion.ECLIPSE_EVENT_SOUND, .75F, .65F);
            if (ticks == FinaleTimeline.BEAM_START)
                cue(client, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, .65F, .6F);
            if (ticks == FinaleTimeline.IMPLOSION_START)
                cue(client, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, .6F, .55F);
            if (ticks == FinaleTimeline.DETONATION)
                cue(client, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 1F, .75F);
        }
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

        if (ticks > 305 && client.player != null && client.level != null
                && client.level.dimension().equals(Level.OVERWORLD)
                && client.level.hasChunk(client.player.getBlockX() >> 4, client.player.getBlockZ() >> 4)
                && client.level.isLoaded(client.player.blockPosition())) {
            overworldReady = true;
            if(fadeTicks< CREDITS_TICKS && !(client.screen instanceof BossCreditsScreen))client.setScreen(new BossCreditsScreen());
            if (++fadeTicks >= CREDITS_TICKS + RETURN_FADE_TICKS) finish(client);
        }
    }

    public static float sunDetonationStrength() {
        if (!active || overworldReady) return 0.0F;
        return FinaleTimeline.charge(renderTime());
    }

    public static boolean isActive() { return active; }
    public static boolean coversWorld() { return active && !overworldReady && ticks>=FinaleTimeline.DETONATION; }

    public static float visualTime(float partial) { return ticks + partial; }
    private static float renderTime() {
        return visualTime(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }
    public static float beamStrength(float partial) {
        return active && !overworldReady ? FinaleTimeline.beam(visualTime(partial)) : 0;
    }
    public static float sunScale() {
        return active && !overworldReady ? FinaleTimeline.sunScale(renderTime()) : 1;
    }

    public static float fov(float original, float partial) {
        if (!active || overworldReady || !net.krodark.asterion.AsterionConfig.INSTANCE.cinematicsEnabled)
            return original;
        float time = visualTime(partial);
        float shot = Mth.lerp(FinaleTimeline.ease((time - 70) / 40F), 57F, 74F);
        shot += FinaleTimeline.implosion(time) * 5;
        return Mth.lerp(FinaleTimeline.ease(time / 16F), original, shot);
    }

    public static CameraPose cameraPose(Vec3 basePosition, float partialTick) {
        if (!active || overworldReady || ticks >= FinaleTimeline.BLACKOUT_END
                || !net.krodark.asterion.AsterionConfig.INSTANCE.cinematicsEnabled) return null;
        float time = visualTime(partialTick);
        var config = net.krodark.asterion.AsterionConfig.INSTANCE;
        Vec3 sun = new Vec3(config.deadSunX, config.deadSunHeight, config.deadSunZ);
        Vec3 arena = net.krodark.asterion.worldgen.WorldGenerator.bossArenaCenter();
        // A lateral establishing move holds the sun in frame; pull back for its strike.
        float widen = FinaleTimeline.ease((time - 70) / 44F);
        float orbit = FinaleTimeline.ease(time / FinaleTimeline.DETONATION);
        double angle = -2.40 + orbit * .32;
        double radius = Mth.lerp(widen, Math.max(105, config.deadSunSize * 2.8), 195);
        double elevation = Mth.lerp(widen, sun.y - 18, sun.y - 62);
        Vec3 position = sun.add(Math.cos(angle) * radius, elevation - sun.y, Math.sin(angle) * radius);
        Vec3 focus = sun.lerp(arena, widen * .36);
        // Once the beam ends, the camera gives the contracting core its own shot.
        float core = FinaleTimeline.ease((time - FinaleTimeline.BEAM_END) / 24F);
        focus = focus.lerp(sun, core * .82);
        float shock = FinaleTimeline.shockwave(time);
        position = position.add(Math.cos(angle) * shock * 42, shock * 12, Math.sin(angle) * shock * 42);
        Vec3 delta = focus.subtract(position);
        float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float)-Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
        float shake = beamStrength(partialTick) * .025F + FinaleTimeline.implosion(time) * .018F;
        if (time >= FinaleTimeline.DETONATION) shake = (1 - shock) * .16F;
        position = position.add(Math.sin(time * 1.7) * shake, Math.cos(time * 2.1) * shake * .6, 0);
        return new CameraPose(position, yaw, pitch);
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if(Minecraft.getInstance().screen instanceof BossCreditsScreen)return;
        renderOverlay(graphics,tracker);
    }
    static void renderOverlay(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!active) return;
        float time = visualTime(tracker.getGameTimeDeltaPartialTick(false));
        if (!overworldReady && net.krodark.asterion.AsterionConfig.INSTANCE.cinematicsEnabled) {
            float bars = FinaleTimeline.ease(time / 16F);
            int bar = Math.round(graphics.guiHeight() * .095F * bars);
            graphics.fill(0, 0, graphics.guiWidth(), bar, 0xFF000000);
            graphics.fill(0, graphics.guiHeight() - bar, graphics.guiWidth(), graphics.guiHeight(), 0xFF000000);
        }
        if (!overworldReady && time < FinaleTimeline.DETONATION) return;
        if (overworldReady) {
            float fade = smoother(Mth.clamp((fadeTicks - CREDITS_TICKS)
                    / (float)RETURN_FADE_TICKS, 0.0F, 1.0F));
            int alpha = Math.round((1.0F - fade) * 255.0F);
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), alpha << 24);
            if (fadeTicks < CREDITS_TICKS) {
                float creditTime = fadeTicks + tracker.getGameTimeDeltaPartialTick(false);
                int card = Math.min(CREDITS.length - 1, fadeTicks / CREDIT_CARD_TICKS);
                float local = creditTime - card * CREDIT_CARD_TICKS;
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
        int shade=Math.round(FinaleTimeline.whiteout(time)*255);
        graphics.fill(0,0,graphics.guiWidth(),graphics.guiHeight(),0xFF000000|shade<<16|shade<<8|shade);
    }

    private static void cue(Minecraft client, net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        var sound = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, pitch, volume);
        cues.add(sound);
        client.getSoundManager().play(sound);
    }

    public static void finish(Minecraft client) {
        for (var sound : cues) client.getSoundManager().stop(sound);
        cues.clear();
        if (active) BiomeMusic.endCredits();
        if(client.screen instanceof BossCreditsScreen)client.setScreen(null);
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
