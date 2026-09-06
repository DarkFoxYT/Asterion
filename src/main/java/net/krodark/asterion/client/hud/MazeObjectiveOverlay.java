package net.krodark.asterion.client.hud;

import net.krodark.asterion.client.cinematic.CinematicHud;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

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
    private static boolean sawMinotaurMold;
    private static boolean sawMinotaurKey;
    private static boolean sawOmegaKey;
    private static boolean sawOre;
    private static boolean sawIngots;
    private static boolean wasInMaze;

    private enum Stage {
        ENTER_CATACOMBS("enter_catacombs"),
        GET_BRAZIER_KEY("get_brazier_key"),
        DEFEAT_BRAZIER("defeat_brazier"),
        REACH_FORGE("reach_forge"),
        GATHER_ORE("gather_ore"),
        PREPARE_INGOTS("prepare_ingots"),
        FORGE_MINOTAUR_KEY("forge_minotaur_key"),
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
        if (armed || visible) return;
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
        wasInMaze = false;
        waitTicks = 0;
        visibleTicks = 0;
        completionTicks = 0;
        resetProgress();
    }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            armed = visible = false;
            wasInMaze = false;
            return;
        }
        if (!wasInMaze) {
            wasInMaze = true;
             
             
            if (!armed) recoverProgress(client);
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
        if (!bossFightActive(client)) visibleTicks++;
        sawOre |= hasCaveOre(client);
        sawIngots |= hasIngots(client);
        sawBrazierKey |= client.player.getInventory().contains(new net.minecraft.world.item.ItemStack(GameplayContent.CURSED_BRAZIER_KEY));
        sawMinotaurMold |= has(client, Asterion.MINOTAUR_KEY_CAST);
        sawMinotaurKey |= client.player.getInventory().contains(new net.minecraft.world.item.ItemStack(Asterion.MINOTAUR_KEY))
                || !client.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        client.player.getBoundingBox().inflate(10.0D), entity -> entity.getItem().is(Asterion.MINOTAUR_KEY))
                        .isEmpty();
        sawOmegaKey |= client.player.getInventory().contains(new net.minecraft.world.item.ItemStack(Asterion.OMEGA_KEY));

        boolean complete = switch (stage) {
            case ENTER_CATACOMBS -> CatacombLayout.contains(client.player.blockPosition());
            case GET_BRAZIER_KEY -> sawBrazierKey;
            case DEFEAT_BRAZIER -> sawMinotaurMold || sawMinotaurKey || sawOmegaKey;
            case REACH_FORGE -> client.player.getY() <= net.krodark.asterion.worldgen.LabyrinthLevels.FORGE_ROOF_Y;
            case GATHER_ORE -> sawOre || sawIngots || sawMinotaurKey || sawOmegaKey;
            case PREPARE_INGOTS -> sawIngots || sawMinotaurKey || sawOmegaKey;
            case FORGE_MINOTAUR_KEY -> sawMinotaurKey || sawOmegaKey;
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

    public static boolean bossFightActive(Minecraft client) {
        return !((net.krodark.asterion.mixin.BossHealthOverlayAccessor)client.gui.getBossOverlay())
                .asterion$bossEvents().isEmpty();
    }

    private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker tracker) {
        if (!visible || CinematicHud.isHidden() || !AsterionConfig.INSTANCE.objectiveHudEnabled) return;
        if (bossFightActive(Minecraft.getInstance())) return;
        int displaySeconds = AsterionConfig.INSTANCE.objectiveHudSeconds;
        if (displaySeconds > 0 && visibleTicks > displaySeconds * 20) return;
        Minecraft client = Minecraft.getInstance();
        float renderTicks = visibleTicks + Mth.clamp(tracker.getGameTimeDeltaPartialTick(false), 0.0F, 1.0F);
        float appear = smootherstep(Mth.clamp(renderTicks / 14.0F, 0.0F, 1.0F));
        float completionFade = 1.0F - smootherstep(Mth.clamp(completionTicks / 18.0F, 0.0F, 1.0F));
        int alpha = Math.round(appear * completionFade * 245.0F);
        Component objective = stage.objective;
        Component hint = stage.hint;
        Vec3 waypoint = keyWaypoint(client);
        Component waypointText = waypoint == null ? Component.empty() : Component.translatable(
                "objective.asterion.key_destination",
                Math.max(1, Math.round((float)Math.sqrt(
                        Math.pow(waypoint.x - client.player.getX(), 2)
                                + Math.pow(waypoint.z - client.player.getZ(), 2)))));
        Component progress = Component.translatable("objective.asterion.progress",
                stage.ordinal() + 1, Stage.values().length);
        int panelWidth = Math.min(graphics.guiWidth() - 20, 236);
        var splitHint = client.font.split(hint, panelWidth - 18);
        var hintLines = splitHint.size() > 2 ? splitHint.subList(0, 2) : splitHint;
        int waypointY = 31 + hintLines.size() * 9;
        int panelHeight = waypointY + (waypoint == null ? 3 : 14);
        int left = Math.round(Mth.lerp(appear, -panelWidth - 4.0F, 12.0F));
        int panelTop = 12;
        graphics.fill(left, panelTop, left + panelWidth, panelTop + panelHeight,
                Math.round(appear * completionFade * 210.0F) << 24 | 0x090707);
        graphics.fill(left, panelTop, left + 3, panelTop + panelHeight,
                alpha << 24 | (stage.ordinal() >= Stage.DEFEAT_BRAZIER.ordinal() ? 0xC34635 : 0xA36745));
        graphics.fill(left + 3, panelTop, left + panelWidth, panelTop + 1,
                Math.round(alpha * 0.35F) << 24 | 0x8B4A3C);
        int textLeft = left + 9;
        graphics.text(client.font, INTRO, textLeft, panelTop + 5,
                Math.round(alpha * 0.74F) << 24 | 0xC18468, false);
        graphics.text(client.font, progress, left + panelWidth - 8 - client.font.width(progress), panelTop + 5,
                Math.round(alpha * 0.58F) << 24 | 0xA89185, false);
        graphics.text(client.font, client.font.plainSubstrByWidth(objective.getString(), panelWidth - 17), textLeft, panelTop + 17,
                alpha << 24 | 0xF2DED0, false);
        for (int line = 0; line < hintLines.size(); line++)
            graphics.text(client.font, hintLines.get(line), textLeft, panelTop + 29 + line * 9,
                    Math.round(alpha * 0.68F) << 24 | 0xB8A49A, false);
        if (waypoint != null) {
            double dx = waypoint.x - client.player.getX();
            double dz = waypoint.z - client.player.getZ();
            float targetYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
            float relative = Mth.wrapDegrees(targetYaw - client.player.getYRot());
            String arrow = Math.abs(relative) < 18.0F ? "◆" : relative < 0.0F ? "◀" : "▶";
            graphics.text(client.font, Component.literal(arrow), textLeft, panelTop + waypointY,
                    alpha << 24 | 0xE8B94A, false);
            graphics.text(client.font, waypointText, textLeft + 13, panelTop + waypointY,
                    Math.round(alpha * 0.84F) << 24 | 0xD8C7A2, false);
        }
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
        sawMinotaurMold = false;
        sawMinotaurKey = false;
        sawOmegaKey = false;
        sawOre = false;
        sawIngots = false;
    }

    private static void recoverProgress(Minecraft client) {
        waitTicks = completionTicks = 0;
        visibleTicks = 0;
        visible = true;
        sawOre = hasCaveOre(client);
        sawIngots = hasIngots(client);
        sawBrazierKey = has(client, GameplayContent.CURSED_BRAZIER_KEY);
        sawMinotaurMold = has(client, Asterion.MINOTAUR_KEY_CAST);
        sawMinotaurKey = has(client, Asterion.MINOTAUR_KEY);
        sawOmegaKey = has(client, Asterion.OMEGA_KEY);
        BlockPos pos = client.player.blockPosition();
        boolean inArena = Math.abs((long)pos.getX()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS
                && Math.abs((long)pos.getZ()) <= net.krodark.asterion.worldgen.AuthoredCatacombs.ARENA_RADIUS;
        if (sawOmegaKey) stage = Stage.OPEN_OMEGA_LOCK;
        else if (inArena) stage = Stage.DEFEAT_DEAD_SUN;
        else if (sawMinotaurKey) stage = Stage.REACH_ARENA_DOORS;
        else if (sawMinotaurMold
                && pos.getY() <= net.krodark.asterion.worldgen.LabyrinthLevels.FORGE_ROOF_Y)
            stage = sawIngots ? Stage.FORGE_MINOTAUR_KEY : sawOre ? Stage.PREPARE_INGOTS : Stage.GATHER_ORE;
        else if (sawMinotaurMold) stage = Stage.REACH_FORGE;
        else if (net.krodark.asterion.worldgen.AuthoredCatacombs.insideCursedBrazierRoom(pos))
            stage = Stage.DEFEAT_BRAZIER;
        else if (sawBrazierKey) stage = Stage.DEFEAT_BRAZIER;
        else if (CatacombLayout.contains(pos)) stage = Stage.GET_BRAZIER_KEY;
        else stage = Stage.ENTER_CATACOMBS;
    }

    private static boolean hasCaveOre(Minecraft client) {
        return has(client, Asterion.SHALE_TARNISHED_GOLD_ORE.asItem())
                || has(client, Asterion.SHADED_SHALE_TARNISHED_GOLD_ORE.asItem())
                || has(client, Asterion.SHALE_CELESTIAL_GOLD_ORE.asItem())
                || has(client, Asterion.SHADED_SHALE_CELESTIAL_GOLD_ORE.asItem());
    }

    private static boolean hasIngots(Minecraft client) {
        return has(client, Asterion.TARNISHED_GOLD_INGOT) || has(client, Asterion.CELESTIAL_GOLD_INGOT)
                || has(client, Asterion.CELESTIAL_BRONZE_INGOT)
                || has(client, Asterion.CELESTIAL_STEEL_INGOT) || has(client, Asterion.BONESTEEL_INGOT);
    }

    private static boolean has(Minecraft client, net.minecraft.world.item.Item item) {
        return client.player.getInventory().contains(new net.minecraft.world.item.ItemStack(item));
    }

     
    private static Vec3 keyWaypoint(Minecraft client) {
        if (stage == Stage.REACH_ARENA_DOORS && has(client, Asterion.MINOTAUR_KEY))
            return MinotaurArenaEntrances.door(MinotaurArenaEntrances.PLAYER_ENTRANCE).getCenter();
        if (stage == Stage.REACH_FORGE && has(client, Asterion.MINOTAUR_KEY_CAST))
            return Vec3.atCenterOf(new BlockPos(CatacombLayout.ROOT_CENTER,
                    net.krodark.asterion.worldgen.AuthoredCatacombs.CONNECTOR_Y,
                    CatacombLayout.ROOT_CENTER));
        if ((stage == Stage.GET_BRAZIER_KEY || stage == Stage.DEFEAT_BRAZIER)
                && has(client, GameplayContent.CURSED_BRAZIER_KEY)) {
            return net.krodark.asterion.worldgen.AuthoredCatacombs.BRAZIER_ROOM_ORIGINS.stream()
                    .map(origin -> Vec3.atCenterOf(new BlockPos(origin.getX(),
                            net.krodark.asterion.worldgen.AuthoredCatacombs.CONNECTOR_Y,
                            origin.getZ() + 25)))
                    .min(java.util.Comparator.comparingDouble(client.player.position()::distanceToSqr))
                    .orElse(null);
        }
        return null;
    }
}
