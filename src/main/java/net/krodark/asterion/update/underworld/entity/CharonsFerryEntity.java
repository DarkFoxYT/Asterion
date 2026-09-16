package net.krodark.asterion.update.underworld.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The single shared Limbo ferry: a walkable, self-guided moving platform. */
public final class CharonsFerryEntity extends Entity implements GeoEntity {
    public static final UUID SHARED_ID = UUID.nameUUIDFromBytes(
            "asterion:limbo:charons_ferry".getBytes(StandardCharsets.UTF_8));
    private static final EntityDataAccessor<Boolean> SAILING = SynchedEntityData.defineId(
            CharonsFerryEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> RIDERS = SynchedEntityData.defineId(
            CharonsFerryEntity.class, EntityDataSerializers.STRING);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    private int departureWait = 40;
    private int returnWait;

    public CharonsFerryEntity(EntityType<? extends CharonsFerryEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void berth() {
        double z = UnderworldTerrain.FERRY_Z;
        setPos(UnderworldTerrain.riverCenter(z), UnderworldTerrain.WATER_Y + .16, z);
        setYRot(0F);
        entityData.set(SAILING, false);
        departureWait = 40;
        returnWait = 0;
    }

    public double deckY() { return getY() + .52; }
    public boolean sailing() { return entityData.get(SAILING); }
    public boolean carries(Entity entity) { return entityData.get(RIDERS).contains("," + entity.getId() + ","); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(SAILING, false);
        data.define(RIDERS, "");
    }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith(Entity other) { return isAlive() && !supports(other); }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    public boolean overlapsDeck(AABB bounds) {
        double dx = (bounds.minX + bounds.maxX) * .5 - getX();
        double dz = (bounds.minZ + bounds.maxZ) * .5 - getZ();
        double yaw = Math.toRadians(getYRot());
        double localX = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        double localZ = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
        return Math.abs(localX) < 2.05 && Math.abs(localZ) < 3.25;
    }

    public boolean supports(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger()
                || entity instanceof Player player && player.getAbilities().flying) return false;
        double offset = entity.getY() - deckY();
        if (!entity.onGround() && offset > .06) return false;
        boolean recovering = carries(entity) && offset < 0 && offset >= -1.5 && overlapsDeck(entity.getBoundingBox());
        return (Math.abs(offset) < .72 || recovering) && entity.getDeltaMovement().y <= .08
                && overlapsDeck(entity.getBoundingBox());
    }

    public static CharonsFerryEntity supporting(Entity entity) {
        for (CharonsFerryEntity ferry : entity.level().getEntitiesOfClass(CharonsFerryEntity.class,
                entity.getBoundingBox().inflate(4.0, 2.0, 4.0)))
            if (ferry.supports(entity)) return ferry;
        return null;
    }

    @Override public void tick() {
        super.tick();
        interpolation.cancel();
        var walkers = level().getEntities(this, getBoundingBox().inflate(.8, 2.1, .8), this::supports);
        if (!level().isClientSide()) {
            String ids = walkers.stream().filter(Player.class::isInstance).map(Entity::getId).sorted()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            entityData.set(RIDERS, ids.isEmpty() ? "" : "," + ids + ",");
            if (!sailing()) {
                if (getZ() >= UnderworldTerrain.END_Z - 56) {
                    if (walkers.isEmpty() && ++returnWait >= 60) berth();
                } else if (walkers.stream().anyMatch(Player.class::isInstance)) {
                    if (--departureWait <= 0) entityData.set(SAILING, true);
                } else departureWait = 40;
            }
        }

        if (!sailing()) return;
        double oldX = getX(), oldY = getY(), oldZ = getZ();
        double nextZ = Math.min(UnderworldTerrain.END_Z - 54, oldZ + .072);
        double nextX = UnderworldTerrain.riverCenter(nextZ);
        double nextY = UnderworldTerrain.WATER_Y + .16 + Math.sin(tickCount * .055) * .025;
        setYRot((float)Math.toDegrees(Math.atan2(-(nextX - oldX), nextZ - oldZ)));
        setPos(nextX, nextY, nextZ);
        setDeltaMovement(Vec3.ZERO);

        Vec3 movement = new Vec3(nextX - oldX, nextY - oldY, nextZ - oldZ);
        for (Entity walker : walkers) {
            if (level().isClientSide() && !(walker instanceof Player player && player.isLocalPlayer())) continue;
            walker.setPos(walker.getX() + movement.x, deckY(), walker.getZ() + movement.z);
            if (walker.getDeltaMovement().y < 0)
                walker.setDeltaMovement(walker.getDeltaMovement().multiply(1, 0, 1));
            walker.setOnGround(true);
            walker.resetFallDistance();
        }
        if (nextZ >= UnderworldTerrain.END_Z - 54) entityData.set(SAILING, false);
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        out.putBoolean("Sailing", sailing());
        out.putInt("DepartureWait", departureWait);
        out.putInt("ReturnWait", returnWait);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(SAILING, in.getBooleanOr("Sailing", false));
        departureWait = in.getIntOr("DepartureWait", 40);
        returnWait = in.getIntOr("ReturnWait", 0);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
}
