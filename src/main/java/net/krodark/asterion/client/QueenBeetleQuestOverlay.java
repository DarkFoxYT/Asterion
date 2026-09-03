package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.QueenBeetleEntity;
import net.krodark.asterion.network.QueenBeetleQuestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;

/** Interaction prompt, dialogue card, and persistent side-objective HUD for the Queen Beetle. */
public final class QueenBeetleQuestOverlay {
    private static int stage = -1;
    private static int dialogueTicks;
    private static int target = QueenBeetleEntity.PETAL_TARGET;
    private static int anger;

    private QueenBeetleQuestOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("queen_beetle_quest"), QueenBeetleQuestOverlay::render);
    }

    public static void receive(QueenBeetleQuestPayload payload) {
        stage = payload.stage() == QueenBeetleQuestPayload.RESTORE_ACTIVE
                ? QueenBeetleQuestPayload.PROGRESS : payload.stage();
        target = payload.target();
        anger = payload.anger();
        dialogueTicks = payload.stage() == QueenBeetleQuestPayload.RESTORE_ACTIVE ? 0
                : payload.stage() == QueenBeetleQuestPayload.REWARDED ? 140 : 100;
    }

    public static void tick(Minecraft client) {
        if (dialogueTicks > 0) dialogueTicks--;
        if (client.player == null || client.level == null) {
            stage = -1;
            dialogueTicks = 0;
        }
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (CinematicHud.isHidden()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        if (client.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof QueenBeetleEntity) {
            Component prompt = Component.translatable("quest.asterion.queen_beetle.interact");
            int width = client.font.width(prompt) + 18;
            int left = (graphics.guiWidth() - width) / 2;
            int top = graphics.guiHeight() / 2 + 18;
            graphics.fill(left, top, left + width, top + 18, 0xB0100A12);
            graphics.centeredText(client.font, prompt, graphics.guiWidth() / 2, top + 5, 0xFFF1D38A);
        }

        if (stage == QueenBeetleQuestPayload.ACCEPTED || stage == QueenBeetleQuestPayload.PROGRESS) {
            int progress = countPetals(client);
            renderObjective(graphics, client, progress);
        }
        if (dialogueTicks > 0) renderDialogue(graphics, client);
    }

    private static int countPetals(Minecraft client) {
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            var stack = client.player.getInventory().getItem(slot);
            if (stack.is(Asterion.TAINTED_PETALS.asItem())) count += stack.getCount();
        }
        return Math.min(count, target);
    }

    private static void renderObjective(GuiGraphicsExtractor graphics, Minecraft client, int progress) {
        Component title = Component.translatable("quest.asterion.queen_beetle.title");
        Component objective = Component.translatable("quest.asterion.queen_beetle.objective", progress, target);
        Component hint = progress >= target
                ? Component.translatable("quest.asterion.queen_beetle.return")
                : Component.translatable("quest.asterion.queen_beetle.hint");
        int width = Math.max(190, Math.max(client.font.width(objective), client.font.width(hint)) + 24);
        int left = graphics.guiWidth() - width - 12;
        int top = 12;
        graphics.fill(left, top, left + width, top + 49, 0xC00B100A);
        graphics.fill(left, top, left + 3, top + 49, 0xFFE0A936);
        graphics.text(client.font, title, left + 11, top + 7, 0xFFE8C96F, false);
        graphics.text(client.font, objective, left + 11, top + 20, 0xFFF5EBD2, false);
        int barWidth = width - 22;
        graphics.fill(left + 11, top + 34, left + 11 + barWidth, top + 39, 0xFF30271A);
        graphics.fill(left + 11, top + 34,
                left + 11 + Math.round(barWidth * Mth.clamp(progress / (float)target, 0.0F, 1.0F)),
                top + 39, 0xFFD99D2B);
        graphics.text(client.font, hint, left + 11, top + 40, 0xFFB7A989, false);
    }

    private static void renderDialogue(GuiGraphicsExtractor graphics, Minecraft client) {
        Component speaker = Component.translatable("entity.asterion.queen_beetle");
        Component line = Component.translatable(switch (stage) {
            case QueenBeetleQuestPayload.ACCEPTED -> "quest.asterion.queen_beetle.accepted";
            case QueenBeetleQuestPayload.PROGRESS -> "quest.asterion.queen_beetle.progress";
            case QueenBeetleQuestPayload.REWARDED -> "quest.asterion.queen_beetle.rewarded";
            default -> "quest.asterion.queen_beetle.complete";
        });
        int width = Math.min(graphics.guiWidth() - 32, Math.max(260,
                Math.min(420, client.font.width(line) + 32)));
        var lines = client.font.split(line, width - 24);
        int height = 37 + lines.size() * 11;
        int left = (graphics.guiWidth() - width) / 2;
        int top = graphics.guiHeight() - height - 24;
        graphics.fill(left, top, left + width, top + height, 0xE00B100A);
        graphics.fill(left, top, left + width, top + 2, anger == 0 ? 0xFFE0A936 : 0xFFFF563D);
        graphics.centeredText(client.font, speaker, graphics.guiWidth() / 2, top + 8, 0xFFE8C96F);
        for (int index = 0; index < lines.size(); index++)
            graphics.centeredText(client.font, lines.get(index), graphics.guiWidth() / 2,
                    top + 25 + index * 11, 0xFFF5EBD2);
        if (anger > 0) {
            Component warning = Component.translatable("quest.asterion.queen_beetle.anger." + anger);
            graphics.centeredText(client.font, warning, graphics.guiWidth() / 2,
                    top + height - 10, 0xFFFF7968);
        }
    }
}
