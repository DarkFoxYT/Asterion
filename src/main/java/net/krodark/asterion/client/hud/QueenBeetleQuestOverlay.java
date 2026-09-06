package net.krodark.asterion.client.hud;

import net.krodark.asterion.client.cinematic.CinematicHud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.entity.QueenBeetleEntity;
import net.krodark.asterion.entity.QueenBeetleQuests;
import net.minecraft.world.item.ItemStack;
import net.krodark.asterion.network.QueenBeetleQuestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;

 
public final class QueenBeetleQuestOverlay {
    private static final int CARD_WIDTH = 202;
    private static int stage = -1;
    private static int dialogueTicks;
    private static int dialogueDuration;
    private static int objectiveTicks;
    private static int target = QueenBeetleEntity.PETAL_TARGET;
    private static int anger;
    private static int questIndex;
    private static float displayedProgress;
    private static Component questTitle = Component.empty();
    private static Component questItem = Component.empty();
    private static Component questHint = Component.empty();
    private static Component questReward = Component.empty();

    private QueenBeetleQuestOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("queen_beetle_quest"), QueenBeetleQuestOverlay::render);
    }

    public static void receive(QueenBeetleQuestPayload payload) {
        boolean restoring = payload.stage() == QueenBeetleQuestPayload.RESTORE_ACTIVE;
        boolean wasActive = isActive() && questIndex == payload.questIndex();
        questIndex = Math.clamp(payload.questIndex(), 0, QueenBeetleQuests.ALL.size() - 1);
        var request = quest();
        questTitle = Component.translatable(request.key("title"));
        questItem = new ItemStack(request.item()).getHoverName();
        questHint = Component.translatable(request.key("hint"));
        questReward = Component.translatable("quest.asterion.queen_beetle.reward_item",
                request.rewardCount(), new ItemStack(request.reward()).getHoverName());
        stage = restoring ? QueenBeetleQuestPayload.PROGRESS : payload.stage();
        target = Math.max(1, payload.target());
        anger = Mth.clamp(payload.anger(), 0, 4);
        if (!wasActive || restoring) displayedProgress = payload.progress();
         
         
        if (!wasActive) objectiveTicks = restoring ? 12 : 0;
        dialogueDuration = payload.stage() == QueenBeetleQuestPayload.REWARDED ? 150 : 120;
        if (stage == QueenBeetleQuestPayload.ACCEPTED || stage == QueenBeetleQuestPayload.REWARDED) {
            String line = Component.translatable(request.key(
                    stage == QueenBeetleQuestPayload.ACCEPTED ? "accepted" : "rewarded"), target).getString();
            dialogueDuration = Mth.clamp(40 + line.codePointCount(0, line.length()), 120, 360);
        }
        dialogueTicks = restoring ? 0 : dialogueDuration;
    }

    public static void tick(Minecraft client) {
        if (dialogueTicks > 0) dialogueTicks--;
        if (client.player == null || client.level == null) {
            stage = -1;
            dialogueTicks = 0;
            objectiveTicks = 0;
            return;
        }
        if (isActive() && !MazeObjectiveOverlay.bossFightActive(client)) {
            objectiveTicks++;
            displayedProgress += (countItems(client) - displayedProgress) * 0.18F;
        }
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (CinematicHud.isHidden()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || MazeObjectiveOverlay.bossFightActive(client)) return;

        if (dialogueTicks == 0 && client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof QueenBeetleEntity
                && client.player.distanceToSqr(hit.getEntity()) <= 36.0D) {
            renderPrompt(graphics, client);
        }

        int displaySeconds = AsterionConfig.INSTANCE.objectiveHudSeconds;
        if (isActive() && AsterionConfig.INSTANCE.objectiveHudEnabled
                && (displaySeconds == 0 || objectiveTicks <= displaySeconds * 20))
            renderObjective(graphics, client);
        if (dialogueTicks > 0) renderDialogue(graphics, client);
    }

    private static boolean isActive() {
        return stage == QueenBeetleQuestPayload.ACCEPTED || stage == QueenBeetleQuestPayload.PROGRESS;
    }

    private static void renderPrompt(GuiGraphicsExtractor graphics, Minecraft client) {
        Component prompt = Component.translatable("quest.asterion.queen_beetle.interact");
        int width = client.font.width(prompt) + 20;
        int left = (graphics.guiWidth() - width) / 2;
        int top = graphics.guiHeight() / 2 + 18;
        graphics.fill(left, top, left + width, top + 18, 0xC70A0D09);
        graphics.fill(left, top, left + 2, top + 18, 0xFFD5A53E);
        graphics.centeredText(client.font, prompt, graphics.guiWidth() / 2 + 1, top + 5, 0xFFF2E5C4);
    }

    private static int countItems(Minecraft client) {
        int count = 0;
        for (int slot = 0; slot < client.player.getInventory().getContainerSize(); slot++) {
            var stack = client.player.getInventory().getItem(slot);
            if (stack.is(quest().item().asItem())) count += stack.getCount();
        }
        return Math.min(count, target);
    }

    private static void renderObjective(GuiGraphicsExtractor graphics, Minecraft client) {
        int actualProgress = countItems(client);
        int width = Math.min(CARD_WIDTH, graphics.guiWidth() - 24);
        Component hint = actualProgress >= target
                ? Component.translatable("quest.asterion.queen_beetle.return") : questHint;
        var hintLines = client.font.split(hint, width - 17);
        int rewardY = 37 + Math.max(1, hintLines.size()) * 11;
        int height = rewardY + 11;
        float appear = smootherstep(Mth.clamp(objectiveTicks / 12.0F, 0.0F, 1.0F));
        int left = graphics.guiWidth() - width - 12 + Math.round((1.0F - appear) * 18.0F);
        int top = 12;

        graphics.fill(left, top, left + width, top + height, alpha(0x090C08, Math.round(218 * appear)));
        graphics.fill(left, top, left + 3, top + height, alpha(0xD5A53E, Math.round(255 * appear)));
        graphics.fill(left + 3, top, left + width, top + 1, alpha(0x836B38, Math.round(150 * appear)));

        Component title = questTitle;
        Component temper = Component.translatable("quest.asterion.queen_beetle.temper." + anger);
        graphics.text(client.font, client.font.plainSubstrByWidth(title.getString(), width - 30 - client.font.width(temper)), left + 9, top + 5, alpha(0xDDBB6D, Math.round(255 * appear)), false);
        graphics.text(client.font, temper, left + width - 8 - client.font.width(temper), top + 5,
                alpha(anger == 0 ? 0x8EBB7C : 0xD7745E, Math.round(220 * appear)), false);

        Component objective = questItem;
        Component count = Component.literal(actualProgress + " / " + target);
        graphics.text(client.font, client.font.plainSubstrByWidth(objective.getString(), width - 28 - client.font.width(count)), left + 9, top + 18, alpha(0xF2E9D5, Math.round(255 * appear)), false);
        graphics.text(client.font, count, left + width - 8 - client.font.width(count), top + 18,
                alpha(0xF2E9D5, Math.round(255 * appear)), false);

        int barLeft = left + 9;
        int barRight = left + width - 8;
        int barTop = top + 30;
        graphics.fill(barLeft, barTop, barRight, barTop + 3, alpha(0x2A251A, Math.round(255 * appear)));
        int filled = Math.round((barRight - barLeft) * Mth.clamp(displayedProgress / target, 0.0F, 1.0F));
        graphics.fill(barLeft, barTop, barLeft + filled, barTop + 3,
                alpha(actualProgress >= target ? 0x82B76B : 0xC99432, Math.round(255 * appear)));

        for (int line = 0; line < hintLines.size(); line++)
            graphics.text(client.font, hintLines.get(line), left + 9, top + 37 + line * 11,
                    alpha(0xB8AD91, Math.round(230 * appear)), false);
        Component reward = questReward;
        graphics.text(client.font, client.font.plainSubstrByWidth(reward.getString(), width - 17), left + 9, top + rewardY, alpha(0x817A67, Math.round(205 * appear)), false);
    }

    private static void renderDialogue(GuiGraphicsExtractor graphics, Minecraft client) {
        float fadeIn = Mth.clamp((dialogueDuration - dialogueTicks) / 8.0F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(dialogueTicks / 12.0F, 0.0F, 1.0F);
        float opacity = smootherstep(Math.min(fadeIn, fadeOut));
        Component speaker = Component.translatable("entity.asterion.queen_beetle");
        Component line = switch (stage) {
            case QueenBeetleQuestPayload.ACCEPTED -> Component.translatable(
                    quest().key("accepted"), target);
            case QueenBeetleQuestPayload.PROGRESS -> Component.translatable(
                    "quest.asterion.queen_beetle.progress", countItems(client), target);
            case QueenBeetleQuestPayload.REWARDED -> Component.translatable(quest().key("rewarded"));
            case QueenBeetleQuestPayload.COOLDOWN -> Component.translatable(
                    "quest.asterion.queen_beetle.cooldown", formatTime(Math.round(displayedProgress)));
            default -> Component.translatable("quest.asterion.queen_beetle.complete");
        };
        int width = Math.min(graphics.guiWidth() - 32, 404);
        var lines = client.font.split(line, width - 28);
        boolean showTemperLine = questIndex == 0 && anger > 0 && stage != QueenBeetleQuestPayload.REWARDED;
        int height = 31 + lines.size() * 11 + (showTemperLine ? 13 : 0);
        int left = (graphics.guiWidth() - width) / 2;
        int top = graphics.guiHeight() - height - 48;

        graphics.fill(left, top, left + width, top + height, alpha(0x090C08, Math.round(224 * opacity)));
        graphics.fill(left, top, left + 3, top + height,
                alpha(anger == 0 ? 0xD5A53E : 0xC65743, Math.round(255 * opacity)));
        graphics.text(client.font, speaker, left + 13, top + 7, alpha(0xDDBB6D, Math.round(255 * opacity)), false);
        for (int index = 0; index < lines.size(); index++) {
            graphics.text(client.font, lines.get(index), left + 13, top + 20 + index * 11,
                    alpha(0xF2E9D5, Math.round(255 * opacity)), false);
        }
        if (showTemperLine) {
            Component warning = Component.translatable("quest.asterion.queen_beetle.anger." + anger);
            graphics.text(client.font, warning, left + 13, top + height - 12,
                    alpha(0xD7745E, Math.round(245 * opacity)), false);
        }
    }

    private static QueenBeetleQuests.Quest quest() {
        return QueenBeetleQuests.get(questIndex);
    }

    private static int alpha(int rgb, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb;
    }

    private static String formatTime(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static float smootherstep(float value) {
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }
}
