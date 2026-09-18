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
    private static final EntityDataAccessor<Integer> EMERGENCE = SynchedEntityData.defineId(
            CharonsFerryEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PAID = SynchedEntityData.defineId(
            CharonsFerryEntity.class, EntityDataSerializers.STRING);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    private int departureWait = 40;
    private int returnWait;
    public static final int EMERGENCE_TICKS = 110;

    public CharonsFerryEntity(EntityType<? extends CharonsFerryEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void berth() {
        double z = UnderworldTerrain.FERRY_Z;
        setPos(UnderworldTerrain.riverCenter(z), UnderworldTerrain.WATER_Y + .65, z);
        setYRot(0F);
        entityData.set(SAILING, false);
        entityData.set(EMERGENCE, EMERGENCE_TICKS);
        entityData.set(PAID, "");
        departureWait = 40;
        returnWait = 0;
    }

    // The supplied model's main deck ends at 21 model pixels (16 pixels per block).
    public double deckY() { return getY() + 21.0 / 16.0; }

    @Override protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof CharonEntity && getPassengers().isEmpty();
    }

    @Override protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!(passenger instanceof CharonEntity)) {
            super.positionRider(passenger, move);
            return;
        }
        // Keep the ferryman inside the stern deck, not beyond its port rail.
        double yaw = Math.toRadians(getYRot());
        double stern = 1.0;
        move.accept(passenger, getX() - stern * Math.sin(yaw), deckY(),
                getZ() + stern * Math.cos(yaw));
        passenger.setYRot(getYRot());
        passenger.resetFallDistance();
    }
    public boolean sailing() { return entityData.get(SAILING); }
    public int emergenceTicks() { return entityData.get(EMERGENCE); }
    public boolean emerging() { return emergenceTicks() > 0 && emergenceTicks() < EMERGENCE_TICKS; }
    public boolean readyForFare() { return emergenceTicks() >= EMERGENCE_TICKS && !sailing(); }
    public boolean carries(Entity entity) { return entityData.get(RIDERS).contains("," + entity.getId() + ","); }
    public boolean hasPaid(Player player) { return entityData.get(PAID).contains("," + player.getUUID() + ","); }
    public void summon() {
        if (!level().isClientSide() && emergenceTicks() == 0) entityData.set(EMERGENCE, 1);
    }
    public void acceptFare(Player player) {
        if (level().isClientSide() || hasPaid(player)) return;
        entityData.set(PAID, entityData.get(PAID) + "," + player.getUUID() + ",");
        departureWait = Math.min(departureWait, 50);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(SAILING, false);
        data.define(RIDERS, "");
        data.define(EMERGENCE, 0);
        data.define(PAID, "");
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
        return Math.abs(localX) < 1.0625 && localZ > -3.0625 && localZ < 2.1875;
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
        if (level().isClientSide()) {
            double beforeX = getX(), beforeY = getY(), beforeZ = getZ();
            var supported = level().getEntities(this, getBoundingBox().inflate(1, 2.1, 1), this::supports);
            interpolation.interpolate();
            for (Entity walker : supported) {
                if (walker instanceof Player player && player.isLocalPlayer()) {
                    walker.setPos(walker.getX() + getX() - beforeX,
                            walker.getY() + getY() - beforeY, walker.getZ() + getZ() - beforeZ);
                    walker.setOnGround(true);
                    walker.resetFallDistance();
                }
            }
            return;
        }
        var walkers = level().getEntities(this, getBoundingBox().inflate(.8, 2.1, .8), this::supports);
        if (!level().isClientSide()) {
            String ids = walkers.stream().filter(Player.class::isInstance).map(Entity::getId).sorted()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            entityData.set(RIDERS, ids.isEmpty() ? "" : "," + ids + ",");
            // Migrate old submerged arrivals to a quietly moored ferry.
            if (emergenceTicks() < EMERGENCE_TICKS) {
                entityData.set(EMERGENCE, EMERGENCE_TICKS);
                setPos(getX(), UnderworldTerrain.WATER_Y + .65, getZ());
            }
            // Repair already-saved ferries whose old waterline left the deck submerged.
            if (!sailing()) setPos(getX(), UnderworldTerrain.WATER_Y + .65, getZ());
            if (!sailing()) {
                if (getZ() >= UnderworldTerrain.END_Z - 56) {
                    if (walkers.stream().noneMatch(Player.class::isInstance)) {
                        if (++returnWait >= 60) berth();
                    } else returnWait = 0;
                } else if (walkers.stream().filter(Player.class::isInstance)
                        .map(Player.class::cast).anyMatch(this::hasPaid)) {
                    boolean waiting = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(24),
                            p -> p.isAlive() && !p.isSpectator() && !p.getAbilities().flying)
                            .stream().anyMatch(p -> !supports(p) || !hasPaid(p));
                    if (waiting) departureWait = 100;
                    else if (--departureWait <= 0) entityData.set(SAILING, true);
                } else departureWait = 100;
            }
        }

        if (emergenceTicks() < EMERGENCE_TICKS || !sailing()) return;
        double oldX = getX(), oldY = getY(), oldZ = getZ();
        double tangent = (UnderworldTerrain.riverCenter(oldZ + .5)
                - UnderworldTerrain.riverCenter(oldZ - .5));
        double nextZ = Math.min(UnderworldTerrain.END_Z - 54, oldZ + .072 / Math.sqrt(1 + tangent * tangent));
        double nextX = UnderworldTerrain.riverCenter(nextZ);
        double nextY = UnderworldTerrain.WATER_Y + .65 + Math.sin(tickCount * .055) * .025;
        float oldYaw = getYRot();
        float targetYaw = (float)Math.toDegrees(Math.atan2(-tangent, 1.0));
        setYRot(oldYaw + net.minecraft.util.Mth.wrapDegrees(targetYaw - oldYaw) * .12F);
        setPos(nextX, nextY, nextZ);
        setDeltaMovement(Vec3.ZERO);

        Vec3 movement = new Vec3(nextX - oldX, nextY - oldY, nextZ - oldZ);
        for (Entity walker : walkers) {
            if (level().isClientSide() && !(walker instanceof Player player && player.isLocalPlayer())) continue;
            double turn = Math.toRadians(getYRot() - oldYaw);
            double relativeX = walker.getX() - oldX, relativeZ = walker.getZ() - oldZ;
            walker.setPos(nextX + relativeX * Math.cos(turn) - relativeZ * Math.sin(turn),
                    deckY(), nextZ + relativeX * Math.sin(turn) + relativeZ * Math.cos(turn));
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
        out.putInt("Emergence", emergenceTicks());
        out.putString("Paid", entityData.get(PAID));
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(SAILING, in.getBooleanOr("Sailing", false));
        departureWait = in.getIntOr("DepartureWait", 40);
        returnWait = in.getIntOr("ReturnWait", 0);
        entityData.set(EMERGENCE, Math.clamp(in.getIntOr("Emergence", 0), 0, EMERGENCE_TICKS));
        entityData.set(PAID, in.getStringOr("Paid", ""));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
}
