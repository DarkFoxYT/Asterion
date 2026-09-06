package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.GameplayContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;

/** Close views of the boss models plus the independent Queen and Minotaur behavior checks. */
public final class BossMechanicsAuditGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        context.runOnClient(c -> {
            for (var quest : net.krodark.asterion.entity.QueenBeetleQuests.ALL)
                for (String suffix : new String[]{"title", "hint", "accepted", "rewarded"})
                    if (!net.minecraft.client.resources.language.I18n.exists(quest.key(suffix)))
                        throw new AssertionError("Missing Queen dialogue: " + quest.key(suffix));
        });
        try (var world = context.worldBuilder().create()) {
            context.waitTicks(60);
            var entity = new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.Entity>();
            world.getServer().runOnServer(server -> {
                var level = server.overworld();
                for (BlockPos pos : BlockPos.betweenClosed(-18, 120, -18, 18, 120, 18))
                    level.setBlock(pos, Asterion.ANCIENT_BRICKS.defaultBlockState(), 18);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.teleportTo(level, .5, 121, 5.5, java.util.Set.of(), 180, 12, true);
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 2400));
                var queen = Asterion.QUEEN_BEETLE.create(level, EntitySpawnReason.COMMAND);
                queen.setPos(.5, 121, .5); queen.setNoAi(true); level.addFreshEntity(queen); entity.set(queen);
                queen.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            });
            context.waitTicks(40); context.takeScreenshot("audit-queen-beetle-closeup");
            world.getServer().runOnServer(server -> {
                entity.get().discard();
                var dialogueViewer = server.getPlayerList().getPlayers().getFirst();
                for (String tag : java.util.List.copyOf(dialogueViewer.entityTags()))
                    if (tag.startsWith("asterion.queen_beetle_quest.")) dialogueViewer.removeTag(tag);
                QueenQuestCheck.run(server);
                var viewer = server.getPlayerList().getPlayers().getFirst();
                viewer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                viewer.setInvulnerable(true);
                viewer.teleportTo(server.overworld(), .5, 121, 6.5,
                        java.util.Set.of(), 180, 4, true);
                var boss = GameplayContent.CURSED_BRAZIER.create(server.overworld(), EntitySpawnReason.COMMAND);
                boss.setPos(.5, 121, .5); server.overworld().addFreshEntity(boss); entity.set(boss);
            });
            context.waitTicks(155);
            world.getServer().runOnServer(server -> {
                var boss = entity.get();
                server.getPlayerList().getPlayers().getFirst().teleportTo(server.overworld(),
                        boss.getX(), boss.getY() + 1, boss.getZ() + 12, java.util.Set.of(), 180, 0, true);
            });
            context.waitTicks(2);
            context.runOnClient(c -> c.gui.getChat().clearMessages(true));
            context.takeScreenshot("audit-cursed-brazier-closeup");
            world.getServer().runOnServer(server -> {
                entity.get().discard();
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.teleportTo(server.overworld(), .5, 121, 10.5, java.util.Set.of(), 180, -10, true);
                var boss = Asterion.MINOTAUR.create(server.overworld(), EntitySpawnReason.COMMAND);
                boss.setPos(.5, 121, .5); server.overworld().addFreshEntity(boss);
                boss.beginDebug(player); boss.setDebugRunning(false); entity.set(boss);
            });
            context.waitTicks(40); context.takeScreenshot("audit-minotaur-closeup");
            world.getServer().runOnServer(server -> {
                entity.get().discard();
                server.getPlayerList().getPlayers().getFirst().setInvulnerable(false);
                MinotaurPolishCheck.run(server.overworld(), server.getPlayerList().getPlayers().getFirst());
            });
        }
    }
}
