package net.krodark.asterion.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.event.DeadStampede;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

/** The dead are Limbo ambience: procession, curiosity, panic, then the river. */
public final class WandererEntity extends PathfinderMob implements GeoEntity {
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(WandererEntity.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int stateTicks;
    private Vec3 destination;
    private Vec3 failedCover;
    private int failedCoverUntil;
    private double coverProgress = Double.MAX_VALUE;
    private int coverStallTicks;
    private int exposedTicks;
    private boolean gaitAnimationInitialized;

    public enum State { PURPOSEFUL, ROAMING, WATCHING, HIDING, DROWNING, STAMPEDE, SEEKING_COVER }
    public WandererEntity(EntityType<? extends WandererEntity> type, Level level) {
        super(type, level);
        xpReward = 5;
        setPathfindingMalus(PathType.WATER, -1F);
        setPathfindingMalus(PathType.WATER_BORDER, 8F);
    }
    public static AttributeSupplier.Builder createAttributes() { return createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, .20).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.KNOCKBACK_RESISTANCE, .15); }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) { return reason != EntitySpawnReason.NATURAL || level instanceof ServerLevel server && server.dimension().equals(Asterion.LIMBO_LEVEL) && super.checkSpawnRules(level, reason); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(STATE, State.ROAMING.ordinal()); }
    @Override protected void registerGoals() { }

    @Override public void tick() {
        super.tick(); if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        if (tickCount == 1 && random.nextFloat() < .58F) setState(State.PURPOSEFUL);
        stateTicks++;
        if (state() == State.DROWNING) { tickDrowning(); return; }
        if (isInWater() || getBlockZ() >= 18 && getY() < UnderworldTerrain.WATER_Y) { setState(State.DROWNING); navigation.stop(); return; }
        double front = DeadStampede.front(level);
        if (!Double.isNaN(front) && Math.abs(getZ() - front) < 100) {
            if (state() != State.STAMPEDE) setState(State.STAMPEDE);
            double targetZ = DeadStampede.gathering(level) ? front - 3 : front - 2 - Math.floorMod(getId(), 5) * 1.2;
            double targetX = UnderworldTerrain.riverCenter(targetZ) - 15 + (Math.floorMod(getId(), 7) - 3) * .9;
            if (tickCount % 8 == 0 || navigation.isDone()) navigation.moveTo(targetX, getY(), targetZ, 1.35);
            return;
        }
        if (state() == State.STAMPEDE) setState(State.PURPOSEFUL);
        if (scatterFromCharge(level)) return;
        switch (state()) { case PURPOSEFUL -> purposeful(level); case ROAMING -> roaming(level); case WATCHING -> watching(level); case SEEKING_COVER -> seekingCover(level); case HIDING -> hiding(level); case STAMPEDE -> stampede(); default -> { } }
    }
    private void purposeful(ServerLevel level) {
        double z = getZ() + 9;
        double towardWater = Math.clamp((z - 12D) / 30D, 0D, 1D);
        double x = UnderworldTerrain.riverCenter(z) - 15 + towardWater * 13;
        if (navigation.isDone() || tickCount % 30 == 0) navigation.moveTo(x, getY(), z, .68);
        navigation.setSpeedModifier(.68 * gaitSpeed());
        for (WandererEntity dead : level.getEntitiesOfClass(WandererEntity.class, getBoundingBox().inflate(3.2), other -> other != this && other.state() == State.ROAMING)) if (random.nextInt(90) == 0) { dead.setState(State.PURPOSEFUL); dead.playSound(SoundEvents.HUSK_AMBIENT, .55F, .75F); }
    }
    private void roaming(ServerLevel level) {
        Player viewer = level.getNearestPlayer(this, 28);
        if (stateTicks > 90 && random.nextInt(100) == 0 && viewer != null) {
            if (random.nextFloat() < .75F && findCover(level, viewer)) return;
            setState(State.WATCHING);
            return;
        }
        if (navigation.isDone() || tickCount % 45 == 0) { double z = getZ() + (random.nextDouble() - .5) * 9, x = UnderworldTerrain.riverCenter(z) - 15; navigation.moveTo(x + (random.nextBoolean() ? 1 : -1) * (3 + random.nextDouble() * 4), getY(), z, .45); }
        navigation.setSpeedModifier(.45 * gaitSpeed());
    }
    private void watching(ServerLevel level) {
        Player player = level.getNearestPlayer(this, 32);
        if (player == null || stateTicks > 100) { setState(State.ROAMING); return; }
        navigation.stop();
        getLookControl().setLookAt(player, 12, 12);
        if (stateTicks == 32 && random.nextBoolean()) findCover(level, player);
    }
    private void seekingCover(ServerLevel level) {
        Player viewer = level.getNearestPlayer(this, 32);
        if (viewer == null || destination == null) { setState(State.ROAMING); return; }
        double remaining = distanceToSqr(destination);
        if (remaining < 1.35 && Math.abs(getY() - destination.y) < .8) {
            navigation.stop();
            if (isConcealed(level, viewer, position())) { setState(State.HIDING); return; }
            if (stateTicks % 12 == 0) {
                rejectCover();
                if (!findCover(level, viewer)) setState(State.WATCHING);
            }
            return;
        }
        if (remaining < coverProgress - .08) { coverProgress = remaining; coverStallTicks = 0; }
        else coverStallTicks++;
        if (coverStallTicks > 30 || navigation.isDone() && stateTicks > 12) {
            rejectCover();
            if (!findCover(level, viewer)) setState(State.ROAMING);
            return;
        }
        navigation.setSpeedModifier(remaining < 9 ? 1.25 : 1.7);
    }
    private void hiding(ServerLevel level) {
        Player viewer = level.getNearestPlayer(this, 32);
        if (viewer == null) { setState(State.ROAMING); return; }
        navigation.stop();
        getLookControl().setLookAt(viewer, 9, 9);
        if (tickCount % 5 == 0) exposedTicks = isConcealed(level, viewer, position()) ? 0 : exposedTicks + 5;
        if (exposedTicks >= 15 || stateTicks > 180) {
            if (!findCover(level, viewer)) setState(State.WATCHING);
        }
    }
    private void stampede() { }
    private boolean scatterFromCharge(ServerLevel level) {
        MinotaurEntity minotaur = level.getNearestEntity(MinotaurEntity.class, net.minecraft.world.entity.ai.targeting.TargetingConditions.forNonCombat().range(32), this, getX(), getY(), getZ(), getBoundingBox().inflate(32));
        if (minotaur == null || !minotaur.isSpineCharging()) return false; Vec3 velocity = minotaur.getDeltaMovement().multiply(1, 0, 1); if (velocity.lengthSqr() < .02) return false;
        Vec3 origin = minotaur.position(), direction = velocity.normalize(), lane = origin.add(direction.scale(28)); if (distanceToLane(origin, lane, position()) > 5.5) return false;
        if (state() == State.SEEKING_COVER) return true;
        Vec3 side = new Vec3(-direction.z, 0, direction.x);
        if (side.dot(position().subtract(origin)) < 0) side = side.scale(-1);
        Player viewer = level.getNearestPlayer(this, 32);
        if (viewer != null && findCover(level, viewer)) return true;
        navigation.moveTo(getX() + side.x * 8, getY(), getZ() + side.z * 8, 1.1);
        return true;
    }
    private boolean findCover(ServerLevel level, Player viewer) {
        Vec3 best = null;
        Path bestPath = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        Vec3 look = viewer.getLookAngle();
        for (int attempt = 0; attempt < 36; attempt++) {
            double z = getZ() + (random.nextDouble() - .5D) * 26D;
            double pathX = UnderworldTerrain.riverCenter(z) - 15D;
            double x = pathX + (random.nextBoolean() ? -1 : 1) * (5D + random.nextDouble() * 11D);
            BlockPos feet = safeFeet(level, x, z);
            if (feet == null) continue;
            Vec3 candidate = Vec3.atBottomCenterOf(feet);
            if (tickCount < failedCoverUntil && failedCover != null
                    && candidate.distanceToSqr(failedCover) < 9) continue;
            double distance = candidate.distanceTo(viewer.position());
            if (distance < 6 || distance > 28) continue;
            if (!isConcealed(level, viewer, candidate)) continue;
            boolean eyesVisible = !blocked(level, viewer, candidate.add(0, 1.55D, 0));
            Vec3 toward = candidate.subtract(viewer.position()).normalize();
            double view = look.x * toward.x + look.z * toward.z;
            double score = (eyesVisible ? 3 : 1) + (view < .6 && view > -.35 ? 2 : 0)
                    - Math.abs(distance - 14) * .08 - candidate.distanceTo(position()) * .04;
            if (score <= bestScore) continue;
            Path path = navigation.createPath(feet, 0);
            if (!dryPath(level, path)) continue;
            best = candidate; bestPath = path; bestScore = score;
        }
        if (best == null) return false;
        destination = best;
        coverProgress = distanceToSqr(best);
        coverStallTicks = exposedTicks = 0;
        setState(State.SEEKING_COVER);
        navigation.moveTo(bestPath, 1.7);
        return true;
    }
    private void rejectCover() {
        failedCover = destination;
        failedCoverUntil = tickCount + 100;
    }
    private BlockPos safeFeet(ServerLevel level, double x, double z) {
        BlockPos origin = BlockPos.containing(x, getY(), z);
        for (int offset : new int[]{0, -1, 1, -2, 2}) {
            BlockPos feet = origin.offset(0, offset, 0);
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                    || !level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()
                    || !level.getBlockState(feet.below()).isSolidRender()) continue;
            Vec3 candidate = Vec3.atBottomCenterOf(feet);
            if (level.noCollision(this, getBoundingBox().move(candidate.subtract(position())))) return feet;
        }
        return null;
    }
    private boolean dryPath(ServerLevel level, Path path) {
        if (path == null || !path.canReach()) return false;
        int climbs = 0;
        BlockPos previous = null;
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos node = path.getNode(i).asBlockPos();
            if (!level.getFluidState(node).isEmpty() || !level.getFluidState(node.below()).isEmpty()) return false;
            if (previous != null && node.getY() > previous.getY() && ++climbs > 2) return false;
            previous = node;
        }
        return true;
    }
    private boolean blocked(ServerLevel level, Player viewer, Vec3 target) {
        return level.clip(new ClipContext(viewer.getEyePosition(), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() == HitResult.Type.BLOCK;
    }
    private boolean isConcealed(ServerLevel level, Player viewer, Vec3 feet) {
        return blocked(level, viewer, feet.add(0, .65, 0))
                && blocked(level, viewer, feet.add(0, 1.12, 0));
    }
    private void tickDrowning() { navigation.stop(); setDeltaMovement(getDeltaMovement().multiply(.82, .7, .82).add(0, -.025, .035)); if (stateTicks == 1) playSound(SoundEvents.PLAYER_SPLASH, .65F, .6F); if (stateTicks >= 300) discard(); }
    /** The model's two-second loop plants a foot at 0, 1 and 2 seconds. Ease down at each contact. */
    private int gaitPhase() {
        long stagger = getUUID().getLeastSignificantBits() ^ getUUID().getMostSignificantBits();
        return (int)Math.floorMod(level().getGameTime() + stagger, 40L);
    }
    private double gaitSpeed() {
        int phase = gaitPhase();
        int fromPlant = Math.min(Math.abs(phase - 20), Math.min(phase, 40 - phase));
        return .45D + .55D * Math.min(1D, fromPlant / 7D);
    }
    private static double distanceToLane(Vec3 from, Vec3 to, Vec3 point) { Vec3 lane = to.subtract(from); double length = lane.lengthSqr(); return length < .001 ? point.distanceTo(from) : point.distanceTo(from.add(lane.scale(Math.clamp(point.subtract(from).dot(lane) / length, 0, 1)))); }
    public State state() { return State.values()[entityData.get(STATE)]; }
    public boolean isWatching() { return state() == State.WATCHING; }
    public void debugState(State requested, ServerLevel level, Player viewer) {
        if (requested == State.HIDING || requested == State.SEEKING_COVER) {
            if (!findCover(level, viewer)) setState(State.WATCHING);
            return;
        }
        setState(requested);
    }
    private void setState(State state) {
        State previous = state();
        entityData.set(STATE, state.ordinal());
        stateTicks = 0;
        if ((previous == State.SEEKING_COVER || previous == State.HIDING)
                && state != State.SEEKING_COVER && state != State.HIDING) navigation.stop();
        if (state != State.SEEKING_COVER && state != State.HIDING) destination = null;
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<WandererEntity>("movement", 0, state -> {
            // Keep the loop running through brief pathfinding pauses; restarting it at each
            // MOVING packet made the legs and mask visibly snap between poses.
            if (state() == State.DROWNING || state() == State.HIDING || state() == State.WATCHING) {
                gaitAnimationInitialized = false;
                return PlayState.STOP;
            }
            PlayState result = state.setAndContinue(WALK);
            if (!gaitAnimationInitialized) {
                state.controller().setAnimationTime(gaitPhase() / 20D);
                gaitAnimationInitialized = true;
            }
            return result;
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
