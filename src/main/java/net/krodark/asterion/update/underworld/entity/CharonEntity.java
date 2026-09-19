package net.krodark.asterion.update.underworld.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Friendly ferryman fixed to the shared ferry. His fare is one gold nugget. */
public final class CharonEntity extends Entity implements GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    public static final UUID SHARED_ID = UUID.nameUUIDFromBytes(
            "asterion:limbo:charon".getBytes(StandardCharsets.UTF_8));
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 2);

    public CharonEntity(EntityType<? extends CharonEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) { }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith(Entity other) { return false; }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    @Override public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
        if (player.isSpectator() || player.distanceToSqr(this) > 49.0) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            Entity entity = ((ServerLevel)level()).getEntity(CharonsFerryEntity.SHARED_ID);
            if (!(entity instanceof CharonsFerryEntity ferry) || !ferry.readyForFare()) {
                player.sendOverlayMessage(Component.translatable("message.asterion.charon.wait"));
            } else if (ferry.hasPaid(player)) {
                player.sendOverlayMessage(Component.translatable("message.asterion.charon.paid"));
            } else if (!player.getAbilities().instabuild && !player.getItemInHand(hand).is(Items.GOLD_NUGGET)) {
                player.sendOverlayMessage(Component.translatable("message.asterion.charon.fare"));
            } else {
                if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
                ferry.acceptFare(player);
                player.sendOverlayMessage(Component.translatable("message.asterion.charon.accepted"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override public void tick() {
        super.tick();
        if (isPassenger()) {
            interpolation.cancel();
            return;
        }
        // Repair old saves through the real passenger relationship; vanilla then synchronizes
        // the ferryman with the ferry instead of two independent position streams.
        if (level() instanceof ServerLevel server
                && server.getEntity(CharonsFerryEntity.SHARED_ID) instanceof CharonsFerryEntity ferry) {
            startRiding(ferry);
        } else if (level().isClientSide()) {
            interpolation.interpolate();
        }
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) { }
    @Override protected void readAdditionalSaveData(ValueInput in) { }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<CharonEntity>("idle", 12,
                state -> state.setAndContinue(IDLE)));
    }
}
