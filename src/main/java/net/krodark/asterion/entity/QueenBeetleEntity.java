package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.RespawnObelisks;
import net.krodark.asterion.network.QueenBeetleQuestPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Peaceful quest-giver. She never targets or damages players. */
public final class QueenBeetleEntity extends PathfinderMob implements GeoEntity {
    public static final int PETAL_TARGET = 8;
    private static final String ACTIVE_TAG = "asterion.queen_beetle_quest.active";
    private static final String COMPLETE_TAG = "asterion.queen_beetle_quest.complete";
    private static final String KILLS_TAG = "asterion.queen_beetle_kills.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public QueenBeetleEntity(EntityType<? extends QueenBeetleEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.12D)
                .add(Attributes.FOLLOW_RANGE, 12.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.7D, 0.012F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override public void kill(ServerLevel level) {
        // She is a persistent quest NPC, not a combat target. Administrative removal can
        // still use /data or entity discard paths without exposing a normal death state.
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        int petals = countPetals(player);
        int anger = angerTier(player);
        if (player.entityTags().contains(COMPLETE_TAG)) {
            ServerPlayNetworking.send(serverPlayer,
                    new QueenBeetleQuestPayload(QueenBeetleQuestPayload.COMPLETE, petals, PETAL_TARGET, anger));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.entityTags().contains(ACTIVE_TAG)) {
            player.addTag(ACTIVE_TAG);
            ServerPlayNetworking.send(serverPlayer,
                    new QueenBeetleQuestPayload(QueenBeetleQuestPayload.ACCEPTED, petals, PETAL_TARGET, anger));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (petals < PETAL_TARGET) {
            ServerPlayNetworking.send(serverPlayer,
                    new QueenBeetleQuestPayload(QueenBeetleQuestPayload.PROGRESS, petals, PETAL_TARGET, anger));
            return InteractionResult.SUCCESS_SERVER;
        }

        consumePetals(player, PETAL_TARGET);
        player.removeTag(ACTIVE_TAG);
        player.addTag(COMPLETE_TAG);
        ItemStack reward = new ItemStack(RespawnObelisks.CHARGED_RUNE);
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        ServerPlayNetworking.send(serverPlayer,
                new QueenBeetleQuestPayload(QueenBeetleQuestPayload.REWARDED, PETAL_TARGET, PETAL_TARGET, anger));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static int countPetals(Player player) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Asterion.TAINTED_PETALS.asItem())) found += stack.getCount();
        }
        return Math.min(found, PETAL_TARGET);
    }

    private static void consumePetals(Player player, int amount) {
        for (int slot = 0; slot < player.getInventory().getContainerSize() && amount > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(Asterion.TAINTED_PETALS.asItem())) continue;
            int removed = Math.min(amount, stack.getCount());
            stack.shrink(removed);
            amount -= removed;
        }
    }

    /** Restores the objective HUD after reconnecting without replaying dialogue. */
    public static void syncActiveQuest(ServerPlayer player) {
        if (player.entityTags().contains(ACTIVE_TAG))
            ServerPlayNetworking.send(player, new QueenBeetleQuestPayload(
                    QueenBeetleQuestPayload.RESTORE_ACTIVE, countPetals(player), PETAL_TARGET, angerTier(player)));
    }

    public static void recordBeetleKill(net.minecraft.world.entity.LivingEntity victim, DamageSource source) {
        if (!(victim instanceof BombadierBeetleEntity || victim instanceof RuneBeetleEntity)
                || !(source.getEntity() instanceof ServerPlayer player)) return;
        int kills = beetleKills(player);
        player.removeTag(KILLS_TAG + kills);
        player.addTag(KILLS_TAG + Math.min(9999, kills + 1));
    }

    private static int beetleKills(Player player) {
        for (String tag : player.entityTags()) if (tag.startsWith(KILLS_TAG)) {
            try { return Math.max(0, Integer.parseInt(tag.substring(KILLS_TAG.length()))); }
            catch (NumberFormatException ignored) { }
        }
        return 0;
    }

    public static int angerTier(Player player) {
        int kills = beetleKills(player);
        return kills >= 25 ? 4 : kills >= 10 ? 3 : kills >= 4 ? 2 : kills >= 1 ? 1 : 0;
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<QueenBeetleEntity>("movement", 4,
                state -> state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D
                        ? WALK : IDLE)));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
