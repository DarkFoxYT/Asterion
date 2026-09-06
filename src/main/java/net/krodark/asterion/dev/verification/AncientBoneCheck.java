package net.krodark.asterion.dev.verification;

import java.util.ArrayList;
import java.util.Set;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.*;

final class AncientBoneCheck {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        var oldLevel = player.level();
        var oldPosition = player.position();
        var inventory = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 36; slot++) { inventory.add(player.getInventory().getItem(slot).copy()); player.getInventory().setItem(slot, ItemStack.EMPTY); }
        BlockPos pos = new BlockPos(0, 200, 0);
        try {
            player.teleportTo(level, .5, 200, 2.5, Set.of(), 0, 0, true);
            var heat = CrucibleBlockEntity.class.getDeclaredField("temperature"); heat.setAccessible(true);
            for (boolean boneFirst : new boolean[]{true, false}) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                level.setBlock(pos, Asterion.CRUCIBLE.defaultBlockState(), 18);
                level.setBlock(pos.below(), Blocks.CAMPFIRE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true), 3);
                var forge = (CrucibleBlockEntity)level.getBlockEntity(pos);
                Item first = boneFirst ? AncientContent.ANCIENT_BONE : Asterion.MAZESTEEL_BLOCK.asItem();
                Item second = boneFirst ? Asterion.MAZESTEEL_BLOCK.asItem() : AncientContent.ANCIENT_BONE;
                check(forge.insert(player, new ItemStack(Asterion.INGOT_CAST)), "Ingot cast rejected");
                check(forge.insert(player, new ItemStack(first)), "First ingredient rejected");
                for (int tick = 0; tick < 250; tick++) { heat.setInt(forge, 350); CrucibleBlockEntity.tick(level, pos, forge.getBlockState(), forge); }
                check(forge.hasUnsmeltedIngredients() && dropped(level, pos, Asterion.BONESTEEL_INGOT) == 0,
                        "One ingredient produced Bonesteel");
                check(forge.insert(player, new ItemStack(second)), "Second ingredient rejected");
                heat.setInt(forge, 0);
                CrucibleBlockEntity.tick(level, pos, forge.getBlockState(), forge);
                check(forge.materialUnits() == 2 && forge.hasUnsmeltedIngredients(), "Cold ingredients converted into metal");
                var saved = forge.saveWithFullMetadata(level.registryAccess());
                forge = (CrucibleBlockEntity)BlockEntity.loadStatic(pos, forge.getBlockState(), saved, level.registryAccess());
                level.setBlockEntity(forge);
                check(forge.materialUnits() == 2 && forge.hasUnsmeltedIngredients(), "Unsmelted ingredients lost on reload");
                for (int tick = 0; tick < 250; tick++) { heat.setInt(forge, 350); CrucibleBlockEntity.tick(level, pos, forge.getBlockState(), forge); }
                check(dropped(level, pos, Asterion.BONESTEEL_INGOT) == 0 && forge.hasUnsmeltedIngredients(),
                        "Forge smelted without a button press");
                forge.control(player, CrucibleControlPayload.POUR);
                check(dropped(level, pos, Asterion.BONESTEEL_INGOT) == 1 && forge.materialUnits() == 0,
                        "Pair did not eject one Bonesteel ingot");
                forge.control(player, CrucibleControlPayload.POUR);
                check(dropped(level, pos, Asterion.BONESTEEL_INGOT) == 1, "Repeated pour duplicated Bonesteel");
                clearDropped(level, pos);
                for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(),18);
            level.setBlock(pos, Asterion.CRUCIBLE.defaultBlockState(),18);
            var forge = (CrucibleBlockEntity)level.getBlockEntity(pos);
            forge.insert(player, new ItemStack(AncientContent.ANCIENT_BONE));
            forge.control(player, CrucibleControlPayload.removeMaterial(0));
            check(count(player, AncientContent.ANCIENT_BONE) == 1 && forge.materialUnits() == 0, "Removing raw bone returned the wrong item");

            var skeleton = AncientContent.SKELETON.create(level, EntitySpawnReason.COMMAND);
            var params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, skeleton)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().genericKill()).create(LootContextParamSets.ENTITY);
            var loot = server.reloadableRegistries().getLootTable(skeleton.getLootTable().orElseThrow());
            int rareDrops = 0;
            for (int seed = 1; seed <= 1000; seed++) {
                var drops = new ArrayList<ItemStack>();
                loot.getRandomItems(params, seed, drops::add);
                check(drops.stream().allMatch(s -> s.is(Items.BONE) || s.is(AncientContent.ANCIENT_BONE)), "Skeleton loot included equipment");
                rareDrops += drops.stream().filter(s -> s.is(AncientContent.ANCIENT_BONE)).mapToInt(ItemStack::getCount).sum();
            }
            check(rareDrops >= 20 && rareDrops <= 90, "Ancient Bone is not a rare drop: " + rareDrops + "/1000");
            Asterion.LOGGER.info("PASS: Ancient Bone rare loot ({}/1000), either input order, cold/incomplete mixture rejection, saved inputs, raw refunds, external-heat casting, front ejection and no duplicate output", rareDrops);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally {
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, inventory.get(slot));
            player.teleportTo(oldLevel, oldPosition.x, oldPosition.y, oldPosition.z, Set.of(), 0, 0, true);
        }
    }
    private static int count(net.minecraft.server.level.ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) if (player.getInventory().getItem(slot).is(item)) count += player.getInventory().getItem(slot).getCount();
        return count;
    }
    private static int dropped(net.minecraft.server.level.ServerLevel level, BlockPos pos, Item item) {
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(5), entity -> entity.getItem().is(item)).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }
    private static void clearDropped(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(5)).forEach(net.minecraft.world.entity.Entity::discard);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
