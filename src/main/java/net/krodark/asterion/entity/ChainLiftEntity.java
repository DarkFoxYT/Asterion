package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

 
public final class ChainLiftEntity extends Entity implements GeoEntity {
    public static final int WAIT_TICKS = 100;
    public static final int RETURN_WAIT_TICKS = 40;
    private static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(ChainLiftEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> FROM = SynchedEntityData.defineId(ChainLiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TO = SynchedEntityData.defineId(ChainLiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CEILING = SynchedEntityData.defineId(ChainLiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(ChainLiftEntity.class, EntityDataSerializers.LONG);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 1);
    private int waiting;
    private boolean descending, armed;
    private int requestedStop = -1;
    private int runeCheckTicks;
    private boolean calledLanding;

    public ChainLiftEntity(EntityType<? extends ChainLiftEntity> type, Level level) { super(type, level); setNoGravity(true); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(ANCHOR, BlockPos.ZERO); data.define(FROM, 0F); data.define(TO, 0F);
        data.define(CEILING, Integer.MIN_VALUE); data.define(START, 0L);
    }
    public void configure(BlockPos anchor, int ceiling) {
        entityData.set(ANCHOR, anchor); entityData.set(CEILING, ceiling);
        float top = ceiling - 3.0F;
        entityData.set(FROM, top); entityData.set(TO, top);
        setPos(anchor.getX() + .5, top, anchor.getZ() + .5);
        descending = false; armed = false; waiting = WAIT_TICKS;
    }
    public void callTo(boolean upper) {
        if (level().isClientSide()) return;
        requestedStop = upper ? 1 : 0;
    }
    private void ensureCallRunes(ServerLevel level) {
        for (boolean upper : new boolean[]{false, true}) {
            var id = java.util.UUID.nameUUIDFromBytes((getUUID() + ":call:" + upper).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (level.getEntity(id) != null) continue;
            double y = (upper ? topY() : bottomY()) + 1.2;
            BlockPos position = BlockPos.containing(getX() + 2, y, getZ());
            for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                BlockPos candidate = BlockPos.containing(getX() + direction.getStepX() * 2, y, getZ() + direction.getStepZ() * 2);
                if (level.hasChunkAt(candidate) && level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()) { position = candidate; break; }
            }
            if (!level.hasChunkAt(position)) continue;
            var rune = ChainLiftContent.CALL_RUNE.create(level, EntitySpawnReason.EVENT);
            if (rune == null) continue;
            rune.setUUID(id); rune.configure(anchor(), upper);
            rune.setPos(position.getX() + .5, y, position.getZ() + .5);
            level.addFreshEntity(rune);
        }
    }
    public BlockPos anchor() { return entityData.get(ANCHOR); }
    public int ceiling() { return entityData.get(CEILING); }
    public double bottomY() { return anchor().getY() + .5; }
    public double topY() { return ceiling() - 3; }
    public boolean moving() { return entityData.get(FROM).floatValue() != entityData.get(TO).floatValue(); }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean canBeCollidedWith(Entity other) {
         
        return isAlive() && !supports(other);
    }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    private static final double START_SPEED = .03, MAX_SPEED = .45, ACCELERATION = .06;
    private static final double RAMP_TICKS = Math.log(MAX_SPEED / START_SPEED) / ACCELERATION;
    private static final double RAMP_DISTANCE = (MAX_SPEED - START_SPEED) / ACCELERATION;

    private static double acceleratedDistance(double ticks) {
        return ticks <= RAMP_TICKS ? START_SPEED * Math.expm1(ACCELERATION * ticks) / ACCELERATION
                : RAMP_DISTANCE + (ticks - RAMP_TICKS) * MAX_SPEED;
    }
    public static int travelTicks(double distance) {
        double half = Math.abs(distance) / 2;
        double ramp = half <= RAMP_DISTANCE ? Math.log1p(half * ACCELERATION / START_SPEED) / ACCELERATION
                : RAMP_TICKS + (half - RAMP_DISTANCE) / MAX_SPEED;
        return Math.max(2, (int)Math.ceil(ramp) * 2);
    }
    public double scheduledY(long time) {
        double from = entityData.get(FROM), to = entityData.get(TO);
        double duration = travelTicks(to - from), half = duration / 2;
        double elapsed = Math.clamp(time - entityData.get(START), 0, duration);
        double fraction = .5 * acceleratedDistance(Math.min(elapsed, duration - elapsed)) / acceleratedDistance(half);
        return from + (to - from) * (elapsed <= half ? fraction : 1 - fraction);
    }
    public boolean supports(Entity entity) {
         
        double tolerance = !level().isClientSide() && entity instanceof Player ? .65 : .16;
        return entity.isAlive() && !entity.isSpectator() && !entity.isPassenger()
                && (!(entity instanceof Player player) || !player.getAbilities().flying)
                && Math.abs(entity.getY() - (getY() + .5)) < tolerance && entity.getDeltaMovement().y <= .08;
    }
    public boolean overlapsDeck(AABB bounds) {
        AABB deck = getBoundingBox();
        return bounds.maxX > deck.minX && bounds.minX < deck.maxX && bounds.maxZ > deck.minZ && bounds.minZ < deck.maxZ;
    }
    public static ChainLiftEntity supporting(Entity entity) {
        for (ChainLiftEntity lift : entity.level().getEntitiesOfClass(ChainLiftEntity.class,
                entity.getBoundingBox().inflate(.01, .7, .01))) {
            if (lift.supports(entity) && lift.overlapsDeck(entity.getBoundingBox())) return lift;
        }
        return null;
    }
    @Override public void tick() {
        super.tick();
        interpolation.cancel();
        if (ceiling() == Integer.MIN_VALUE) return;
        if (!level().isClientSide() && !level().getBlockState(anchor()).is(ChainLiftContent.ANCHOR)) { discard(); return; }
        if (level() instanceof ServerLevel server && --runeCheckTicks <= 0) { ensureCallRunes(server); runeCheckTicks = 100; }
        var riders = level().getEntities(this, getBoundingBox().inflate(0, .7, 0).expandTowards(0, 2, 0), this::supports);
        double next = scheduledY(level().getGameTime()), dy = next - getY();
        if (!level().isClientSide() && Math.abs(dy) > .00001) {
             
            AABB swept = new AABB(getX()-1.49, getY()+.01, getZ()-1.49, getX()+1.49, getY()+3, getZ()+1.49).expandTowards(0, dy, 0);
            boolean blocked = level().getBlockCollisions(this, swept).iterator().hasNext();
            if (dy < 0) blocked |= !level().getEntities(this, getBoundingBox().expandTowards(0, dy, 0), e -> !(e instanceof LiftCallRuneEntity) && !riders.contains(e) && e.isAlive() && !e.isSpectator()).isEmpty();
            if (blocked) { stopAtCurrentPosition(); next = getY(); dy = 0; waiting = WAIT_TICKS; }
        }
         
        setPos(anchor().getX() + .5, next, anchor().getZ() + .5);
        for (Entity rider : riders) {
            if (level().isClientSide() && !(rider instanceof Player player && player.isLocalPlayer())) continue;
            rider.setPos(rider.getX(), getY() + .5, rider.getZ());
            if (rider.getDeltaMovement().y < 0) rider.setDeltaMovement(rider.getDeltaMovement().multiply(1, 0, 1));
            rider.fallDistance = 0; rider.setOnGround(true);
        }
        if (level().isClientSide()) return;
        if (moving() && level().getGameTime() >= entityData.get(START) + travelTicks(entityData.get(TO)-entityData.get(FROM))) {
            stopAtCurrentPosition();
            armed = false;
            waiting = descending ? RETURN_WAIT_TICKS : WAIT_TICKS;
        }
        if (!moving()) {
            if (requestedStop >= 0) {
                double target = requestedStop == 1 ? topY() : bottomY();
                requestedStop = -1;
                calledLanding = true;
                armed = false;
                waiting = WAIT_TICKS;
                if (Math.abs(getY() - target) > .02) { beginJourney(target, target < getY()); return; }
            }
            boolean playerAboard = riders.stream().anyMatch(e -> e instanceof Player);
            boolean atTop = Math.abs(getY() - topY()) < .02;
            boolean atBottom = Math.abs(getY() - bottomY()) < .02;
            if (atTop) {
                 
                 
                if (!playerAboard) { armed = false; waiting = WAIT_TICKS; }
                else if (!armed) { armed = true; waiting = WAIT_TICKS; }
                else if (--waiting <= 0) beginJourney(bottomY(), true);
            } else if (atBottom && calledLanding) {
                if (!playerAboard) { armed = false; waiting = WAIT_TICKS; }
                else if (!armed) { armed = true; waiting = WAIT_TICKS; }
                else if (--waiting <= 0) { calledLanding = false; beginJourney(topY(), false); }
            } else if (atBottom) {
                 
                if (playerAboard) waiting = RETURN_WAIT_TICKS;
                else if (--waiting <= 0) beginJourney(topY(), false);
            } else if (--waiting <= 0) {
                 
                 
                if (descending && playerAboard) beginJourney(bottomY(), true);
                else beginJourney(topY(), false);
            }
        }
    }
    private void beginJourney(double target, boolean goingDown) {
        descending = goingDown;
        entityData.set(FROM, (float)getY()); entityData.set(TO, (float)target);
        entityData.set(START, level().getGameTime() + 3);  
    }
    private void stopAtCurrentPosition() { entityData.set(FROM, (float)getY()); entityData.set(TO, (float)getY()); }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        out.putLong("Anchor", anchor().asLong()); out.putInt("Ceiling", ceiling());
        out.putInt("RequestedStop", requestedStop >= 0 ? requestedStop : moving() && calledLanding ? (descending ? 0 : 1) : -1); out.putBoolean("CalledLanding", calledLanding);
        out.putBoolean("Descending", descending); out.putBoolean("Armed", armed); out.putInt("Waiting", waiting);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(ANCHOR, BlockPos.of(in.getLongOr("Anchor", 0))); entityData.set(CEILING, in.getIntOr("Ceiling", Integer.MIN_VALUE));
        stopAtCurrentPosition(); descending = in.getBooleanOr("Descending", false);
        requestedStop = Math.clamp(in.getIntOr("RequestedStop", -1), -1, 1); calledLanding = in.getBooleanOr("CalledLanding", false);
        armed = in.getBooleanOr("Armed", false); waiting = in.getIntOr("Waiting", WAIT_TICKS);
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
}
