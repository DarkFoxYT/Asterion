package net.krodark.asterion.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

public final class MinotaurBossBar {
    private static final Identifier FRAME = Asterion.id("textures/gui/bossbar/minotaur.png");
    private static final Identifier FILL = Asterion.id("dynamic/minotaur_bar_fill");
    private static final Identifier EYES = Asterion.id("dynamic/minotaur_bar_eyes");
    private static final Identifier PILLARS = Asterion.id("textures/gui/bossbar/minotaur_pillars.png");
    private static boolean loaded;
    private static MinotaurEntity boss;
    private static long nextScan;
    private static double lastTime = -1, impactTime = -100;
    private static float previousProgress = 1, blue;
    private static java.util.UUID eventId;

    private MinotaurBossBar() { }

    public static void reset() {
        var textures = Minecraft.getInstance().getTextureManager();
        if (loaded) { textures.release(FILL); textures.release(EYES); }
        loaded = false;
        boss = null;
        eventId = null;
        nextScan = 0;
        lastTime = -1;
        blue = 0;
    }

    public static float scale(GuiGraphicsExtractor graphics) {
        return Math.min(1.1F, (graphics.guiWidth() - 24F) / 256F);
    }

    public static void render(GuiGraphicsExtractor g, int x, int y, BossEvent event) {
        var client = Minecraft.getInstance();
        if (!loaded) {
            mask("minotaur_filling", FILL, false);
            mask("minotaur_eyes", EYES, true);
            loaded = true;
        }
        if (client.level == null || client.player == null) return;
        long tick = client.level.getGameTime();
        if (boss == null || boss.isRemoved() || boss.level() != client.level || tick >= nextScan) {
            boss = client.level.getEntitiesOfClass(MinotaurEntity.class, client.player.getBoundingBox().inflate(160),
                    entity -> entity.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS && !entity.isDefeatedBoss())
                    .stream().min(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(client.player))).orElse(null);
            nextScan = tick + 10;
        }
        double time = tick + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (!event.getId().equals(eventId)) {
            eventId = event.getId(); previousProgress = event.getProgress(); impactTime = -100;
            lastTime = time; blue = 0;
        }
        float dt = (float)Math.clamp(time - lastTime, 0, 2);
        lastTime = time;
        float progress = Mth.clamp(event.getProgress(), 0, 1);
        if (progress < previousProgress - .002F) impactTime = time;
        previousProgress = progress;
        var animation = boss == null ? MinotaurEntity.AnimationState.IDLE : boss.animationState();
        boolean charged = animation == MinotaurEntity.AnimationState.ROAR_START
                || animation == MinotaurEntity.AnimationState.WARNING
                || animation == MinotaurEntity.AnimationState.CHARGE_RUN;
        blue += ((charged ? 1F : 0F) - blue) * (1F - (float)Math.exp(-dt / 7F));
        float impact = (float)Math.max(0, 1 - (time - impactTime) / 12);
        float force = impact * 1.5F + (charged ? .45F : 0);
        float shakeX = (float)Math.sin(time * 1.8) * force;
        float shakeY = (float)Math.sin(time * 2.3) * force * .55F;
        int eyeColor = ARGB.linearLerp(blue, 0xFFFF263D, 0xFF55CEFF);
        g.pose().pushMatrix();
        float scale = scale(g);
        g.pose().translate(x + 91 - 128 * scale + shakeX, y - 5 + shakeY);
        g.pose().scale(scale, scale);
         
        int filled = Math.round(164 * progress);
        for (int offset = 3; offset >= 1; offset--) {
            int glow = (30 / offset) << 24 | 0xA40920;
            fill(g, filled, 0, -offset, glow);
            fill(g, filled, 0, offset, glow);
        }
        int[] blood = {0xFF4A0710, 0xFF850D1B, 0xFFBE2434, 0xFFA01628, 0xFF730B1A, 0xFF3B050E};
        for (int row = 0; row < blood.length; row++) if (filled > 0)
            g.blit(RenderPipelines.GUI_TEXTURED, FILL, 46, 21 + row,
                    46, 53 + row, filled, 1, 256, 112, blood[row]);
        g.blit(RenderPipelines.GUI_TEXTURED, FRAME, 0, 0, 0, 32, 256, 48, 256, 112);
        g.blit(RenderPipelines.GUI_TEXTURED, PILLARS, 0, 0, 0, 0, 256, 112, 256, 112);
        String pillarCount = Integer.toString(Math.max(0, WorldGenerator.bossPillarsRemaining()));
        g.text(client.font, pillarCount, 128 - client.font.width(pillarCount) / 2, 48, 0xFFFF263D, true);
        for (int offset = 2; offset >= 1; offset--) {
            int glow = (45 / offset) << 24 | eyeColor & 0xFFFFFF;
            eyes(g, -offset, 0, glow); eyes(g, offset, 0, glow);
            eyes(g, 0, -offset, glow); eyes(g, 0, offset, glow);
        }
        eyes(g, 0, 0, eyeColor);
        g.pose().popMatrix();
    }

    private static void fill(GuiGraphicsExtractor g, int width, int dx, int dy, int color) {
        if (width > 0) g.blit(RenderPipelines.GUI_TEXTURED, FILL, 46 + dx, 21 + dy,
                46, 53, width, 6, 256, 112, color);
    }

    private static void eyes(GuiGraphicsExtractor g, int dx, int dy, int color) {
        g.blit(RenderPipelines.GUI_TEXTURED, EYES, 121 + dx, 19 + dy, 121, 51, 14, 10, 256, 112, color);
    }

    private static void mask(String name, Identifier id, boolean shading) {
        var client = Minecraft.getInstance();
        try (var stream = client.getResourceManager().open(Asterion.id("textures/gui/bossbar/" + name + ".png"))) {
            NativeImage pixels = NativeImage.read(stream);
            for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++) {
                int pixel = pixels.getPixel(x, y);
                int light = shading ? Math.max(110, (pixel >> 16) & 255) : 255;
                pixels.setPixel(x, y, pixel & 0xFF000000 | light << 16 | light << 8 | light);
            }
            client.getTextureManager().register(id, new DynamicTexture(() -> "Minotaur boss bar mask", pixels));
        } catch (java.io.IOException error) { Asterion.LOGGER.warn("Could not load Minotaur boss bar", error); }
    }
}
