package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.RespawnObelisks;
import net.krodark.asterion.block.SanctuaryBlock;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

final class RefugeAndHarvestCheck {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        var originalHand = player.getMainHandItem().copy();
        var originalMode = player.gameMode.getGameModeForPlayer();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var corpse = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        var initial = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        corpse.saveWithoutId(initial);
        var tag = initial.buildResult();
        tag.putInt("asterion_behavior_phase", MinotaurEntity.BehaviorPhase.BOSS.ordinal());
        tag.putInt("asterion_boss_stage", 3);
        corpse.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        corpse.setPos(80, 180, 0);
        var area = new AABB(75, 175, -5, 85, 190, 5);
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            corpse.mobInteract(player, InteractionHand.MAIN_HAND);
            check(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "Empty hand harvested the corpse");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
            corpse.mobInteract(player, InteractionHand.MAIN_HAND);
            var drops = level.getEntitiesOfClass(ItemEntity.class, area);
            check(drops.size() == 3 && drops.stream().anyMatch(item -> item.getItem().is(Items.LEATHER)), "Missing hide rewards");
            check(player.getMainHandItem().getDamageValue() == 1, "Harvest did not wear the tool");
            corpse.mobInteract(player, InteractionHand.MAIN_HAND);
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            corpse.saveWithoutId(saved);
            corpse.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            corpse.mobInteract(player, InteractionHand.MAIN_HAND);
            check(level.getEntitiesOfClass(ItemEntity.class, area).size() == 3, "Repeated or reloaded harvest duplicated rewards");
            drops.forEach(ItemEntity::discard);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
            player.setGameMode(originalMode);
        }

        int index = 0;
        for (Rotation rotation : Rotation.values()) {
            var template = level.getStructureManager().get(Asterion.id("safe_room")).orElseThrow();
            var origin = new BlockPos(100 + index++ * 60, 180, 80);
            var settings = new StructurePlaceSettings().setRotation(rotation);
            check(template.placeInWorld(level, origin, origin, settings, RandomSource.create(1), 18), "Refuge placement failed");
            var box = template.getBoundingBox(settings, origin);
            var center = new BlockPos((box.minX() + box.maxX()) / 2, 181, (box.minZ() + box.maxZ()) / 2);
            check(level.getBlockState(center).is(RespawnObelisks.OBELISK), "Obelisk is off centre");
            int parts = 0, pads = 0;
            for (var pos : BlockPos.betweenClosed(box.minX(), 180, box.minZ(), box.maxX(), 190, box.maxZ())) {
                var state = level.getBlockState(pos);
                if (state.is(RespawnObelisks.OBELISK)) {
                    parts++;
                    check(state.getValue(SanctuaryBlock.CHARGE) == 1, "Refuge obelisk is inactive");
                    check(RespawnObelisks.OBELISK.root(pos, state).equals(center), "Rotated obelisk part points to another root");
                }
                if (state.is(Blocks.LODESTONE)) {
                    pads++;
                    check(level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir(), "Respawn pad is obstructed");
                }
            }
            check(parts == 27 && pads == 1, "Incomplete obelisk or missing checkpoint");
        }
        for (String name : new String[]{"gatehouse", "collapsed_dwelling", "courtyard"}) {
            var template = level.getStructureManager().get(Asterion.id("ruins/" + name)).orElseThrow();
            var origin = new BlockPos(400 + index++ * 32, 180, 80);
            check(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1), 18), "Ruin failed: " + name);
        }
        Asterion.LOGGER.info("PASS: one-time corpse harvest with saved state, four refuge rotations, centred multipart obelisks and three ruin templates");
    }

    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
