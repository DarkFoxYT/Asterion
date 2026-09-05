package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

final class SkeletonLootCheck {
    static void run(MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().getFirst();
        var originalLevel = player.level();
        var originalPosition = player.position();
        var originalMode = player.gameMode.getGameModeForPlayer();
        var originalHand = player.getMainHandItem().copy();
        var level = server.getLevel(Asterion.ASTERION_LEVEL);
        var pos = new BlockPos(320, 70, 320);
        var clock = (net.minecraft.world.level.storage.ServerLevelData)server.overworld().getLevelData();
        long time = level.getGameTime();
        try {
            player.teleportTo(level, 322.5, 70, 320.5, java.util.Set.of(), 0, 0, true);
            player.setGameMode(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 18);
            level.setBlock(pos, Asterion.SKELETON.defaultBlockState(), 18);
            check(player.gameMode.destroyBlock(pos), "Skeleton could not be broken by hand");
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
            int bones = drops.stream().filter(item -> item.getItem().is(Items.BONE)).mapToInt(item -> item.getItem().getCount()).sum();
            int skulls = drops.stream().filter(item -> item.getItem().is(Items.SKELETON_SKULL)).mapToInt(item -> item.getItem().getCount()).sum();
            check(bones >= 3 && bones <= 6 && skulls == 1, "Skeleton did not drop bones and one skull");
            check(drops.stream().noneMatch(item -> item.getItem().is(Asterion.SKELETON.asItem())), "Skeleton dropped itself");
            var tick = WorldGenerator.class.getDeclaredMethod("tickRestoringBlocks", MinecraftServer.class);
            tick.setAccessible(true);
            clock.setGameTime(time + 200);
            tick.invoke(null, server);
            check(level.getBlockState(pos).isAir(), "Maze repair replaced the broken skeleton");
            drops.forEach(ItemEntity::discard);
            Asterion.LOGGER.info("PASS: floor skeleton breaks by hand, drops 3–6 bones and one skull, and stays removed after maze repair");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        } finally {
            clock.setGameTime(time);
            player.teleportTo(originalLevel, originalPosition.x, originalPosition.y, originalPosition.z,
                    java.util.Set.of(), 0, 0, true);
            player.setGameMode(originalMode);
            player.setItemInHand(InteractionHand.MAIN_HAND, originalHand);
        }
    }

    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
