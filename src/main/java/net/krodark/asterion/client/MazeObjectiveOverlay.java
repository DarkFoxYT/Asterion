package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class MazeObjectiveOverlay {
    private static final Component INTRO = Component.translatable("objective.asterion.new");
    private static boolean armed;
    private static boolean sawTumble;
    private static boolean visible;
    private static int waitTicks;
    private static int visibleTicks;
    private static int completionTicks;
    private static Stage stage = Stage.ENTER_CATACOMBS;
    private static boolean sawBrazierKey;
    private static boolean sawMinotaurKey;
    private static boolean sawOmegaKey;

    private enum Stage {
        ENTER_CATACOMBS("enter_catacombs"),
        GET_BRAZIER_KEY("get_brazier_key"),
        DEFEAT_BRAZIER("defeat_brazier"),
        REACH_ARENA_DOORS("reach_arena_doors"),
        DEFEAT_DEAD_SUN("defeat_dead_sun"),
        OPEN_OMEGA_LOCK("open_omega_lock");

        private final Component objective;
        private final Component hint;
        Stage(String key) {
            objective = Component.translatable("objective.asterion." + key);
            hint = Component.translatable("objective.asterion." + key + "_hint");
        }
    }

    private MazeObjectiveOverlay() { }

    public static void register() {
        HudElementRegistry.addLast(Asterion.id("maze_objective"), MazeObjectiveOverlay::render);
    }

    public static void armAfterArrival() {
        armed = true;
        sawTumble = false;
        visible = false;
        waitTicks = 0;
        visibleTicks = 0;
        completionTicks = 0;
        resetProgress();
    }

    public static void armAfterBossWipe() {
        armed = false;
        sawTumble = false;
        visible = true;
        waitTicks = 0;
        visibleTicks = 0;
        completionTicks = 0;
        resetProgress();
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            armed = visible = false;
            return;
        }
        boolean tumbling = DismembermentEngine.INSTANCE.isPlayerTumbling(client.player.getId());
        if (armed) {
            waitTicks++;
            sawTumble |= tumbling;
            if (waitTicks >= 20 && !tumbling && (sawTumble || waitTicks >= 100)) {
                armed = false;
                visible = true;
            }
        }
        if (!visible) return;
        visibleTicks++;
        sawBrazierKey |= client.player.getInventory().contains(new net.minecraft.world.item.ItemStack(GameplayContent.CURSED_BRAZIER_KEY));
        sawMinotaurKey |= client.player.getInventory().contains(new net.minecraft.world.item.ItemStack(Asterion.MINOTAUR_KEY));
        sawOmegaKey |= client.player.getInventory().contains(new net.minecraft.world.item.ItemStack(Asterion.OMEGA_KEY));

        boolean complete = switch (stage) {
            case ENTER_CATACOMBS -> CatacombLayout.contains(client.player.blockPosition());
            case GET_BRAZIER_KEY -> sawBrazierKey;
            case DEFEAT_BRAZIER -> sawMinotaurKey || sawOmegaKey;
            case REACH_ARENA_DOORS -> client.player.position().distanceToSqr(
                    MinotaurArenaEntrances.door(MinotaurArenaEntrances.PLAYER_ENTRANCE).getCenter()) <= 24.0D * 24.0D;
            case DEFEAT_DEAD_SUN -> sawOmegaKey;
            case OPEN_OMEGA_LOCK -> sawOmegaKey && !client.player.getInventory().contains(
                    new net.minecraft.world.item.ItemStack(Asterion.OMEGA_KEY));
        };
        if (!complete) {
            completionTicks = 0;
        } else if (++completionTicks >= 18) {
            completionTicks = 0;
            if (stage == Stage.OPEN_OMEGA_LOCK) visible = false;
            else {
                stage = Stage.values()[stage.ordinal() + 1];
                visibleTicks = 0;
            }
        }
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!visible || CinematicHud.isHidden()) return;
        Minecraft client = Minecraft.getInstance();
        float renderTicks = visibleTicks + Mth.clamp(tracker.getGameTimeDeltaPartialTick(false), 0.0F, 1.0F);
        float appear = smootherstep(Mth.clamp(renderTicks / 14.0F, 0.0F, 1.0F));
        float completionFade = 1.0F - smootherstep(Mth.clamp(completionTicks / 18.0F, 0.0F, 1.0F));
        int alpha = Math.round(appear * completionFade * 245.0F);
        Component objective = stage.objective;
        Component hint = stage.hint;
        int contentWidth = Math.max(client.font.width(objective), client.font.width(hint));
        float settle = smootherstep(Mth.clamp((renderTicks - 34.0F) / 48.0F, 0.0F, 1.0F));
        int panelWidth = Math.max(184, contentWidth + 26);
        int panelHeight = 42;
        int centerX = Math.round(Mth.lerp(settle, graphics.guiWidth() * 0.5F, 12.0F + panelWidth * 0.5F));
        int panelTop = Math.round(Mth.lerp(settle, graphics.guiHeight() * 0.30F, 12.0F));
        int left = centerX - panelWidth / 2;
        graphics.fill(left, panelTop, left + panelWidth, panelTop + panelHeight,
                Math.round(appear * completionFade * 190.0F) << 24 | 0x090707);
        graphics.fill(left, panelTop, left + 3, panelTop + panelHeight,
                alpha << 24 | (stage.ordinal() >= Stage.DEFEAT_BRAZIER.ordinal() ? 0xD31C16 : 0x8F2A24));
        graphics.fill(left + 3, panelTop, left + panelWidth, panelTop + 1,
                Math.round(alpha * 0.35F) << 24 | 0x8B4A3C);
        int textCenter = left + panelWidth / 2 + 2;
        graphics.centeredText(client.font, INTRO, textCenter, panelTop + 6,
                Math.round(alpha * 0.72F) << 24 | 0xB66A5C);
        graphics.centeredText(client.font, objective, textCenter, panelTop + 17,
                alpha << 24 | 0xF2DED0);
        graphics.centeredText(client.font, hint, textCenter, panelTop + 29,
                Math.round(alpha * 0.62F) << 24 | 0xB8A49A);
    }

    private static float smoothstep(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static float smootherstep(float value) {
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }

    private static void resetProgress() {
        stage = Stage.ENTER_CATACOMBS;
        sawBrazierKey = false;
        sawMinotaurKey = false;
        sawOmegaKey = false;
    }
}
