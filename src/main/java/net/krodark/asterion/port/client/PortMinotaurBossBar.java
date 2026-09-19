package net.krodark.asterion.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

/** 1.21.1 implementation of the authored Minotaur boss frame and animated fill. */
public final class PortMinotaurBossBar {
    private static final ResourceLocation FRAME = Asterion.id("textures/gui/bossbar/minotaur.png");
    private static final ResourceLocation FILL = Asterion.id("textures/gui/bossbar/minotaur_filling.png");
    private static final ResourceLocation EYES = Asterion.id("textures/gui/bossbar/minotaur_eyes.png");
    private static final ResourceLocation PILLARS = Asterion.id("textures/gui/bossbar/minotaur_pillars.png");
    private static MinotaurEntity boss;
    private static long nextScan;
    private static float previousProgress = 1;
    private static long impactAt = -100;

    private PortMinotaurBossBar() {}

    public static boolean isMinotaur(BossEvent event) {
        return "THE MINOTAUR".equals(event.getName().getString());
    }

    public static void render(GuiGraphics graphics, int vanillaX, int vanillaY, BossEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        long now = client.level.getGameTime();
        if (boss != null && (boss.isRemoved() || boss.level() != client.level)) { boss = null; nextScan = 0; }
        if (now >= nextScan) {
            boss = client.level.getEntitiesOfClass(MinotaurEntity.class,
                    client.player.getBoundingBox().inflate(160.0D),
                    candidate -> candidate.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS
                            && !candidate.isDefeatedBoss()).stream()
                    .min(java.util.Comparator.comparingDouble(candidate -> candidate.distanceToSqr(client.player)))
                    .orElse(null);
            nextScan = now + 10;
        }
        float progress = Mth.clamp(event.getProgress(), 0, 1);
        if (progress < previousProgress - .002F) impactAt = now;
        previousProgress = progress;
        float impact = Math.max(0, 1.0F - (now - impactAt) / 12.0F);
        boolean charged = boss != null && (boss.animationState() == MinotaurEntity.AnimationState.ROAR_START
                || boss.animationState() == MinotaurEntity.AnimationState.WARNING
                || boss.animationState() == MinotaurEntity.AnimationState.CHARGE_RUN);
        float force = impact * 1.5F + (charged ? .45F : 0);
        int x = vanillaX - 37 + Math.round(Mth.sin(now * 1.8F) * force);
        int y = vanillaY - 5 + Math.round(Mth.sin(now * 2.3F) * force * .55F);
        int filled = Math.round(164 * progress);

        RenderSystem.enableBlend();
        if (filled > 0) {
            int[] blood = {0xFF4A0710, 0xFF850D1B, 0xFFBE2434, 0xFFA01628, 0xFF730B1A, 0xFF3B050E};
            for (int offset = 3; offset >= 1; offset--) {
                int glow = (30 / offset) << 24 | 0xA40920;
                PortGuiMask.blit(graphics, FILL, x + 46, y + 21 - offset, 46, 53, filled, 6, 256, 112, glow);
                PortGuiMask.blit(graphics, FILL, x + 46, y + 21 + offset, 46, 53, filled, 6, 256, 112, glow);
            }
            for (int row = 0; row < blood.length; row++)
                PortGuiMask.blit(graphics, FILL, x + 46, y + 21 + row, 46, 53 + row, filled, 1, 256, 112, blood[row]);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        graphics.blit(FRAME, x, y, 0, 32, 256, 48, 256, 112);
        if (boss != null && boss.isPillarPhase()) {
            graphics.blit(PILLARS, x, y - 28, 0, 0, 256, 112, 256, 112);
            String count = Integer.toString(Math.max(0, boss.pillarsRemaining()));
            graphics.drawString(client.font, count, x + 128 - client.font.width(count) / 2,
                    y + 34, 0xFFFF263D, true);
        }
        PortGuiMask.blit(graphics, EYES, x + 121, y + 19, 121, 51, 14, 10, 256, 112,
                boss != null && PortMinotaurPose.attackCue(boss) ? 0xFF55FF66 : charged ? 0xFF55CEFF : 0xFFFF263D);
        RenderSystem.disableBlend();
    }

    public static void reset() { boss = null; nextScan = 0; previousProgress = 1; impactAt = -100; }
}
