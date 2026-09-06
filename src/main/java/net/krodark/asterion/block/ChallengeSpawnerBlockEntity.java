package net.krodark.asterion.block;

import java.util.*;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.GameplayContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.decoration.ArmorStand;

import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class ChallengeSpawnerBlockEntity extends BlockEntity {
    private boolean started, complete;
    private int remaining = 60 * 20;
    private final List<UUID> mobs = new ArrayList<>();
    private UUID label;
    public ChallengeSpawnerBlockEntity(BlockPos pos, BlockState state) { super(GameplayContent.CHALLENGE_SPAWNER_ENTITY, pos, state); }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putBoolean("Started", started); out.putBoolean("Complete", complete); out.putInt("Remaining", remaining);
        out.putString("Mobs", String.join(",", mobs.stream().map(UUID::toString).toList()));
        if (label != null) out.putString("Label", label.toString());
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        started = in.getBooleanOr("Started", false); complete = in.getBooleanOr("Complete", false);
        remaining = Math.clamp(in.getIntOr("Remaining", 1200), 0, 1200);
        mobs.clear();
        for (String id : in.getStringOr("Mobs", "").split(",")) if (!id.isEmpty()) mobs.add(UUID.fromString(id));
        String id = in.getStringOr("Label", ""); label = id.isEmpty() ? null : UUID.fromString(id);
    }
    public static void tick(Level world, BlockPos pos, BlockState state, ChallengeSpawnerBlockEntity spawner) {
        if (!(world instanceof ServerLevel level) || spawner.complete) return;
        boolean explosive = ((ChallengeSpawnerBlock)state.getBlock()).explosive();
        if (!spawner.started) {
            var player = level.players().stream().filter(p -> p.isAlive() && !p.isCreative() && !p.isSpectator()
                    && p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) < 64).findFirst().orElse(null);
            if (player == null || level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) return;
            int groupSize = 2 + level.getRandom().nextInt(3);
            for (int attempt = 0; attempt < 24 && spawner.mobs.size() < groupSize; attempt++) {
                EntityType<? extends Mob> type = level.getBiome(pos).is(Asterion.FORGE_BIOME)
                        ? Asterion.CONSTRUCT
                        : (level.getRandom().nextBoolean()
                        ? net.krodark.asterion.game.AncientContent.SKELETON : Asterion.CONSTRUCT);
                Mob mob = type.create(level, EntitySpawnReason.SPAWNER);
                if (mob == null) continue;
                BlockPos spawn = pos.offset(level.getRandom().nextInt(7) - 3, 0, level.getRandom().nextInt(7) - 3);
                mob.setPos(spawn.getX() + .5, spawn.getY(), spawn.getZ() + .5);
                if (!level.noCollision(mob) || !level.getBlockState(spawn.below()).isFaceSturdy(level, spawn.below(), net.minecraft.core.Direction.UP)) continue;
                mob.setPersistenceRequired(); mob.setTarget(player);
                mob.addTag(net.krodark.asterion.game.ChallengeDeaths.TAG);
                if (level.addFreshEntity(mob)) spawner.mobs.add(mob.getUUID());
            }
            if (spawner.mobs.isEmpty()) return;
            spawner.started = true;
        }
         
        var deaths = net.krodark.asterion.game.ChallengeDeaths.get(level);
        spawner.mobs.removeIf(deaths::consume);
        if (spawner.mobs.isEmpty()) {
            spawner.complete = true;
            spawner.removeLabel(level);
            dropRewards(level, pos);

            ExperienceOrb.award(level, pos.getCenter().add(0, 1, 0), 20);
        } else if (explosive) {
            if (--spawner.remaining <= 0) {
                spawner.complete = true; spawner.removeLabel(level);
                level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 3F, Level.ExplosionInteraction.NONE);
            } else if (spawner.remaining % 20 == 0) {
                ArmorStand display = spawner.label == null ? null : level.getEntity(spawner.label) instanceof ArmorStand stand ? stand : null;
                if (display == null) {
                    display = new ArmorStand(level, pos.getX() + .5, pos.getY() + 1.35, pos.getZ() + .5);
                    display.setInvisible(true); display.setNoGravity(true); display.setInvulnerable(true);
                    display.setCustomNameVisible(true);
                    level.addFreshEntity(display); spawner.label = display.getUUID();
                }
                display.setCustomName(Component.literal(Integer.toString((spawner.remaining + 19) / 20)).withStyle(ChatFormatting.RED));
            }
        }
        spawner.setChanged();
    }
    private void removeLabel(ServerLevel level) {
        if (label != null && level.getEntity(label) != null) level.getEntity(label).discard();
        label = null;
    }
    private static void dropRewards(ServerLevel level, BlockPos pos) {
        var random = level.getRandom();
        Block.popResource(level, pos.above(), new ItemStack(Asterion.SHALE_TARNISHED_GOLD_ORE, 3 + random.nextInt(4)));
        ItemStack reward = switch (random.nextInt(17)) {
            case 0 -> new ItemStack(Items.GOLDEN_APPLE);
            case 1 -> new ItemStack(Items.GOLDEN_CARROT, 2 + random.nextInt(3));
            case 2 -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.SWIFTNESS);
            case 3 -> net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.FIRE_RESISTANCE);
            case 4 -> new ItemStack(Items.TORCH, 8 + random.nextInt(9));
            case 5 -> new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4));
            case 6 -> new ItemStack(Asterion.CELESTIAL_GOLD_INGOT);
            case 7 -> new ItemStack(Items.COAL, 3 + random.nextInt(5));
            case 8 -> new ItemStack(Items.APPLE, 2 + random.nextInt(4));
            case 9 -> new ItemStack(Items.COOKED_BEEF, 3 + random.nextInt(5));
            case 10 -> new ItemStack(Asterion.RUNE_TABLETS[random.nextInt(Asterion.RUNE_TABLETS.length)]);
            case 11 -> new ItemStack(Items.DIAMOND);
            default -> new ItemStack(Asterion.SHALE_CELESTIAL_GOLD_ORE, 1 + random.nextInt(2));
        };
        Block.popResource(level, pos.above(), reward);
        if (random.nextInt(100) == 0) Block.popResource(level, pos.above(), new ItemStack(net.krodark.asterion.game.AncientContent.ANCIENT_BONE));
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) removeLabel(server);
        super.preRemoveSideEffects(pos, state);
    }
}

