package net.krodark.asterion.block;

import java.util.*;
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
            for (int attempt = 0; attempt < 24 && spawner.mobs.size() < 3; attempt++) {
                Mob mob = (attempt % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON).create(level, EntitySpawnReason.SPAWNER);
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
        // Missing entities may be in unloaded chunks: only a witnessed death counts.
        var deaths = net.krodark.asterion.game.ChallengeDeaths.get(level);
        spawner.mobs.removeIf(deaths::consume);
        if (spawner.mobs.isEmpty()) {
            spawner.complete = true;
            spawner.removeLabel(level);
            Block.popResource(level, pos.above(), new ItemStack(Items.EMERALD, 3 + level.getRandom().nextInt(4)));
            ExperienceOrb.award(level, pos.getCenter().add(0, 1, 0), 20);
            level.removeBlock(pos, false);
        } else if (explosive) {
            if (--spawner.remaining <= 0) {
                spawner.complete = true; spawner.removeLabel(level);
                level.removeBlock(pos, false);
                level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 3F, Level.ExplosionInteraction.MOB);
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
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel server) removeLabel(server);
        super.preRemoveSideEffects(pos, state);
    }
}
