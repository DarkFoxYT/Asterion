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
import net.minecraft.util.Mth;

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

    private static final String INDEX_TAG = "asterion.queen_beetle_quest.index.";

    public static int questIndex(Player player) {
        for (String tag : player.entityTags()) if (tag.startsWith(INDEX_TAG)) {
            try { return Math.clamp(Integer.parseInt(tag.substring(INDEX_TAG.length())), 0, QueenBeetleQuests.ALL.size()); }
            catch (NumberFormatException ignored) { }
        }
        // Old saves finished only the petal introduction. They can continue at request two.
        return player.entityTags().contains(COMPLETE_TAG) ? 1 : 0;
    }

    private static void setQuestIndex(Player player, int index) {
        for (String tag : java.util.List.copyOf(player.entityTags())) if (tag.startsWith(INDEX_TAG)) player.removeTag(tag);
        player.addTag(INDEX_TAG + index);
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        int index = questIndex(player);
        int anger = angerTier(player);
        if (index >= QueenBeetleQuests.ALL.size()) {
            sendQuest(serverPlayer, QueenBeetleQuestPayload.COMPLETE, index - 1, 0, 1, anger);
            return InteractionResult.SUCCESS_SERVER;
        }
        var quest = QueenBeetleQuests.get(index);
        int target = index == 0 ? petalTarget(anger) : quest.count();
        int progress = countItems(player, quest.item().asItem(), target);
        setQuestIndex(player, index);
        player.removeTag(COMPLETE_TAG);
        if (!player.entityTags().contains(ACTIVE_TAG)) {
            player.addTag(ACTIVE_TAG);
            sendQuest(serverPlayer, QueenBeetleQuestPayload.ACCEPTED, index, progress, target, anger);
        } else if (progress < target) {
            sendQuest(serverPlayer, QueenBeetleQuestPayload.PROGRESS, index, progress, target, anger);
        } else {
            consumeItems(player, quest.item().asItem(), target);
            player.removeTag(ACTIVE_TAG);
            setQuestIndex(player, index + 1);
            if (index + 1 == QueenBeetleQuests.ALL.size()) player.addTag(COMPLETE_TAG);
            ItemStack reward = new ItemStack(quest.reward(), quest.rewardCount());
            if (!player.getInventory().add(reward)) player.drop(reward, false);
            sendQuest(serverPlayer, QueenBeetleQuestPayload.REWARDED, index, target, target, anger);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    private static void sendQuest(ServerPlayer player, int stage, int index, int progress, int target, int anger) {
        ServerPlayNetworking.send(player, new QueenBeetleQuestPayload(stage, progress, target, anger, index));
    }

    public static int countItems(Player player, net.minecraft.world.item.Item item, int target) {
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) found += stack.getCount();
        }
        return Math.min(found, target);
    }

    private static void consumeItems(Player player, net.minecraft.world.item.Item item, int amount) {
        for (int slot = 0; slot < player.getInventory().getContainerSize() && amount > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(amount, stack.getCount());
            stack.shrink(removed);
            amount -= removed;
        }
    }

    /** Restores the exact request after reconnecting without replaying dialogue. */
    public static void syncActiveQuest(ServerPlayer player) {
        int index = questIndex(player);
        if (player.entityTags().contains(ACTIVE_TAG) && index < QueenBeetleQuests.ALL.size()) {
            int anger = angerTier(player);
            var quest = QueenBeetleQuests.get(index);
            int target = index == 0 ? petalTarget(anger) : quest.count();
            sendQuest(player, QueenBeetleQuestPayload.RESTORE_ACTIVE, index,
                    countItems(player, quest.item().asItem(), target), target, anger);
        }
    }

    public static void copyQuests(ServerPlayer oldPlayer, ServerPlayer newPlayer) {
        for (String tag : java.util.List.copyOf(newPlayer.entityTags()))
            if (tag.startsWith("asterion.queen_beetle_quest.") || tag.startsWith(KILLS_TAG)) newPlayer.removeTag(tag);
        for (String tag : oldPlayer.entityTags())
            if (tag.startsWith("asterion.queen_beetle_quest.") || tag.startsWith(KILLS_TAG)) newPlayer.addTag(tag);
        syncActiveQuest(newPlayer);
    }
    public static void recordBeetleKill(net.minecraft.world.entity.LivingEntity victim, DamageSource source) {
        if (!(victim instanceof BombadierBeetleEntity || victim instanceof RuneBeetleEntity)
                || !(source.getEntity() instanceof ServerPlayer player)) return;
        int kills = beetleKills(player);
        player.removeTag(KILLS_TAG + kills);
        player.addTag(KILLS_TAG + Math.min(9999, kills + 1));
        if (player.entityTags().contains(ACTIVE_TAG)) syncActiveQuest(player);
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

    /** Her request remains completable, but slaughtering her brood makes trust costlier. */
    public static int petalTarget(int anger) {
        return PETAL_TARGET + Mth.clamp(anger, 0, 4) * 2;
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
