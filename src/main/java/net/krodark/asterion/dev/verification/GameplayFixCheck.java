package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.ChallengeSpawnerBlockEntity;
import net.krodark.asterion.game.GameplayContent;
import net.krodark.asterion.item.AfterblowItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/** Exercise encounter completion, saved countdowns and charge expiry in a disposable world. */
public final class GameplayFixCheck {
    public static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(0, 180, 0);
        player.teleportTo(5, 180, 0);
        for (BlockPos p : BlockPos.betweenClosed(-5, 179, -5, 5, 179, 5)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
        AABB area = new AABB(pos).inflate(12);
        for (var block : new net.minecraft.world.level.block.Block[]{GameplayContent.REWARD_SPAWNER, GameplayContent.EXPLOSIVE_SPAWNER}) {
            level.setBlock(pos, block.defaultBlockState(), 3);
            var state = level.getBlockState(pos);
            var spawner = (ChallengeSpawnerBlockEntity)level.getBlockEntity(pos);
            ChallengeSpawnerBlockEntity.tick(level, pos, state, spawner);
            var mobs = level.getEntitiesOfClass(Mob.class, area);
            check(!mobs.isEmpty(), "Spawner did not create mobs near survival player");
            if (block == GameplayContent.REWARD_SPAWNER) {
                for (Mob mob : mobs) mob.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
                ChallengeSpawnerBlockEntity.tick(level, pos, state, spawner);
                check(level.getBlockState(pos).isAir(), "Completed reward spawner remained active");
                check(level.getEntitiesOfClass(ItemEntity.class, area).stream().anyMatch(e -> e.getItem().is(Items.EMERALD)), "No encounter reward");
                mobs.forEach(Entity::discard);
            } else {
                for (int i = 0; i < 39; i++) ChallengeSpawnerBlockEntity.tick(level, pos, state, spawner);
                var saved = spawner.saveWithFullMetadata(level.registryAccess());
                var restored = (ChallengeSpawnerBlockEntity)BlockEntity.loadStatic(pos, state, saved, level.registryAccess());
                check(restored != null, "Spawner reload failed");
                check(level.getEntitiesOfClass(ArmorStand.class, area).stream().anyMatch(e -> e.getCustomName() != null
                        && "58".equals(e.getCustomName().getString())
                        && e.getCustomName().getStyle().getColor().getValue() == 0xFF5555), "Missing red countdown");
                for (int i = 0; i < 1160; i++) ChallengeSpawnerBlockEntity.tick(level, pos, state, restored);
                check(level.getBlockState(pos).isAir(), "Expired explosive spawner remained");
                check(level.getEntitiesOfClass(ArmorStand.class, area).isEmpty(), "Countdown survived explosion");
                mobs.forEach(Entity::discard);
            }
        }
        ItemStack sword = new ItemStack(Asterion.AFTERBLOW);
        CompoundTag tag = new CompoundTag();
        tag.putFloat("afterblow_damage", 12); tag.putLong("afterblow_stored_at", level.getGameTime());
        sword.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        var item = (AfterblowItem)sword.getItem();
        item.inventoryTick(sword, level, player, EquipmentSlot.MAINHAND);
        check(sword.get(DataComponents.CUSTOM_MODEL_DATA).flags().getFirst(), "Charge did not power texture");
        check(AfterblowItem.consumeStored(sword, level.getGameTime()) == 12, "Charge damage lost");
        check(!sword.get(DataComponents.CUSTOM_MODEL_DATA).flags().getFirst(), "Discharge left powered texture");
        tag.putLong("afterblow_stored_at", level.getGameTime() - 201);
        sword.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        item.inventoryTick(sword, level, player, EquipmentSlot.MAINHAND);
        check(!sword.get(DataComponents.CUSTOM_MODEL_DATA).flags().getFirst(), "Expired charge stayed powered");
        player.setGameMode(GameType.CREATIVE);
        Asterion.LOGGER.info("PASS: reward/explosive spawners, countdown persistence and Afterblow charge textures");
    }
    private static void check(boolean passed, String message) { if (!passed) throw new AssertionError(message); }
}
