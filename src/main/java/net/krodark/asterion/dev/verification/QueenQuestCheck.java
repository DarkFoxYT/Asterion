package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.QueenBeetleEntity;
import net.krodark.asterion.entity.QueenBeetleQuests;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;

/** Real interactions verify all requests consume exactly once and advance saved progress. */
public final class QueenQuestCheck {
    public static void run(MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().getFirst();
        var queen = Asterion.QUEEN_BEETLE.create(server.overworld(), EntitySpawnReason.COMMAND);
        check(queen != null, "Missing Queen");
        check(QueenBeetleQuests.ALL.size() == 21, "Expected original quest plus 20 new requests");
        check(QueenBeetleQuests.ALL.stream().map(QueenBeetleQuests.Quest::id).distinct().count() == 21, "Duplicate quest IDs");
        player.addTag("asterion.queen_beetle_quest.complete");
        check(QueenBeetleEntity.questIndex(player) == 1, "Legacy completion did not advance to new quests");
        queen.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
        check(player.entityTags().contains("asterion.queen_beetle_quest.active"), "Legacy player could not accept follow-up");
        for (String tag : java.util.List.copyOf(player.entityTags()))
            if (tag.startsWith("asterion.queen_beetle_quest.")) player.removeTag(tag);
        for (int index = 0; index < QueenBeetleQuests.ALL.size(); index++) {
            var quest = QueenBeetleQuests.get(index);
            player.getInventory().clearContent();
            queen.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            check(QueenBeetleEntity.questIndex(player) == index, "Accepting advanced quest prematurely");
            player.getInventory().add(new ItemStack(quest.item(), quest.count() - 1));
            queen.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            check(QueenBeetleEntity.questIndex(player) == index, "Incomplete request paid out");
            player.getInventory().add(new ItemStack(quest.item(), 3));
            QueenBeetleEntity.syncActiveQuest(player);
            queen.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
            check(QueenBeetleEntity.questIndex(player) == index + 1, "Request did not advance: " + quest.id());
            check(QueenBeetleEntity.countItems(player, quest.item().asItem(), 100) == 2, "Wrong tribute consumption: " + quest.id());
            check(QueenBeetleEntity.countItems(player, quest.reward().asItem(), 100) == quest.rewardCount(), "Wrong reward: " + quest.id());
            check(player.entityTags().contains("asterion.queen_beetle_quest.index." + (index + 1)), "Progress was not persisted");
        }
        var last = QueenBeetleQuests.get(20);
        queen.interact(player, InteractionHand.MAIN_HAND, net.minecraft.world.phys.Vec3.ZERO);
        check(QueenBeetleEntity.countItems(player, last.reward().asItem(), 100) == last.rewardCount(), "Final reward could be claimed twice");
        player.getInventory().clearContent();
        Asterion.LOGGER.info("PASS: all 21 Queen quests, exact tribute/rewards, legacy migration, persisted progression and one-time completion");
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
