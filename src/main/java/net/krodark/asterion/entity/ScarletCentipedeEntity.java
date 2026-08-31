package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumSet;

/** A directly controllable mount that keeps its feet attached across surface corners. */
public final class ScarletCentipedeEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final EntityDataAccessor<Integer> DATA_ATTACHED_SURFACE = SynchedEntityData.defineId(
            ScarletCentipedeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHAIN_SEGMENTS = SynchedEntityData.defineId(
            ScarletCentipedeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SEATS = SynchedEntityData.defineId(
            ScarletCentipedeEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<org.joml.Vector3fc> DATA_FORWARD = SynchedEntityData.defineId(
            ScarletCentipedeEntity.class, EntityDataSerializers.VECTOR3);
    private static final Direction[] SURFACES = Direction.values();
    private static final int DEFAULT_CHAIN_SEGMENTS = 7;
    private static final int MIN_CHAIN_SEGMENTS = CentipedeSegments.MIN;
    private static final int MAX_CHAIN_SEGMENTS = CentipedeSegments.MAX;
    private static final int CONTACT_GRACE_TICKS = 3;
    private static final double CONTACT_PROBE = 0.12D;
    private static final double ADHESION = 0.145D;
    private static final double RIDDEN_SPEED = 0.31D;
    private static final double SURFACE_BLEND = 0.18D;
    private int lastSurfaceSwitchTick = -100;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 surfaceForward = new Vec3(0.0D, 0.0D, -1.0D);
    private Vec3 smoothedAttachmentNormal = Direction.DOWN.getUnitVec3();
    private Vec3 smoothedSurfaceMotion = Vec3.ZERO;
    private Vec3 wildHeading = new Vec3(0, 0, -1);
    private double wildSpeed;
    private final CentipedeSeats seats = new CentipedeSeats();
    private String cachedSeats = "";
    private Vec3 driverHeading = Vec3.ZERO;
    private int driverFrameTick = -100;
    private int surfaceContactGrace = CONTACT_GRACE_TICKS;
    private final CentipedeChain bodyChain = new CentipedeChain();
    private final CentipedeCollision bodyCollision = new CentipedeCollision(region -> {
        List<AABB> blocks = new ArrayList<>();
        for (var shape : level().getBlockCollisions(this, region)) blocks.addAll(shape.toAabbs());
        return blocks;
    });

    public ScarletCentipedeEntity(EntityType<? extends ScarletCentipedeEntity> type, Level level) {
        super(type, level);
        xpReward = 4;
        if (!level.isClientSide()) setChainSegmentCount(CentipedeSegments.randomCount(random));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 18.0D)
                .add(Attributes.ARMOR, 5.0D)
                .add(Attributes.STEP_HEIGHT, 1.25D);
    }

    @Override
    public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        if (reason == EntitySpawnReason.NATURAL
                && (!(level instanceof ServerLevel serverLevel)
                || !serverLevel.dimension().equals(Asterion.ASTERION_LEVEL))) return false;
        if (level instanceof ServerLevel server && reason != EntitySpawnReason.SPAWN_ITEM_USE
                && net.krodark.asterion.worldgen.BossArenaEncounter.blocksCentipedeSpawn(server, position())) return false;
        return super.checkSpawnRules(level, reason);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACHED_SURFACE, Direction.DOWN.ordinal());
        builder.define(DATA_CHAIN_SEGMENTS, DEFAULT_CHAIN_SEGMENTS);
        builder.define(DATA_SEATS, "");
        builder.define(DATA_FORWARD, new Vector3f(0, 0, -1));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("chain_segments", chainSegmentCount());
        output.putString("segment_riders", getEntityData().get(DATA_SEATS));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setChainSegmentCount(input.getIntOr("chain_segments", chainSegmentCount()));
        getEntityData().set(DATA_SEATS, input.getStringOr("segment_riders", ""));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new SurfaceWanderGoal());
    }

    @Override
    public void tick() {
        if (getControllingPassenger() != null) navigation.stop();
        super.tick();
        if (!level().isClientSide() && getControllingPassenger() != null && tickCount - driverFrameTick <= 15) {
            surfaceForward = driverHeading;
            smoothedSurfaceMotion = Vec3.ZERO;
        }
        if (!level().isClientSide() || isLocalInstanceAuthoritative()) updateSurfaceAfterMovement();
        blendAttachmentNormal();
        if (!level().isClientSide()) {
            getEntityData().set(DATA_FORWARD, vector(surfaceForward()));
            // Saved reservations may outlive a disconnected player, but never reshuffle seats.
            if (tickCount == 60) {
                CentipedeSeats live = new CentipedeSeats();
                for (Entity rider : getPassengers()) live.claim(rider.getUUID(), seatIndex(rider), chainSegmentCount());
                getEntityData().set(DATA_SEATS, live.encode());
            }
        } else if (!isLocalInstanceAuthoritative()) {
            var heading = getEntityData().get(DATA_FORWARD);
            surfaceForward = new Vec3(heading.x(), heading.y(), heading.z());
            smoothedSurfaceMotion = Vec3.ZERO;
        }
        bodyChain.tick(chainHeadCenter(), attachmentNormal(), surfaceForward(), chainSegmentCount(), bodyCollision);
        for (Entity passenger : getPassengers()) positionRider(passenger);
        if (!usesSurfaceTravel()) setNoGravity(false);
        else {
            setNoGravity(true);
            resetFallDistance();
        }
    }

    @Override
    public void travel(Vec3 input) {
        updateNearbySurface(input);
        Direction surface = attachedSurface();
        if (isInWater() || isInLava() || !onGround() && !usesSurfaceTravel()) {
            if (input.x * input.x + input.z * input.z > 1.0E-4D) {
                Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
                surfaceForward = input.z < 0.0D ? facing.scale(-1.0D) : facing;
            }
            super.travel(getControllingPassenger() == null && wildSpeed > 0 ? new Vec3(0, 0, 1) : input);
            smoothedSurfaceMotion = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
            return;
        }

        setNoGravity(usesSurfaceTravel());
        resetFallDistance();
        Vec3 normal = attachmentNormal();
        Vec3 up = normal.scale(-1.0D);
        Vec3 forward = projectOntoSurface(surfaceForward, normal);
        if (forward.lengthSqr() < 1.0E-5D) forward = fallbackForward(up);
        forward = forward.normalize();

        LivingEntity controller = getControllingPassenger();
        Vec3 tangent;
        double speed;
        if (controller instanceof Player) {
            forward = riderForward((Player)controller, up);
            // Minecraft's xxa axis is positive-left, not positive-right.
            Vec3 left = up.cross(forward).normalize();
            tangent = forward.scale(input.z).subtract(left.scale(input.x));
            speed = RIDDEN_SPEED * Math.min(1.0D, tangent.length());
            if (tangent.lengthSqr() > 1.0E-5D) {
                tangent = tangent.normalize();
                surfaceForward = tangent;
            } else tangent = Vec3.ZERO;
        } else {
            tangent = isNoAi() ? Vec3.ZERO : CentipedeFrame.tangent(wildHeading, normal, forward);
            speed = isNoAi() ? 0 : wildSpeed;
            if (speed > 0) surfaceForward = tangent;
        }

        // Tangent movement uses the blended plane while adhesion targets the real block face.
        // During a corner transition this makes the in-between pose behave like a diagonal wall.
        Vec3 desiredTangent = tangent.scale(speed);
        double response = desiredTangent.lengthSqr() > 1.0E-5D ? 0.20D : 0.30D;
        smoothedSurfaceMotion = smoothedSurfaceMotion.lerp(desiredTangent, response);
        smoothedSurfaceMotion = projectOntoSurface(smoothedSurfaceMotion, normal);
        smoothedSurfaceMotion = bodyChain.limitHeadMotion(smoothedSurfaceMotion);
        if (smoothedSurfaceMotion.lengthSqr() < 1.0E-6D) smoothedSurfaceMotion = Vec3.ZERO;
        Vec3 motion = smoothedSurfaceMotion.add(surface.getUnitVec3().scale(ADHESION));
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (smoothedSurfaceMotion.lengthSqr() > 1.0E-5D) updateYaw(smoothedSurfaceMotion);
    }

    @Override
    protected void tickRidden(Player player, Vec3 input) {
        if (!usesSurfaceTravel()) {
            setYRot(player.getYRot());
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
            surfaceForward = Vec3.directionFromRotation(0.0F, getYRot());
            return;
        }
        surfaceForward = riderForward(player, attachmentNormal().scale(-1.0D));
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 ignored) {
        return new Vec3(player.xxa, 0.0D, player.zza);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float)RIDDEN_SPEED;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        for (Entity passenger : getPassengers())
            if (passenger instanceof Player player && seatIndex(player) == 0) return player;
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().size() < chainSegmentCount()
                && (seatIndex(passenger) >= 0 || seatTable().firstFree(chainSegmentCount()) >= 0);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!player.isSecondaryUseActive() && !player.isPassenger()) {
            // Vanilla interactions with the main entity still use the actual clicked section.
            Vec3 eye = player.getEyePosition();
            var hit = CentipedeInteraction.pick(eye, eye.add(player.getViewVector(1).scale(player.entityInteractionRange())),
                    chainSegmentCount(), i -> chainPose(i, 1));
            if (hit != null && (level().isClientSide() || mountSegment(player, hit.seat())))
                return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        int seat = Math.max(0, seatIndex(passenger));
        return CentipedeInteraction.saddle(chainPose(seat, 1), seat).subtract(position());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        Vec3 point = passengerPosition(passenger, 1);
        move.accept(passenger, point.x, point.y, point.z);
        passenger.resetFallDistance();
    }

    public Vec3 passengerPosition(Entity passenger, float partial) {
        int seat = Math.max(0, seatIndex(passenger));
        var pose = chainPose(seat, partial);
        // Vanilla's VEHICLE attachment is a feet-to-seat offset. Rotate that offset with
        // the shell too; subtracting it along world Y made riders float beside walls.
        Vec3 attachment = passenger.getVehicleAttachmentPoint(this);
        Vec3 rotated = CentipedeInteraction.toWorld(attachment, pose).subtract(pose.position());
        return CentipedeInteraction.saddle(pose, seat).subtract(rotated);
    }

    public boolean mountSegment(Player player, int seat) {
        if (level().isClientSide() || !isAlive() || !player.isAlive() || player.isSpectator()
                || player.isSecondaryUseActive() || player.isPassenger()
                || !seatTable().claim(player.getUUID(), seat, chainSegmentCount())) return false;
        syncSeats();
        if (player.startRiding(this)) return true;
        seatTable().release(player.getUUID());
        syncSeats();
        return false;
    }

    public void receiveDriverFrame(Player sender, int surface, Vec3 heading) {
        if (level().isClientSide() || getControllingPassenger() != sender || !sender.isAlive() || !isAlive()
                || surface < 0 || surface >= SURFACES.length
                || !Double.isFinite(heading.x) || !Double.isFinite(heading.y) || !Double.isFinite(heading.z)
                || heading.lengthSqr() < .5 || heading.lengthSqr() > 1.5) return;
        Direction face = SURFACES[surface];
        // A rear passenger cannot send steering, and even the driver cannot invent a wall.
        if (face != Direction.DOWN && !touchingSurface(face)) return;
        if (face != attachedSurface()) lastSurfaceSwitchTick = tickCount;
        setAttachedSurface(face);
        surfaceContactGrace = CONTACT_GRACE_TICKS;
        driverHeading = heading.normalize();
        driverFrameTick = tickCount;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide() && seatIndex(passenger) < 0) {
            seatTable().claim(passenger.getUUID(), seatTable().firstFree(chainSegmentCount()), chainSegmentCount());
            syncSeats();
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        if (seatIndex(passenger) == 0) driverFrameTick = -100;
        super.removePassenger(passenger);
        if (!level().isClientSide()) {
            seatTable().release(passenger.getUUID());
            syncSeats();
        }
    }

    private CentipedeSeats seatTable() {
        String data = getEntityData().get(DATA_SEATS);
        if (!data.equals(cachedSeats)) { seats.decode(data); cachedSeats = data; }
        return seats;
    }

    private void syncSeats() {
        cachedSeats = seats.encode();
        getEntityData().set(DATA_SEATS, cachedSeats);
    }

    public int seatIndex(Entity passenger) { return seatTable().seatOf(passenger.getUUID()); }

    public float segmentGait(int index, float partial) { return bodyChain.initialized() ? bodyChain.gait(index, partial) : 0; }
    public float segmentSpeed(int index, float partial) { return bodyChain.initialized() ? bodyChain.speed(index, partial) : 0; }

    private Vec3 chainHeadCenter() {
        Vec3 normal = attachmentNormal();
        double halfHeight = getBbHeight() * 0.5D;
        double boxReach = (Math.abs(normal.x) + Math.abs(normal.z)) * getBbWidth() * 0.5D
                + Math.abs(normal.y) * halfHeight;
        return position().add(0, halfHeight, 0)
                .add(normal.scale(boxReach - CentipedeFrame.CLEARANCE));
    }

    public CentipedeChain.Pose chainPose(int index, float partialTick) {
        if (bodyChain.initialized()) return bodyChain.sample(index, partialTick);
        return new CentipedeChain.Pose(chainHeadCenter().subtract(surfaceForward().scale(index * CentipedeFrame.LINK_LENGTH)),
                attachmentNormal(), surfaceForward());
    }

    public Vec3 passengerNormal(Entity passenger, float partialTick) {
        return chainPose(Math.max(0, seatIndex(passenger)), partialTick).normal();
    }

    public Vec3 passengerForward(Entity passenger, float partialTick) {
        return chainPose(Math.max(0, seatIndex(passenger)), partialTick).forward();
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        resetFallDistance();
        return false;
    }

    private void updateSurfaceAfterMovement() {
        Direction current = attachedSurface();
        if (current != Direction.DOWN && tickCount - lastSurfaceSwitchTick < 6 && touchingSurface(current)) return;
        if (current == Direction.DOWN && !horizontalCollision) return;

        Direction transition = bestTransitionSurface(current);
        if (transition != null && transition != current) {
            attachTo(transition, current);
            return;
        }
        if (current != Direction.DOWN && touchingSurface(current)) {
            surfaceContactGrace = CONTACT_GRACE_TICKS;
            return;
        }
        if (current != Direction.DOWN) {
            Direction replacement = firstTouchingSurface();
            if (replacement != null) {
                attachTo(replacement, current);
                return;
            }
            // Only a short grace period for block seams; a missing wall restores gravity.
            if (surfaceContactGrace-- <= 0) detachFromSurface();
        }
    }

    private Direction bestTransitionSurface(Direction current) {
        // Keep the selected face through the overlap zone instead of ping-ponging between
        // the two touching walls. Losing support still permits an immediate replacement.
        if (tickCount - lastSurfaceSwitchTick < 6 && touchingSurface(current)) return null;
        if (current == Direction.DOWN && horizontalCollision) {
            Direction wall = null;
            double wallScore = .05;
            for (Direction candidate : Direction.Plane.HORIZONTAL) {
                if (!touchingSurface(candidate)) continue;
                double score = surfaceForward.dot(candidate.getUnitVec3());
                if (score > wallScore) {
                    wallScore = score;
                    wall = candidate;
                }
            }
            if (wall != null) return wall;
        }
        Vec3 motion = getDeltaMovement();
        Direction best = null;
        double bestScore = 0.025D;
        for (Direction candidate : SURFACES) {
            if (candidate == current || !touchingSurface(candidate)) continue;
            double score = motion.dot(candidate.getUnitVec3());
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private Direction firstTouchingSurface() {
        for (Direction candidate : SURFACES)
            if (touchingSurface(candidate)) return candidate;
        return null;
    }

    private boolean touchingSurface(Direction direction) {
        return !level().noCollision(this,
                getBoundingBox().move(direction.getUnitVec3().scale(CONTACT_PROBE)));
    }

    private void updateNearbySurface(Vec3 input) {
        if (isInWater() || isInLava() || tickCount - lastSurfaceSwitchTick < 6) return;
        Vec3 motion;
        if (getControllingPassenger() instanceof Player player) {
            Vec3 up = attachmentNormal().scale(-1);
            Vec3 forward = riderForward(player, up);
            motion = forward.scale(input.z).subtract(up.cross(forward).scale(input.x)).scale(RIDDEN_SPEED);
        } else motion = isNoAi() ? Vec3.ZERO : wildHeading.scale(wildSpeed);
        if (motion.lengthSqr() < .000225) return;
        var blocks = bodyCollision.collect(getBoundingBox().inflate(.85));
        var approach = CentipedeSurfaceProbe.ahead(getBoundingBox(), motion, attachedSurface(), blocks);
        if (approach == null)
            approach = CentipedeSurfaceProbe.aroundEdge(getBoundingBox(), motion, attachedSurface(), blocks);
        if (approach == null) return;
        // No speculative tilt or gravity changes: the new face must already be within
        // contact tolerance. Blend the pose only after this confirmed hand-off.
        if (approach.gap() <= .08 && touchingSurface(approach.face())) attachTo(approach.face(), attachedSurface());
    }

    private void attachTo(Direction next, Direction previous) {
        if (!touchingSurface(next)) return;
        Vec3 nextNormal = next.getUnitVec3();
        Vec3 projected = projectOntoSurface(surfaceForward, nextNormal);
        if (projected.lengthSqr() < 1.0E-5D) {
            // Rolling over a 90-degree edge continues away from the old surface.
            projected = projectOntoSurface(previous.getUnitVec3().scale(-1.0D), nextNormal);
        }
        if (projected.lengthSqr() < 1.0E-5D)
            projected = fallbackForward(nextNormal.scale(-1.0D));
        wildHeading = projected.normalize();
        // Momentum is transported gradually by blendAttachmentNormal, not snapped to the
        // final wall direction while the body is still facing the old surface.
        setAttachedSurface(next);
        lastSurfaceSwitchTick = tickCount;
        setNoGravity(next != Direction.DOWN);
        surfaceContactGrace = CONTACT_GRACE_TICKS;
        navigation.stop();
        resetFallDistance();
    }

    private void detachFromSurface() {
        setAttachedSurface(Direction.DOWN);
        setNoGravity(false);
        setDeltaMovement(getDeltaMovement().scale(0.72D));
        smoothedSurfaceMotion = smoothedSurfaceMotion.scale(0.72D);
        surfaceContactGrace = CONTACT_GRACE_TICKS;
        resetFallDistance();
    }

    private void blendAttachmentNormal() {
        Vec3 oldNormal = smoothedAttachmentNormal;
        Vec3 target = attachedSurface().getUnitVec3();
        Vec3 blended = smoothedAttachmentNormal.lerp(target, SURFACE_BLEND);
        smoothedAttachmentNormal = blended.lengthSqr() < 1.0E-6D ? target : blended.normalize();
        Quaternionf turn = new Quaternionf().rotationTo(vector(oldNormal), vector(smoothedAttachmentNormal));
        Vector3f heading = turn.transform(vector(surfaceForward));
        surfaceForward = new Vec3(heading.x, heading.y, heading.z);
        Vector3f motion = turn.transform(vector(smoothedSurfaceMotion));
        smoothedSurfaceMotion = new Vec3(motion.x, motion.y, motion.z);
    }

    private boolean usesSurfaceTravel() {
        // Visual settling after detachment must never keep the mount levitating.
        return attachedSurface() != Direction.DOWN;
    }

    /** Matches the camera transform: player yaw is evaluated on a flat plane, then tilted
     * onto the current blended surface. This keeps forward and strafe controls uninverted. */
    private static Vec3 riderForward(Player player, Vec3 surfaceUp) {
        Vec3 flat = Vec3.directionFromRotation(0.0F, player.getYRot());
        Quaternionf tilt = new Quaternionf().rotationTo(
                new Vector3f(0.0F, 1.0F, 0.0F), vector(surfaceUp));
        Vector3f transformed = tilt.transform(vector(flat));
        Vec3 forward = new Vec3(transformed.x, transformed.y, transformed.z);
        Vec3 normal = surfaceUp.scale(-1.0D);
        forward = projectOntoSurface(forward, normal);
        return forward.lengthSqr() < 1.0E-6D ? fallbackForward(surfaceUp) : forward.normalize();
    }

    private static Vector3f vector(Vec3 value) {
        return new Vector3f((float)value.x, (float)value.y, (float)value.z);
    }

    private void updateYaw(Vec3 tangent) {
        Vec3 horizontal = tangent.multiply(1.0D, 0.0D, 1.0D);
        if (horizontal.lengthSqr() < 1.0E-5D) return;
        float target = (float)(Mth.atan2(horizontal.z, horizontal.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.rotLerp(0.35F, getYRot(), target));
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
    }

    private static Vec3 projectOntoSurface(Vec3 vector, Vec3 normal) {
        return vector.subtract(normal.scale(vector.dot(normal)));
    }

    private static Vec3 fallbackForward(Vec3 up) {
        Vec3 axis = Math.abs(up.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        return up.cross(axis).normalize();
    }

    public Direction attachedSurface() {
        int ordinal = Mth.clamp(getEntityData().get(DATA_ATTACHED_SURFACE), 0, SURFACES.length - 1);
        return SURFACES[ordinal];
    }

    private void setAttachedSurface(Direction direction) {
        getEntityData().set(DATA_ATTACHED_SURFACE, direction.ordinal());
    }

    public Vec3 surfaceForward() {
        Vec3 normal = attachmentNormal();
        Vec3 motion = projectOntoSurface(smoothedSurfaceMotion, normal);
        return motion.lengthSqr() > 1.0E-5D ? motion.normalize() : surfaceForward;
    }

    public Vec3 attachmentNormal() {
        return smoothedAttachmentNormal;
    }

    public int chainSegmentCount() {
        return Mth.clamp(getEntityData().get(DATA_CHAIN_SEGMENTS),
                MIN_CHAIN_SEGMENTS, MAX_CHAIN_SEGMENTS);
    }

    public void setChainSegmentCount(int count) {
        getEntityData().set(DATA_CHAIN_SEGMENTS, Mth.clamp(count,
                MIN_CHAIN_SEGMENTS, MAX_CHAIN_SEGMENTS));
        if (!level().isClientSide()) for (Entity rider : List.copyOf(getPassengers()))
            if (seatIndex(rider) >= chainSegmentCount()) rider.stopRiding();
    }

    /** Wandering in the current surface plane, not ground navigation projected onto a wall. */
    private final class SurfaceWanderGoal extends Goal {
        private Vec3 wanted = new Vec3(0, 0, -1);
        private Vec3 lastPosition = Vec3.ZERO;
        private int decisionTicks, pauseTicks, runTicks, stuckTicks, hazardCooldown;

        SurfaceWanderGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() { return getControllingPassenger() == null && !isNoAi(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() {
            navigation.stop();
            wanted = wildHeading = surfaceForward();
            decisionTicks = 0;
            lastPosition = position();
        }
        @Override public void stop() { wildSpeed = 0; }

        @Override public void tick() {
            Vec3 normal = attachedSurface().getUnitVec3();
            wildHeading = CentipedeFrame.tangent(wildHeading, normal, surfaceForward);
            wanted = CentipedeFrame.tangent(wanted, normal, wildHeading);
            Vec3 right = wildHeading.cross(normal.scale(-1));
            if (hurtTime > 0) {
                runTicks = 100;
                pauseTicks = 0;
                LivingEntity attacker = getLastHurtByMob();
                if (attacker != null) wanted = CentipedeFrame.tangent(position().subtract(attacker.position()), normal, wildHeading);
            }
            if (--decisionTicks <= 0) {
                double turn = (random.nextDouble() - .5) * 1.6;
                wanted = wildHeading.scale(Math.cos(turn)).add(right.scale(Math.sin(turn)));
                decisionTicks = 65 + random.nextInt(90);
                if (runTicks == 0 && random.nextInt(4) == 0) runTicks = 45 + random.nextInt(60);
                else if (runTicks == 0 && attachedSurface() == Direction.DOWN && random.nextInt(4) == 0)
                    pauseTicks = 15 + random.nextInt(25);
            }

            double moved = position().distanceToSqr(lastPosition);
            lastPosition = position();
            stuckTicks = wildSpeed > .08 && moved < .0004 ? stuckTicks + 1 : 0;
            // Look ahead for unsupported edges. A solid face ahead is deliberately allowed:
            // the existing attachment solver will roll the head up onto that wall.
            Vec3 ahead = wildHeading.scale(.95);
            boolean supportedAhead = !level().noCollision(ScarletCentipedeEntity.this,
                    getBoundingBox().move(ahead).move(normal.scale(.8)));
            boolean faceAhead = !level().noCollision(ScarletCentipedeEntity.this, getBoundingBox().move(ahead));
            if (--hazardCooldown <= 0 && (stuckTicks > 18 || !supportedAhead && !faceAhead && onGround())) {
                double turn = random.nextBoolean() ? 1.25 : -1.25;
                wanted = wildHeading.scale(Math.cos(turn)).add(right.scale(Math.sin(turn)));
                hazardCooldown = 25;
                decisionTicks = 50;
                stuckTicks = 0;
                runTicks = 0;
            }
            wildHeading = CentipedeMotion.steer(wildHeading, wanted, normal, runTicks > 0 ? .085 : .05);
            wildSpeed = pauseTicks > 0 ? 0 : runTicks > 0 ? .28 : .18;
            if (pauseTicks > 0) pauseTicks--;
            if (runTicks > 0) runTicks--;
            if (isInWater() || isInLava()) { setSpeed((float)wildSpeed); updateYaw(wildHeading); }
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ScarletCentipedeEntity>("movement", 3, state -> {
            state.setControllerSpeed(0.35F + segmentSpeed(0, 1));
            return state.setAndContinue(WALK_ANIMATION);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
