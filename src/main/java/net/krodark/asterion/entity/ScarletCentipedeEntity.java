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
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A directly controllable mount that keeps its feet attached across surface corners. */
public final class ScarletCentipedeEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final EntityDataAccessor<Integer> DATA_ATTACHED_SURFACE = SynchedEntityData.defineId(
            ScarletCentipedeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CHAIN_SEGMENTS = SynchedEntityData.defineId(
            ScarletCentipedeEntity.class, EntityDataSerializers.INT);
    private static final Direction[] SURFACES = Direction.values();
    private static final int DEFAULT_CHAIN_SEGMENTS = 7;
    private static final int MIN_CHAIN_SEGMENTS = 3;
    private static final int MAX_CHAIN_SEGMENTS = 32;
    private static final int CONTACT_GRACE_TICKS = 10;
    private static final double CONTACT_PROBE = 0.34D;
    private static final double ADHESION = 0.145D;
    private static final double RIDDEN_SPEED = 0.31D;
    private static final double WILD_WALL_SPEED = 0.12D;
    private static final double SURFACE_BLEND = 0.22D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private Vec3 surfaceForward = new Vec3(0.0D, 0.0D, -1.0D);
    private Vec3 smoothedAttachmentNormal = Direction.DOWN.getUnitVec3();
    private Vec3 smoothedSurfaceMotion = Vec3.ZERO;
    private int wildTurnTicks;
    private int surfaceContactGrace = CONTACT_GRACE_TICKS;

    public ScarletCentipedeEntity(EntityType<? extends ScarletCentipedeEntity> type, Level level) {
        super(type, level);
        xpReward = 4;
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
        return super.checkSpawnRules(level, reason);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ATTACHED_SURFACE, Direction.DOWN.ordinal());
        builder.define(DATA_CHAIN_SEGMENTS, DEFAULT_CHAIN_SEGMENTS);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("chain_segments", chainSegmentCount());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        setChainSegmentCount(input.getIntOr("chain_segments", DEFAULT_CHAIN_SEGMENTS));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new RandomStrollGoal(this, 0.85D, 28));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        if (isVehicle()) navigation.stop();
        super.tick();
        updateSurfaceAfterMovement();
        blendAttachmentNormal();
        if (!usesSurfaceTravel()) setNoGravity(false);
        else {
            setNoGravity(true);
            resetFallDistance();
        }
    }

    @Override
    public void travel(Vec3 input) {
        Direction surface = attachedSurface();
        if (!usesSurfaceTravel()) {
            if (input.x * input.x + input.z * input.z > 1.0E-4D) {
                Vec3 facing = Vec3.directionFromRotation(0.0F, getYRot());
                surfaceForward = input.z < 0.0D ? facing.scale(-1.0D) : facing;
            }
            super.travel(input);
            smoothedSurfaceMotion = getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
            return;
        }

        setNoGravity(true);
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
            tangent = forward.scale(input.z).add(left.scale(input.x));
            speed = RIDDEN_SPEED * Math.min(1.0D, tangent.length());
            if (tangent.lengthSqr() > 1.0E-5D) {
                tangent = tangent.normalize();
                surfaceForward = tangent;
            } else tangent = Vec3.ZERO;
        } else {
            if (--wildTurnTicks <= 0) {
                double turn = (random.nextDouble() - 0.5D) * 0.9D;
                Vec3 right = forward.cross(up).normalize();
                surfaceForward = forward.scale(Math.cos(turn)).add(right.scale(Math.sin(turn))).normalize();
                wildTurnTicks = 35 + random.nextInt(55);
            }
            tangent = surfaceForward;
            speed = WILD_WALL_SPEED;
        }

        // Tangent movement uses the blended plane while adhesion targets the real block face.
        // During a corner transition this makes the in-between pose behave like a diagonal wall.
        Vec3 desiredTangent = tangent.scale(speed);
        double response = tangent.lengthSqr() > 1.0E-5D ? 0.24D : 0.34D;
        smoothedSurfaceMotion = smoothedSurfaceMotion.lerp(desiredTangent, response);
        smoothedSurfaceMotion = projectOntoSurface(smoothedSurfaceMotion, normal);
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
            yRotO = getYRot();
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
        Entity passenger = getFirstPassenger();
        return passenger instanceof Player player ? player : super.getControllingPassenger();
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !isVehicle() && passenger instanceof Player;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isVehicle() && !player.isSecondaryUseActive()) {
            if (!level().isClientSide()) player.startRiding(this);
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        Vec3 up = attachmentNormal().scale(-1.0D);
        Vec3 center = new Vec3(0.0D, getBbHeight() * 0.48D, 0.0D);
        // The rider belongs on the head; future models can lengthen the chain without moving the seat.
        return center.add(up.scale(0.58D)).add(surfaceForward().scale(0.78D));
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        resetFallDistance();
        return false;
    }

    private void updateSurfaceAfterMovement() {
        Direction current = attachedSurface();
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
            // Convex block corners can lose collision contact for a handful of ticks. Keep pulling
            // toward the last face while the wider probe searches for the next surface.
            if (surfaceContactGrace-- <= 0) detachFromSurface();
        }
    }

    private Direction bestTransitionSurface(Direction current) {
        if (current == Direction.DOWN && horizontalCollision) {
            Direction wall = null;
            double wallScore = -Double.MAX_VALUE;
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

    private void attachTo(Direction next, Direction previous) {
        Vec3 nextNormal = next.getUnitVec3();
        Vec3 projected = projectOntoSurface(surfaceForward, nextNormal);
        if (projected.lengthSqr() < 1.0E-5D) {
            // Rolling over a 90-degree edge continues away from the old surface.
            projected = projectOntoSurface(previous.getUnitVec3().scale(-1.0D), nextNormal);
        }
        if (projected.lengthSqr() < 1.0E-5D)
            projected = fallbackForward(nextNormal.scale(-1.0D));
        surfaceForward = projected.normalize();
        setAttachedSurface(next);
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
        Vec3 target = attachedSurface().getUnitVec3();
        Vec3 blended = smoothedAttachmentNormal.lerp(target, SURFACE_BLEND);
        smoothedAttachmentNormal = blended.lengthSqr() < 1.0E-6D ? target : blended.normalize();
    }

    private boolean usesSurfaceTravel() {
        return attachedSurface() != Direction.DOWN
                || smoothedAttachmentNormal.distanceToSqr(Direction.DOWN.getUnitVec3()) > 0.0025D;
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
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ScarletCentipedeEntity>("movement", 3, state -> {
            boolean moving = getDeltaMovement().lengthSqr() > 0.001D;
            state.setControllerSpeed(isVehicle() ? 1.35F : 0.9F);
            return state.setAndContinue(moving ? WALK_ANIMATION : IDLE_ANIMATION);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
