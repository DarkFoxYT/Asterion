package net.krodark.asterion.update.underworld.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.entity.BugSurfaces;
import net.krodark.asterion.entity.CentipedeSurfaceProbe;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;

import java.util.UUID;
import net.krodark.asterion.update.underworld.WebPatchGenerator;
import net.krodark.asterion.update.underworld.LimboWebSystem;

/** One territorial spider per nest. All eleven encounter modes share one server state machine. */
public final class LimboSpiderEntity extends PathfinderMob implements GeoEntity {
    public enum State { HANGING, WAITING, MIMICKING, WANDERING_CAMOUFLAGED, WANDERING,
        STALKING_CAMOUFLAGED, STALKING, HUNTING, ATTACKING, FLEEING_HURT, FLEEING_SEEN }
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(
            LimboSpiderEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SURFACE = SynchedEntityData.defineId(
            LimboSpiderEntity.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos nest;
    private UUID huntTarget;
    private int stateTicks, approachTicks, attackCooldown, unseenTicks;
    private double previousDistance = Double.POSITIVE_INFINITY;
    private Vec3 surfaceGoal;
    private final java.util.ArrayDeque<SpiderSurfaceRoute.Point> surfaceRoute=new java.util.ArrayDeque<>();
    private int routeRetry;
    private Vec3 perch;
    private Vec3 patrolGoal;
    private Vec3 escapeGoal;
    private boolean homebound;
    private int surfaceGrace;
    private double crawlSpeed = 1;
    private Vec3 crawlHeading = new Vec3(0,0,1);
    private int turnLock, patrolUntil, pauseUntil, restDuration, roamDuration, paceUntil;
    private double pace = 1;
    private final int flank;
    private long silkKey;
    private int silkEdge = -1;
    private SpiderSupportSurface.Plane smoothSupport;
    private boolean supportMotion;
    private static final EntityDataAccessor<Float> NORMAL_X = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> NORMAL_Y = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> NORMAL_Z = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SUPPORT_DISTANCE = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    public Vec3 attachmentNormal() { return new Vec3(entityData.get(NORMAL_X),entityData.get(NORMAL_Y),entityData.get(NORMAL_Z)).normalize(); }
    public boolean hasSurfaceSupport() { return entityData.get(SUPPORT_DISTANCE) > 0; }
    public boolean hasSmoothSupport() { return hasSurfaceSupport() && attachmentNormal().dot(attachedSurface().getUnitVec3()) < .995; }
    public Vec3 supportPoint() { return getBoundingBox().getCenter().add(attachmentNormal().scale(entityData.get(SUPPORT_DISTANCE))); }
    private void syncSupport() {
        Vec3 normal = smoothSupport == null ? attachedSurface().getUnitVec3() : smoothSupport.outward().scale(-1);
        entityData.set(NORMAL_X,(float)normal.x); entityData.set(NORMAL_Y,(float)normal.y); entityData.set(NORMAL_Z,(float)normal.z);
        entityData.set(SUPPORT_DISTANCE,smoothSupport == null ? 0F : (float)Math.max(.01,smoothSupport.distance(getBoundingBox().getCenter())));
    }
    private static final EntityDataAccessor<Boolean> WEB = SynchedEntityData.defineId(
            LimboSpiderEntity.class, EntityDataSerializers.BOOLEAN);
    public boolean onWeb() { return entityData.get(WEB); }
    public Vec3 debugGoal() {
        return surfaceRoute.isEmpty()?surfaceGoal:surfaceRoute.peekFirst().center().add(0,-getBbHeight()*.5,0);
    }
    /** NoAI freezes decisions, while physical support and client leg IK remain active. */
    public void configureTest(Direction face,boolean noAi) {
        nest=blockPosition(); perch=face==Direction.UP?position():null;
        entityData.set(STATE,State.WANDERING.ordinal()); entityData.set(SURFACE,face.ordinal());
        setNoAi(noAi); setPersistenceRequired(); setInvisible(false);
        getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
        refreshSupport();
        setNoGravity(smoothSupport!=null);
    }
    private void refreshSupport() {
        Direction face=attachedSurface();
        if (isInWater() || isInLava() || onWeb()) smoothSupport=null;
        else {
            smoothSupport=SpiderSupportSurface.find(level(),getBoundingBox(),face);
            if(smoothSupport==null)smoothSupport=SpiderSupportSurface.contact(level(),getBoundingBox(),face);
        }
        supportMotion=smoothSupport!=null && (face!=Direction.DOWN || isNoAi()
                || smoothSupport.outward().dot(face.getUnitVec3().scale(-1))<.995);
        syncSupport();
    }

    public LimboSpiderEntity(net.minecraft.world.entity.EntityType<? extends LimboSpiderEntity> type, Level level) {
        super(type, level);
        xpReward = 20;
        setNoGravity(true);
        flank = random.nextBoolean() ? 1 : -1;
        restDuration = 100 + random.nextInt(220);
        roamDuration = 260 + random.nextInt(360);
    }
    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH, 60)
                .add(Attributes.MOVEMENT_SPEED, .42).add(Attributes.FOLLOW_RANGE, 40)
                .add(Attributes.ATTACK_DAMAGE, 7).add(Attributes.KNOCKBACK_RESISTANCE, .35);
    }
    @Override public boolean checkSpawnRules(LevelAccessor level, EntitySpawnReason reason) {
        return reason != EntitySpawnReason.NATURAL;
    }
    @Override protected void registerGoals() { }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(STATE, State.HANGING.ordinal());
        data.define(SURFACE, Direction.DOWN.ordinal());
        data.define(WEB, false);
        data.define(NORMAL_X,0F); data.define(NORMAL_Y,-1F); data.define(NORMAL_Z,0F); data.define(SUPPORT_DISTANCE,0F);
    }
    public State state() { return State.values()[Math.clamp(entityData.get(STATE),0,State.values().length-1)]; }
    public Direction attachedSurface() {
        return Direction.values()[Math.clamp(entityData.get(SURFACE),0,Direction.values().length-1)];
    }
    public void setNest(BlockPos pos) {
        nest = pos.immutable();
        perch = findPerch();
        if (perch != null) {
            setPos(perch.x,perch.y,perch.z);
            entityData.set(SURFACE,Direction.UP.ordinal());
            setNoGravity(true);
        } else {
            setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
            state(State.WANDERING);
        }
    }
    private Vec3 findPerch() {
        if (nest == null) return null;
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz)) != radius) continue;
                double x = nest.getX()+dx+.5, z = nest.getZ()+dz+.5;
                for (int y = nest.getY()+10; y <= nest.getY()+27; y++) {
                    double foot = y-getBbHeight()-.14;
                    AABB box = getBoundingBox().move(x-getX(),foot-getY(),z-getZ());
                    if (level().noCollision(this,box)
                            && BugSurfaces.touches(level(),box.move(0,.30,0)))
                        return new Vec3(x,foot,z);
                }
            }
        }
        return null;
    }
    private void state(State next) {
        if (state() == next) return;
        entityData.set(STATE,next.ordinal()); stateTicks = 0;
        if (next == State.HANGING) restDuration = 100 + random.nextInt(220);
        if (next == State.FLEEING_SEEN) { unseenTicks = 0; escapeGoal = null; }
        boolean hidden = next == State.WANDERING_CAMOUFLAGED || next == State.STALKING_CAMOUFLAGED;
        setInvisible(hidden);
        boolean perched = next == State.HANGING || next == State.WAITING;
        setNoGravity(perched || attachedSurface() != Direction.DOWN || onWeb());
        if (perched) {
            if (perch != null && position().distanceToSqr(perch) < 2
                    && touching(Direction.UP)) entityData.set(SURFACE,Direction.UP.ordinal());
            getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
        } else if (attachedSurface() != Direction.DOWN && !onWeb() && !touching(attachedSurface())) detach();
    }
    public void hunt(Player player) {
        if (!isAlive() || state() == State.FLEEING_HURT) return;
        huntTarget = player.getUUID(); state(State.HUNTING);
        playSound(SoundEvents.SPIDER_AMBIENT,1.3F,.6F);
    }
    private Player prey(ServerLevel level) {
        Player marked = huntTarget == null ? null : level.getServer().getPlayerList().getPlayer(huntTarget);
        if (marked != null && marked.level() == level && marked.isAlive() && !marked.isSpectator()
                && !marked.getAbilities().instabuild && distanceToSqr(marked) < 60*60) return marked;
        Player nearest = level.getNearestPlayer(this,42);
        return nearest != null && !nearest.isSpectator() && !nearest.getAbilities().instabuild ? nearest : null;
    }
    private boolean seenBy(Player player) {
        Vec3 toward = getEyePosition().subtract(player.getEyePosition());
        return toward.lengthSqr() > .001 && toward.lengthSqr() < 26*26
                && player.getLookAngle().dot(toward.normalize()) > .82
                && getSensing().hasLineOfSight(player);
    }
    private void move(Vec3 target,double speed) {
        surfaceGoal = target;
        crawlSpeed = speed;
        if (attachedSurface() != Direction.DOWN) return;
        if (stateTicks % 8 == 0 || getNavigation().isDone() || getNavigation().isStuck())
            getNavigation().moveTo(target.x,target.y,target.z,speed);
        if (getNavigation().isDone() && onGround()) {
            Vec3 direct = target.subtract(position()).multiply(1,0,1);
            if (direct.lengthSqr() > .25) {
                direct = direct.normalize().scale(.25 * speed);
                setDeltaMovement(direct.x,getDeltaMovement().y,direct.z);
            }
        }
    }
    private boolean touching(Direction face) {
        return face==attachedSurface() && (smoothSupport!=null || !surfaceRoute.isEmpty())
                || BugSurfaces.touches(level(),getBoundingBox().move(face.getUnitVec3().scale(.36)));
    }
    @Override public void travel(Vec3 input) {
        if(!level().isClientSide() && !surfaceRoute.isEmpty() && !isNoAi()) {
            var blocks=BugSurfaces.collect(level(),getBoundingBox().inflate(1.5));
            if(SpiderSurfaceRoute.support(blocks,getBoundingBox(),attachedSurface())==null
                    || SpiderSurfaceRoute.support(blocks,getBoundingBox().move(getDeltaMovement()),attachedSurface())==null) {
                detach();super.travel(input);return;
            }
            // Corner routes have a normal component; projecting onto the old
            // ceiling would erase the climb around a convex ledge.
            move(MoverType.SELF,getDeltaMovement());resetFallDistance();return;
        }
        if(!level().isClientSide()) {
            refreshSupport();
            if(attachedSurface()!=Direction.DOWN && !onWeb() && !touching(attachedSurface()))detach();
        }
        if (!level().isClientSide() && supportMotion && smoothSupport != null && isAlive() && !isInWater() && !isInLava()) {
            Vec3 motion = smoothSupport.tangent(getDeltaMovement());
            Vec3 correction = smoothSupport.correction(getBoundingBox(),motion);
            // First leave the stair recess, then travel along the outer envelope.
            // Both moves retain vanilla collision with unrelated obstacles/entities.
            double lift = correction.dot(smoothSupport.outward());
            if (lift > .002) move(MoverType.SELF,correction.scale(Math.min(1,.18/Math.max(.001,lift))));
            if (lift <= .18) move(MoverType.SELF,motion);
            if (lift < -.002) move(MoverType.SELF,correction.scale(Math.min(1,.12/-lift)));
            resetFallDistance();
            // Collision resolution may zero components of delta movement. Keep
            // adhesion separate so those contact impulses never become facing input.
            setDeltaMovement(motion);
            return;
        }
        if (!level().isClientSide() && isAlive() && isNoGravity() && !isInWater() && !isInLava()
                && (attachedSurface() != Direction.DOWN || onWeb())) {
            // Surface velocity is already in world space. Ground acceleration and
            // friction must not compete with the crawler's vertical movement.
            move(MoverType.SELF, getDeltaMovement());
            resetFallDistance();
        } else super.travel(input);
    }
    private void attach(Direction face) {
        Direction old = attachedSurface();
        if (old != face) {
            smoothSupport = null;
            crawlHeading = SpiderSurfaceMotion.cornerHeading(old, face, crawlHeading);
            entityData.set(SURFACE,face.ordinal());
            turnLock = 10;


        }
        surfaceGrace = 6;
        setNoGravity(face != Direction.DOWN);
        getNavigation().stop();
        syncSupport();
    }
    private void crawl() {
        State mode = state();
        refreshSupport();
        if (mode == State.HANGING || mode == State.WAITING) {
            surfaceRoute.clear();
            setNoGravity(smoothSupport!=null); setDeltaMovement(Vec3.ZERO); return;
        }
        Direction surface = attachedSurface();
        Vec3 goal = surfaceGoal;
        if(!surfaceRoute.isEmpty()) {
            if(goal!=null && !isInWater() && !isInLava() && followSurfaceRoute())return;
            surfaceRoute.clear();
        }
        // Prefer the solid face we already grip. Silk can take over only off that
        // face, preventing dense webs from flipping a wall crawler's normal.
        if (goal != null && !isInWater() && !isInLava()
                && (smoothSupport == null || !supportMotion)
                && (onWeb() || surface == Direction.DOWN || !touching(surface)) && crawlWeb(goal)) return;
        entityData.set(WEB, false);
        silkEdge = -1;
        if (supportMotion && smoothSupport != null) {
            // Keep corner detection active on flat walls too; a fitted contact
            // plane must not swallow the wall-to-ceiling transition.
            if (surface.getAxis().isHorizontal() && goal!=null && goal.y>getY()+.5 && touching(Direction.UP)) {
                attach(Direction.UP); refreshSupport(); surface=attachedSurface();
                if(smoothSupport==null)return;
            }
            Vec3 desired = goal == null ? Vec3.ZERO : goal.subtract(position());
            Vec3 tangent = smoothSupport.tangent(desired);
            getNavigation().stop(); setNoGravity(true); resetFallDistance();
            if (tangent.lengthSqr() > .16 && goal != null) {
                crawlHeading = tangent.normalize();
                Vec3 step=crawlHeading.scale(.32*crawlSpeed*pace);
                if(surface!=Direction.DOWN) {
                    step=surfaceStep(step,surface);
                    if(!surfaceRoute.isEmpty() && followSurfaceRoute())return;
                }
                if(step.lengthSqr()>.001)crawlHeading=step.normalize();
                setDeltaMovement(step);
            } else setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (surface == Direction.DOWN) {
            setNoGravity(false);
            if (goal == null || isInWater() || isInLava()) return;
            Vec3 toward = goal.subtract(position());
            if (turnLock == 0 && (horizontalCollision || getNavigation().isStuck()) && toward.horizontalDistanceSqr() > 1) {
                Direction best = null;
                double score = .15;
                for (Direction face : Direction.Plane.HORIZONTAL) {
                    if (!touching(face)) continue;
                    double candidate = face.getUnitVec3().dot(toward.normalize());
                    if (candidate > score) { score = candidate; best = face; }
                }
                if (best != null) {
                    attach(best);
                    crawlHeading = SpiderSurfaceMotion.heading(best,toward,new Vec3(0,1,0),false);
                    setDeltaMovement(crawlHeading.scale(.28).add(best.getUnitVec3().scale(.04)));
                } else if (onGround() && tickCount % 12 == 0
                        && level().noCollision(this,getBoundingBox().move(0,.7,0))) {
                    // Low rubble and stair lips can block path nodes in narrow cave mouths.
                    setDeltaMovement(getDeltaMovement().add(0,.34,0));
                }
            }
            return;
        }
        if (goal == null && touching(surface)) {
            setNoGravity(true);
            setDeltaMovement(surface.getUnitVec3().scale(.04));
            return;
        }
        if (goal == null || isInWater() || isInLava()) {
            detach(); return;
        }
        // Grace must not propel the crawler upward after its wall ends.
        // A planned corner supplies validated support; ordinary crawling does not.
        if (touching(surface)) surfaceGrace = 8;
        else { detach(); return; }
        Vec3 normal = surface.getUnitVec3();
        Vec3 desired = goal.subtract(position());
        if (turnLock == 0
                && SpiderSurfaceMotion.tangent(surface,desired).lengthSqr() < .36) {
            // Arrival is measured in the support plane: prey may be far below
            // a ceiling spider. Do not overshoot and reverse every other tick.
            getNavigation().stop(); setNoGravity(true);
            setDeltaMovement(normal.scale(.04));
            return;
        }
        Vec3 tangent = SpiderSurfaceMotion.heading(surface,desired,crawlHeading,false);
        var nearby = BugSurfaces.collect(level(),getBoundingBox().inflate(.8));
        var corner = CentipedeSurfaceProbe.ahead(getBoundingBox(),tangent.scale(.23),surface,nearby);
        if (corner == null) corner = CentipedeSurfaceProbe.aroundEdge(getBoundingBox(),tangent.scale(.23),surface,nearby);
        if (turnLock == 0) {
            Direction next = surface;
            if (surface.getAxis().isHorizontal() && tangent.y > .25 && touching(Direction.UP)) next = Direction.UP;
            else if (corner != null && corner.face() != surface && touching(corner.face())) next = corner.face();
            if (next != surface) {
                attach(next);
                surface = next;
                tangent = crawlHeading;
                normal = surface.getUnitVec3();
            }
        }
        getNavigation().stop();
        setNoGravity(true);
        resetFallDistance();
        // A corner changes the tangent plane too. Never push along the old plane.
        tangent = tangent.subtract(normal.scale(tangent.dot(normal)));
        if (tangent.lengthSqr() < .01) {
            tangent = desired.subtract(normal.scale(desired.dot(normal)));
        }
        tangent = tangent.normalize();
        crawlHeading = tangent;
        double speed = .32 * crawlSpeed * pace;
        setDeltaMovement(tangent.scale(speed).add(normal.scale(.06)));
        if (tangent.horizontalDistanceSqr() > .015) {
            float yaw = (float)(Mth.atan2(tangent.z,tangent.x)*Mth.RAD_TO_DEG)-90;
            setYRot(Mth.rotLerp(.28F,getYRot(),yaw));
            setYBodyRot(getYRot());
        }
    }
    private void detach() {
        Vec3 velocity=getDeltaMovement();
        setDeltaMovement(velocity.x,Math.min(0,velocity.y),velocity.z);
        surfaceRoute.clear();
        smoothSupport = null;
        supportMotion = false;
        entityData.set(SURFACE,Direction.DOWN.ordinal());
        setNoGravity(false);
        surfaceGrace = 0;
        turnLock = 10;

        entityData.set(WEB,false);
        silkEdge = -1;
        syncSupport();
    }
    private boolean supportedStep(Vec3 step,Direction face) {
        AABB ahead=getBoundingBox().move(step.scale(2));
        return level().noCollision(this,getBoundingBox().expandTowards(step))
                && (SpiderSupportSurface.contact(level(),ahead,face)!=null
                || SpiderSupportSurface.find(level(),ahead,face)!=null);
    }
    private Vec3 surfaceStep(Vec3 desired,Direction face) {
        if(supportedStep(desired,face))
            return desired;
        if(tickCount>=routeRetry && surfaceGoal!=null) {
            routeRetry=tickCount+20;
            Vec3 center=getBoundingBox().getCenter();
            Vec3 normal=face.getUnitVec3();
            Vec3 target=center.add(desired.normalize().scale(3)).add(normal.scale(
                    Math.clamp(surfaceGoal.subtract(position()).dot(normal),0,3)));
            surfaceRoute.addAll(SpiderSurfaceRoute.find(
                    BugSurfaces.collect(level(),getBoundingBox().inflate(6)),getBoundingBox(),attachedSurface(),target));
            if(!surfaceRoute.isEmpty())return Vec3.ZERO;
        }
        Vec3 side=face.getUnitVec3().cross(desired);
        for(Vec3 step:new Vec3[]{desired,side.scale(flank),side.scale(-flank),desired.scale(-1)}) {
            if(supportedStep(step,face))
                return step;
        }
        return Vec3.ZERO;
    }
    private boolean followSurfaceRoute() {
        Vec3 center=getBoundingBox().getCenter();
        while(!surfaceRoute.isEmpty() && surfaceRoute.peekFirst().center().distanceToSqr(center)<.0064)
            surfaceRoute.removeFirst();
        if(surfaceRoute.isEmpty()) { refreshSupport();return false; }
        var point=surfaceRoute.peekFirst();
        Vec3 delta=point.center().subtract(center);
        Vec3 step=delta.normalize().scale(Math.min(delta.length(),.26*crawlSpeed*pace));
        var blocks=BugSurfaces.collect(level(),getBoundingBox().inflate(1.5));
        if(!level().noCollision(this,getBoundingBox().expandTowards(step))
                || SpiderSurfaceRoute.support(blocks,getBoundingBox().move(step),point.face())==null) {
            surfaceRoute.clear();setDeltaMovement(Vec3.ZERO);routeRetry=tickCount+10;return false;
        }
        attach(point.face());
        crawlHeading=step.normalize();setNoGravity(true);setDeltaMovement(step);
        getNavigation().stop();return true;
    }

    /** Follow actual, intact generated silk, including the links between anchors. */
    private boolean crawlWeb(Vec3 goal) {
        if (!level().dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)) return false;
        Vec3 center = position().add(0, getBbHeight() * .5, 0);
        Vec3 wanted = goal.subtract(position());
        if (wanted.lengthSqr() < .04) return false;
        Vec3 best = null;
        Direction webFace = attachedSurface();
        long bestKey = 0;
        int bestEdge = -1;
        double score = .08;
        for (var patch : WebPatchGenerator.around(level(), center, 5)) {
            for (int edgeIndex = 0; edgeIndex < patch.edges().size(); edgeIndex++) {
                var edge = patch.edges().get(edgeIndex);
                Vec3 a = patch.anchors().get(edge.a()), b = patch.anchors().get(edge.b());
                Vec3 ab = b.subtract(a);
                if (ab.lengthSqr() < .001) continue;
                Vec3 contact = LimboWebSystem.nearest(a, b, center);
                if (contact.distanceToSqr(center) > 1.15 * 1.15) continue;
                double along = contact.subtract(a).dot(ab) / ab.lengthSqr();
                Vec3 axis = ab.normalize();
                if (axis.dot(wanted) < 0) axis = axis.scale(-1);
                Direction support = attachedSurface();
                if (Math.abs(axis.dot(support.getUnitVec3())) > .7)
                    support = Math.abs(axis.x) < .7 ? Direction.EAST : Direction.DOWN;
                Vec3 nextContact = LimboWebSystem.nearest(a, b, contact.add(axis.scale(.38 * crawlSpeed)));
                double nextAlong = nextContact.subtract(a).dot(ab) / ab.lengthSqr();
                int from = patch.linkIndex(edgeIndex, Math.min(along, nextAlong));
                int to = patch.linkIndex(edgeIndex, Math.max(along, nextAlong));
                boolean broken = false;
                for (int link = from; link <= to; link++) if (LimboWebSystem.cut(patch.key(), link)) { broken = true; break; }
                if (broken || nextContact.distanceToSqr(contact) < .002) continue;
                double candidate = axis.dot(wanted.normalize()) - contact.distanceTo(center) * .18;
                if (onWeb() && silkKey == patch.key() && silkEdge == edgeIndex) candidate += .4;
                Vec3 step = nextContact.subtract(support.getUnitVec3().scale(.45)).subtract(center);
                if (step.length() > .46) step = step.normalize().scale(.46);
                if (candidate > score && level().noCollision(this, getBoundingBox().expandTowards(step))) {
                    best = step; score = candidate; webFace = support; bestKey = patch.key(); bestEdge = edgeIndex;
                }
            }
        }
        if (best == null) return false;
        getNavigation().stop();
        entityData.set(WEB, true);
        entityData.set(SURFACE, webFace.ordinal());
        smoothSupport = null; syncSupport();
        silkKey = bestKey; silkEdge = bestEdge;
        crawlHeading = SpiderSurfaceMotion.heading(webFace,best,crawlHeading,false);
        surfaceGrace = 2;
        setNoGravity(true); resetFallDistance();
        setDeltaMovement(best);
        return true;
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || !isAlive()) return;
        refreshSupport();
        if (isNoAi()) {
            if(smoothSupport==null && onGround() && attachedSurface()!=Direction.DOWN) {
                entityData.set(SURFACE,Direction.DOWN.ordinal()); refreshSupport();
            }
            surfaceRoute.clear();surfaceGoal=null; getNavigation().stop();
            if(smoothSupport!=null)setDeltaMovement(Vec3.ZERO);
            setNoGravity(smoothSupport!=null);
            // Mob AI normally drives travel; a NoAI display still needs to settle
            // against its support, and fall normally if that support is removed.
            travel(Vec3.ZERO);
            return;
        }
        if (nest == null) {
            if(level.dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)) {
                int slot = Math.floorDiv(getBlockZ()-UnderworldTerrain.SPAWN_Z,80);
                nest = UnderworldTerrain.chamberCenter(slot);
            } else nest=blockPosition();
        }
        if (perch == null && tickCount % 100 == 1) perch = findPerch();
        stateTicks++; if (attackCooldown > 0) attackCooldown--;
        if (turnLock > 0) turnLock--;
        if (tickCount >= paceUntil) {
            pace = .82 + random.nextDouble() * .34;
            paceUntil = tickCount + 45 + random.nextInt(90);
        }
        surfaceGoal = null;
        Player player = prey(level);
        double distance = player == null ? Double.POSITIVE_INFINITY : Math.sqrt(distanceToSqr(player));
        boolean approaching = distance + .06 < previousDistance;
        previousDistance = distance;
        boolean noticed = player != null && seenBy(player);
        boolean night = Math.floorMod(level.getGameTime(),24000L) >= 12000;
        switch (state()) {
            case HANGING -> {
                getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
                if (!touching(Direction.UP)) {
                    state(State.WANDERING); detach(); break;
                }
                if (player != null && distance < 30 && approaching) {
                    approachTicks = Math.min(80,approachTicks+1);
                    if (approachTicks >= 14) state(State.WAITING);
                } else approachTicks = Math.max(0,approachTicks-2);
                if (noticed && player != null && distance < 24) state(State.WAITING);
                else if (stateTicks > restDuration) {
                    patrolGoal = null;
                    state(night && random.nextBoolean() ? State.WANDERING_CAMOUFLAGED : State.WANDERING);
                }
            }
            case WAITING -> {
                getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
                if(!touching(Direction.UP)) { state(State.WANDERING); detach(); break; }
                if (approaching) approachTicks = Math.min(80,approachTicks+1);
                if (player != null && distance < 23 && stateTicks > 8
                        && (noticed || approachTicks > 22 && !approaching || distance < 5)) {
                    state(State.ATTACKING);
                    detach();
                    Vec3 jump = player.position().subtract(position()).normalize().scale(.55);
                    setDeltaMovement(jump.x,Math.min(-.08,jump.y),jump.z);
                    playSound(SoundEvents.SPIDER_AMBIENT,1.5F,.52F);
                } else if (player == null || distance > 38 || stateTicks > 260) state(State.HANGING);
            }
            case MIMICKING -> {
                if (attachedSurface() == Direction.UP) detach();
                if (player != null && (distance < 12 || noticed)) state(State.STALKING);
                else if (stateTicks > 220 || night) state(State.WANDERING_CAMOUFLAGED);
                else move(new Vec3(UnderworldTerrain.riverCenter(getZ()+12)-15,
                        nest.getY(),getZ()+12),.8);
            }
            case WANDERING_CAMOUFLAGED, WANDERING -> {
                if (player != null && distance < 30 && !homebound) {
                    state(state() == State.WANDERING_CAMOUFLAGED
                            ? State.STALKING_CAMOUFLAGED : State.STALKING);
                } else if (homebound || stateTicks > roamDuration
                        || position().distanceToSqr(Vec3.atCenterOf(nest)) > 46*46) {
                    homebound = true; returnHome(1.05);
                } else {
                    if (player != null && distance > 22 && distance < 40
                            && onGround() && stateTicks > 80 && random.nextInt(260) == 0) {
                        state(State.MIMICKING); break;
                    }
                    patrol();
                    if (state() == State.WANDERING_CAMOUFLAGED && stateTicks > 220)
                        state(State.WANDERING);
                }
            }
            case STALKING_CAMOUFLAGED, STALKING -> {
                if (player == null || distance > 42) { homebound = true; state(State.WANDERING); break; }
                if (noticed) { state(State.FLEEING_SEEN); break; }
                boolean isolated = level.players().stream().noneMatch(other -> other != player
                        && other.isAlive() && !other.isSpectator()
                        && other.distanceToSqr(player) < 12*12);
                if (distance < 8 && (isolated || player.getHealth() < player.getMaxHealth()*.4F)) {
                    state(State.ATTACKING); break;
                }
                if (stateTicks > 190 && state() == State.STALKING_CAMOUFLAGED) state(State.STALKING);
                Vec3 behind = player.position().subtract(player.getLookAngle().multiply(1,0,1).scale(8))
                        .add(player.getLookAngle().cross(new Vec3(0,1,0)).scale(flank * 5));
                if (distance > 13 || attachedSurface() == Direction.UP) move(behind,.93);
                else getNavigation().stop();
            }
            case HUNTING, ATTACKING -> {
                if (player == null) { homebound = true; state(State.WANDERING); break; }
                Vec3 toPrey = player.position().subtract(position());
                if (attachedSurface() == Direction.UP && toPrey.y < -2.5
                        && toPrey.horizontalDistanceSqr() < 9 && attackCooldown == 0 && getSensing().hasLineOfSight(player)) {
                    detach(); state(State.ATTACKING);
                    setDeltaMovement(toPrey.normalize().scale(.65));
                    break;
                }
                if (distance < 2.9 && getSensing().hasLineOfSight(player)) {
                    state(State.ATTACKING); getNavigation().stop();
                    if (attackCooldown == 0) {
                        attackCooldown = 30;
                        if (player.hurtServer(level,damageSources().mobAttack(this),7F)) {
                            Vec3 knock = player.position().subtract(position()).multiply(1,0,1)
                                    .normalize().scale(.46).add(0,.18,0);
                            if (player instanceof ServerPlayer victim && random.nextInt(4) == 0)
                                knockDown(victim,knock);
                            else player.push(knock.x,knock.y,knock.z);
                        }
                    }
                } else move(player.position(),state() == State.HUNTING ? 1.4 : 1.15);
                if (stateTicks > 380 && distance > 27) {
                    homebound = true; state(State.WANDERING);
                }
            }
            case FLEEING_HURT -> {
                if (stateTicks % 40 == 0 && getHealth() < getMaxHealth()) heal(1F);
                if (perch != null) returnHome(1.35);
                else if (player != null) {
                    Vec3 away = position().subtract(player.position()).multiply(1,0,1).normalize();
                    move(position().add(away.scale(17)),1.3);
                }
                if (perch == null && getHealth() >= getMaxHealth()*.65F && stateTicks > 100) {
                    homebound = true; state(State.WANDERING);
                }
            }
            case FLEEING_SEEN -> {
                if (player == null) { homebound = true; state(State.WANDERING); break; }
                if (escapeGoal == null || getNavigation().isStuck() && stateTicks % 20 == 0) {
                    Vec3 away = position().subtract(player.position()).multiply(1,0,1).normalize();
                    if (away.lengthSqr() < .001) away = Vec3.directionFromRotation(0,getYRot());
                    escapeGoal = position().add(away.scale(16)).add(random.nextBoolean()?4:-4,0,
                            random.nextBoolean()?4:-4);
                }
                move(escapeGoal,1.3);
                unseenTicks = getSensing().hasLineOfSight(player) ? 0 : unseenTicks+1;
                if (unseenTicks > 30) state(State.STALKING_CAMOUFLAGED);
                else if (stateTicks > 180) {
                    homebound = true;
                    state(State.WANDERING_CAMOUFLAGED);
                }
            }
        }
        crawl();
    }
    private void returnHome(double speed) {
        if (nest == null) return;
        if (attachedSurface() != Direction.DOWN && perch != null) {
            move(perch,speed);
            if (attachedSurface() == Direction.UP && position().distanceToSqr(perch) < 2.25
                    && touching(Direction.UP) && (state() != State.FLEEING_HURT || getHealth() >= getMaxHealth()*.65F)) {
                homebound = false; state(State.HANGING);
            }
            return;
        }
        Vec3 anchor = new Vec3(nest.getX()+.5,getY(),nest.getZ()+.5);
        if (position().distanceToSqr(anchor) > 2.5*2.5) {
            move(new Vec3(anchor.x,nest.getY()+1,anchor.z),speed);
            return;
        }
        if (perch == null) {
            getNavigation().stop(); return;
        }
        // Homeward ascent must have silk or a solid climbing surface to support it.
        move(perch, speed);
        if (position().distanceToSqr(perch) >= .35 * .35) return;
        if (touching(Direction.UP)) {
            entityData.set(SURFACE,Direction.UP.ordinal());
            getNavigation().stop(); setDeltaMovement(Vec3.ZERO); setNoGravity(true);
            if (state() != State.FLEEING_HURT || getHealth() >= getMaxHealth()*.65F) {
                homebound = false;
                state(State.HANGING);
            }
        }
    }
    private void patrol() {
        if (tickCount < pauseUntil) {
            getNavigation().stop();
            return;
        }
        if (patrolGoal == null || tickCount >= patrolUntil || position().distanceToSqr(patrolGoal) < 2) {
            Direction surface = attachedSurface();
            double angle = random.nextDouble() * Math.PI * 2;
            Vec3 offset = new Vec3(Math.cos(angle),0,Math.sin(angle)).scale(5 + random.nextInt(10));
            if (surface.getAxis().isHorizontal())
                offset = surface.getUnitVec3().cross(new Vec3(0,1,0)).scale(Math.cos(angle)*8)
                        .add(0,Math.sin(angle)*5,0);
            patrolGoal = surface == Direction.DOWN ? new Vec3(nest.getX()+offset.x,nest.getY()+1,nest.getZ()+offset.z)
                    : position().add(offset);
            patrolUntil = tickCount + 45 + random.nextInt(75);
            if (random.nextInt(4) == 0) {
                pauseUntil = tickCount + 8 + random.nextInt(22);
                getNavigation().stop(); return;
            }
        }
        move(patrolGoal,.9);
    }
    private void knockDown(ServerPlayer victim, Vec3 impulse) {
        victim.setDeltaMovement(impulse);
        victim.hurtMarked = true;
        victim.resetFallDistance();
        RagdollServerNetworking.markRagdolled(victim,38);
        if (ServerPlayNetworking.canSend(victim,RagdollImpulsePayload.TYPE))
            ServerPlayNetworking.send(victim,new RagdollImpulsePayload(position(),impulse,1.1F));
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level,source,amount);
        if (hurt && isAlive() && (getHealth() < getMaxHealth()*.5F || amount >= 30F)) {
            homebound = false;
            state(State.FLEEING_HURT);
        }
        return hurt;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putInt("SpiderState",state().ordinal());
        out.putInt("SpiderSurface",attachedSurface().ordinal());
        if (nest != null) { out.putInt("NestX",nest.getX());out.putInt("NestY",nest.getY());out.putInt("NestZ",nest.getZ()); }
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        if (in.getIntOr("NestY",Integer.MIN_VALUE) != Integer.MIN_VALUE)
            nest = new BlockPos(in.getIntOr("NestX",0),in.getIntOr("NestY",0),in.getIntOr("NestZ",0));
        state(State.values()[Math.clamp(in.getIntOr("SpiderState",0),0,State.values().length-1)]);
        entityData.set(SURFACE,Math.clamp(in.getIntOr("SpiderSurface",Direction.DOWN.ordinal()),
                0,Direction.values().length-1));
        syncSupport();
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
}
