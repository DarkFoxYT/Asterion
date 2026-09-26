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
import net.krodark.asterion.network.DazePayload;
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
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(
            LimboSpiderEntity.class, EntityDataSerializers.FLOAT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private BlockPos nest;
    private UUID huntTarget;
    private int stateTicks, approachTicks, attackCooldown, unseenTicks;
    private double previousDistance = Double.POSITIVE_INFINITY;
    private double previousWaitingDistance=Double.POSITIVE_INFINITY;
    private UUID observedPrey;
    private net.krodark.asterion.update.underworld.WebPatch perchSilk;
    private int perchEdge=-1;
    private boolean restoreSilk;
    private long savedSilkKey;
    private int savedSilkEdge=-1;
    private Vec3 surfaceGoal;
    private final java.util.ArrayDeque<SpiderSurfaceRoute.Point> surfaceRoute=new java.util.ArrayDeque<>();
    private int routeRetry;
    private Vec3 routeGoal;
    private int geometryTick = -1;
    private java.util.List<AABB> supportBlocks = java.util.List.of();
    private static final java.util.Map<Level,long[]> ROUTE_BUDGET = new java.util.WeakHashMap<>();
    private Vec3 perch;
    private Vec3 patrolGoal;
    private Vec3 escapeGoal;
    private boolean homebound;
    private int surfaceGrace;
    private double crawlSpeed = 1;
    private Vec3 crawlHeading = new Vec3(0,0,1);
    private static final EntityDataAccessor<Float> HEADING_X = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Y = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEADING_Z = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    public Vec3 locomotionHeading() { return new Vec3(entityData.get(HEADING_X),entityData.get(HEADING_Y),entityData.get(HEADING_Z)); }
    private int turnLock, patrolUntil, pauseUntil, restDuration, roamDuration, paceUntil;
    private double pace = 1;
    private final int flank;
    private long silkKey;
    private int silkEdge = -1;
    private net.krodark.asterion.update.underworld.WebPatch silkPatch;
    private int webReleaseUntil, nextSpin;
    private int nextBridge;
    private SpiderWebTrip webTrip;
    private int tripDeadline;
    private static final EntityDataAccessor<Boolean> THREAD = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> THREAD_X = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THREAD_Y = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> THREAD_Z = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    public Vec3 threadAnchor() { return entityData.get(THREAD)?new Vec3(entityData.get(THREAD_X),entityData.get(THREAD_Y),entityData.get(THREAD_Z)):null; }
    private void thread(Vec3 anchor) {
        entityData.set(THREAD,anchor!=null);
        if(anchor!=null) { entityData.set(THREAD_X,(float)anchor.x);entityData.set(THREAD_Y,(float)anchor.y);entityData.set(THREAD_Z,(float)anchor.z); }
    }
    public Vec3 estimatedWebmaker() {
        Vec3 up=attachmentNormal().scale(-1);
        return position().add(0,.84*modelScale(),0)
                .add(up.scale(-.39*modelScale()))
                .subtract(crawlHeading.normalize().scale(23.0/16*modelScale()));
    }
    public void cutThread(Player player) {
        webTrip=null;thread(null);nextSpin=tickCount+200;webReleaseUntil=tickCount+30;
        if(state()==State.HANGING || state()==State.WAITING)detach();
        hunt(player);
    }
    private void beginWeb(net.krodark.asterion.update.underworld.WebPatch patch) {
        if(patch==null || webTrip!=null)return;
        webTrip=new SpiderWebTrip(patch);tripDeadline=tickCount+600;
        surfaceRoute.clear();routeRetry=tickCount;
    }
    private Vec3 progressPosition;
    private int stalledTicks;
    private int stalledAttempts;
    private static final EntityDataAccessor<Long> SILK_KEY = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> SILK_EDGE = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.INT);
    public long silkKey() { return entityData.get(SILK_KEY); }
    public int silkEdge() { return entityData.get(SILK_EDGE); }
    private SpiderSupportSurface.Plane smoothSupport;
    private boolean supportMotion;
    private static final EntityDataAccessor<Float> NORMAL_X = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> NORMAL_Y = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> NORMAL_Z = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SUPPORT_DISTANCE = SynchedEntityData.defineId(LimboSpiderEntity.class,EntityDataSerializers.FLOAT);
    public Vec3 attachmentNormal() { return new Vec3(entityData.get(NORMAL_X),entityData.get(NORMAL_Y),entityData.get(NORMAL_Z)).normalize(); }
    public float spiderScale() { return entityData.get(SIZE); }
    public float modelScale() { return SpiderDimensions.renderScale(spiderScale()); }
    @Override protected AABB makeBoundingBox(Vec3 position) {
        return net.minecraft.world.entity.EntityDimensions.scalable(
                SpiderDimensions.width(spiderScale()), SpiderDimensions.height(spiderScale())).makeBoundingBox(position);
    }
    public boolean hasSurfaceSupport() { return entityData.get(SUPPORT_DISTANCE) > 0; }
    public boolean hasSmoothSupport() { return hasSurfaceSupport() && attachmentNormal().dot(attachedSurface().getUnitVec3()) < .995; }
    public Vec3 supportPoint() { return getBoundingBox().getCenter().add(attachmentNormal().scale(entityData.get(SUPPORT_DISTANCE))); }
    private void syncSupport() {
        Vec3 normal = smoothSupport == null ? attachedSurface().getUnitVec3() : smoothSupport.outward().scale(-1);
        if(!onWeb() && smoothSupport!=null && normal.dot(attachedSurface().getUnitVec3())>.995)
            normal=SpiderSupportSurface.neighborhoodNormal(localSupport(),getBoundingBox(),attachedSurface());
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
            var blocks=localSupport();
            smoothSupport=SpiderSupportSurface.fit(blocks,getBoundingBox(),face);
            if(smoothSupport==null)smoothSupport=SpiderSupportSurface.contact(blocks,getBoundingBox(),face);
        }
        supportMotion=smoothSupport!=null && (face!=Direction.DOWN || isNoAi()
                || smoothSupport.outward().dot(face.getUnitVec3().scale(-1))<.995);
        syncSupport();
    }

    private java.util.List<AABB> localSupport() {
        if(geometryTick!=tickCount) {
            geometryTick=tickCount;
            supportBlocks=BugSurfaces.collectCollision(level(),getBoundingBox().inflate(2.8)
                    .expandTowards(attachedSurface().getUnitVec3().scale(2)));
        }
        return supportBlocks;
    }
    private boolean mayPlanRoute() {
        if(tickCount<routeRetry)return false;
        long[] budget=ROUTE_BUDGET.computeIfAbsent(level(),ignored->new long[]{-1,0});
        if(budget[0]!=level().getGameTime()) { budget[0]=level().getGameTime();budget[1]=0; }
        // Bound aggregate work; existing routes keep moving while others wait.
        if(budget[1]>=2) { routeRetry=tickCount+1+Math.floorMod(getId()+tickCount,5);return false; }
        budget[1]++;
        routeRetry=tickCount+25+Math.floorMod(getId(),15);
        return true;
    }
    private boolean planSurfaceRoute(Vec3 goal) {
        if(!mayPlanRoute())return false;
        var blocks=BugSurfaces.collectCollision(level(),getBoundingBox().inflate(13));
        surfaceRoute.addAll(SpiderSurfaceRoute.navigate(blocks,getBoundingBox(),attachedSurface(),goal.add(0,getBbHeight()*.5,0)));
        routeGoal=goal;
        return !surfaceRoute.isEmpty();
    }
    public LimboSpiderEntity(net.minecraft.world.entity.EntityType<? extends LimboSpiderEntity> type, Level level) {
        super(type, level);
        xpReward = 20;
        setPersistenceRequired();
        setNoGravity(true);
        entityData.set(SIZE, SpiderDimensions.MIN_SIZE + random.nextFloat() * (SpiderDimensions.MAX_SIZE - SpiderDimensions.MIN_SIZE));
        refreshDimensions();
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
        return reason != EntitySpawnReason.NATURAL || level instanceof Level world
                && world.dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)
                && world.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL;
    }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    public boolean camouflaged() { return state()==State.WANDERING_CAMOUFLAGED || state()==State.STALKING_CAMOUFLAGED; }
    public void settleNaturally() {
        nest=blockPosition();setPersistenceRequired();
        state(random.nextBoolean()?State.WANDERING_CAMOUFLAGED:State.WANDERING);
        setNoGravity(false);refreshSupport();
    }
    @Override protected void registerGoals() { }
    @Override public void jumpFromGround() {
        // Voxel steps use connected surface routes, not the ground navigator's
        // bunny-hop impulse. Swimming retains vanilla escape behavior.
        if(isInWater() || isInLava())super.jumpFromGround();
    }
    @Override public void makeStuckInBlock(net.minecraft.world.level.block.state.BlockState state,Vec3 multiplier) {
        // Legacy chunks may still contain old cobweb blocks. They cannot fight silk locomotion.
        if(!state.is(net.minecraft.world.level.block.Blocks.COBWEB))super.makeStuckInBlock(state,multiplier);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(STATE, State.HANGING.ordinal());
        data.define(SURFACE, Direction.DOWN.ordinal());
        data.define(SIZE, SpiderDimensions.MIN_SIZE);
        data.define(WEB, false);
        data.define(THREAD,false);data.define(THREAD_X,0F);data.define(THREAD_Y,0F);data.define(THREAD_Z,0F);
        data.define(HEADING_X,0F);data.define(HEADING_Y,0F);data.define(HEADING_Z,1F);
        data.define(SILK_KEY,0L);data.define(SILK_EDGE,-1);
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
            if(perchSilk!=null) {
                silkPatch=perchSilk;silkKey=perchSilk.key();silkEdge=perchEdge;
                entityData.set(WEB,true);entityData.set(SILK_KEY,silkKey);entityData.set(SILK_EDGE,silkEdge);
                entityData.set(SURFACE,Direction.DOWN.ordinal());
            } else entityData.set(SURFACE,Direction.UP.ordinal());
            setNoGravity(true);
        } else {
            setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
            state(State.WANDERING);
        }
    }
    private Vec3 findPerch() {
        if (nest == null) return null;
        perchSilk=null;perchEdge=-1;
        if(level() instanceof ServerLevel && level().dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)) {
            for(var patch:WebPatchGenerator.around(level(),Vec3.atCenterOf(nest),3))for(int edge=0;edge<patch.edges().size();edge++) {
                var link=patch.edges().get(edge);
                Vec3 span=patch.anchors().get(link.b()).subtract(patch.anchors().get(link.a()));
                Vec3 at=patch.point(edge,.5).add(0,.08,0);
                if(span.lengthSqr()<36 || Math.abs(span.normalize().y)>.35 || at.distanceToSqr(Vec3.atCenterOf(nest))>20*20
                        || !LimboWebSystem.supports(level(),patch,edge))continue;
                if(level().noCollision(this,getBoundingBox().move(at.subtract(position())))) {
                    perchSilk=patch;perchEdge=edge;return at;
                }
            }
        }
        for (int radius = 0; radius <= 3; radius++) {
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz)) != radius) continue;
                double x = nest.getX()+dx+.5, z = nest.getZ()+dz+.5;
                for (int y = nest.getY()+10; y <= nest.getY()+27; y++) {
                    double foot = y-getBbHeight()-.14;
                    AABB box = getBoundingBox().move(x-getX(),foot-getY(),z-getZ());
                    if (level().noCollision(this,box)
                            && !BugSurfaces.collectCollision(level(),box.move(0,.30,0)).isEmpty())
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
        if(next==State.WAITING)previousWaitingDistance=previousDistance;
        if (next == State.FLEEING_SEEN) { unseenTicks = 0; escapeGoal = null; }
        // Camouflage is a per-bone block material, never entity invisibility.
        setInvisible(false);
        boolean perched = next == State.HANGING || next == State.WAITING;
        setNoGravity(perched || attachedSurface() != Direction.DOWN || onWeb());
        if (perched) {
            if (!onWeb() && perch != null && position().distanceToSqr(perch) < 2
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
        if (attachedSurface() != Direction.DOWN || onWeb() || !surfaceRoute.isEmpty()) return;
        if ((tickCount+getId()) % 10 == 0)
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
        return face==attachedSurface() && smoothSupport!=null
                || !BugSurfaces.collectCollision(level(),getBoundingBox().move(face.getUnitVec3().scale(.36))).isEmpty();
    }
    @Override public void travel(Vec3 input) {
        if (!level().isClientSide() && onWeb() && !webStillSupports()) {
            webReleaseUntil=tickCount+16;
            detach();
        }
        if(!level().isClientSide() && !surfaceRoute.isEmpty() && !isNoAi()) {
            var blocks=localSupport();
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
            // A large fitted-plane error must not indefinitely suppress forward
            // motion. Try a collision-safe shortened step while resolving it.
            Vec3 step=motion;
            for(int attempt=0;attempt<4 && !level().noCollision(this,getBoundingBox().expandTowards(step));attempt++)step=step.scale(.5);
            if(level().noCollision(this,getBoundingBox().expandTowards(step)))move(MoverType.SELF,step);
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
        } else {
            if(!level().isClientSide() && onGround() && !isInWater() && !isInLava()) {
                Vec3 intended=input.horizontalDistanceSqr()>.0001
                        ? input.yRot(-getYRot()*Mth.DEG_TO_RAD).multiply(1,0,1)
                        : getDeltaMovement().multiply(1,0,1);
                if(intended.lengthSqr()>.0001) {
                    crawlHeading=SpiderSurfaceMotion.turn(Direction.DOWN.getUnitVec3(),crawlHeading,intended,.42);
                    double alignment=Math.clamp((crawlHeading.dot(intended.normalize())-.2)/.8,0,1);
                    input=input.scale(alignment);
                    Vec3 velocity=getDeltaMovement();
                    setDeltaMovement(velocity.x*alignment,velocity.y,velocity.z*alignment);
                }
            }
            super.travel(input);
        }
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
            if(onWeb()) {
                if(webStillSupports() && crawlWeb(position()))return;
                detach();state(State.WANDERING);return;
            }
            setNoGravity(smoothSupport!=null); setDeltaMovement(Vec3.ZERO); return;
        }
        Direction surface = attachedSurface();
        Vec3 goal = surfaceGoal;
        if(goal!=null && routeGoal!=null && goal.distanceToSqr(routeGoal)>16)surfaceRoute.clear();
        if(!surfaceRoute.isEmpty()) {
            if(goal!=null && !isInWater() && !isInLava() && followSurfaceRoute())return;
            surfaceRoute.clear();
        }
        // Prefer the solid face we already grip. Silk can take over only off that
        // face, preventing dense webs from flipping a wall crawler's normal.
        if ((goal != null || onWeb()) && !isInWater() && !isInLava() && tickCount>=webReleaseUntil
                && (smoothSupport == null || onWeb())
                && (onWeb() || surface == Direction.DOWN || !touching(surface)) && crawlWeb(goal==null?position():goal)) return;
        if(onWeb()) { webReleaseUntil=tickCount+16;detach();surface=Direction.DOWN; }
        entityData.set(WEB, false);
        silkEdge = -1;
        Vec3 ahead=goal==null?Vec3.ZERO:goal.subtract(position()).multiply(1,0,1).normalize().scale(.7);
        boolean ledge=surface==Direction.DOWN && goal!=null && smoothSupport!=null
                && !supportedStep(ahead,surface);
        boolean pit=ledge && level().noCollision(this,getBoundingBox().move(ahead))
                && SpiderSupportSurface.contact(localSupport(),getBoundingBox().move(ahead.scale(2)).move(0,-2,0),Direction.DOWN)==null;
        if(pit && tickCount>=nextBridge && level() instanceof ServerLevel server) {
            nextBridge=tickCount+60+Math.floorMod(getId(),20);
            beginWeb(WebPatchGenerator.planBridge(server,getBoundingBox().getCenter(),goal,getBbHeight()));
        }
        if(pit && tickCount>=webReleaseUntil && crawlWeb(goal))return;
        // A roof target requires a connected route to a wall before climbing.
        if(goal!=null && (smoothSupport!=null || onGround())
                && (ledge || Math.abs(goal.y-getY())>1.5
                || surface!=Direction.DOWN && Math.abs(goal.subtract(position()).dot(surface.getUnitVec3()))>1.2
                || horizontalCollision || getNavigation().isStuck())
                && planSurfaceRoute(goal) && followSurfaceRoute())return;
        if(pit) { getNavigation().stop();setDeltaMovement(Vec3.ZERO);return; }
        if (supportMotion && smoothSupport != null) {
            // Keep corner detection active on flat walls too; a fitted contact
            // plane must not swallow the wall-to-ceiling transition.
            if (surface.getAxis().isHorizontal() && goal!=null && goal.y>getY()+.5 && touching(Direction.UP)) {
                var ceiling=SpiderSupportSurface.contact(localSupport(),getBoundingBox(),Direction.UP);
                if(ceiling!=null) { attach(Direction.UP);smoothSupport=ceiling;syncSupport();surface=attachedSurface(); }
            }
            Vec3 desired = goal == null ? Vec3.ZERO : goal.subtract(position());
            Vec3 tangent = smoothSupport.tangent(desired);
            getNavigation().stop(); setNoGravity(true); resetFallDistance();
            if (tangent.lengthSqr() > .16 && goal != null) {
                crawlHeading = SpiderSurfaceMotion.turn(smoothSupport.outward(),crawlHeading,tangent,.42);
                Vec3 step=crawlHeading.scale(.32*crawlSpeed*pace);
                if(surface!=Direction.DOWN || horizontalCollision) {
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
            if (goal == null || isInWater() || isInLava()) {
                if(goal==null && onGround())setDeltaMovement(0,getDeltaMovement().y,0);
                return;
            }
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
        var nearby = BugSurfaces.collectCollision(level(),getBoundingBox().inflate(.8));
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
        silkPatch = null;
        syncSupport();
    }
    private boolean supportedStep(Vec3 step,Direction face) {
        AABB ahead=getBoundingBox().move(step.scale(2));
        return level().noCollision(this,getBoundingBox().expandTowards(step))
                && (SpiderSupportSurface.contact(localSupport(),ahead,face)!=null
                || SpiderSupportSurface.fit(localSupport(),ahead,face)!=null);
    }
    private Vec3 surfaceStep(Vec3 desired,Direction face) {
        if(supportedStep(desired,face))
            return desired;
        if(surfaceGoal!=null)planSurfaceRoute(surfaceGoal);
        return Vec3.ZERO;
    }
    private boolean followSurfaceRoute() {
        Vec3 center=getBoundingBox().getCenter();
        while(!surfaceRoute.isEmpty() && surfaceRoute.peekFirst().center().distanceToSqr(center)<.0001)
            surfaceRoute.removeFirst();
        if(surfaceRoute.isEmpty()) { refreshSupport();return false; }
        // Carry speed across collinear nodes instead of slowing to the small
        // remainder at every grid cell (the old stop/go "rollback" appearance).
        double speed=.26*crawlSpeed*pace;
        while(surfaceRoute.size()>1) {
            var iterator=surfaceRoute.iterator();
            var first=iterator.next();var second=iterator.next();
            Vec3 a=first.center().subtract(center),b=second.center().subtract(first.center());
            if(a.length()>=speed || a.normalize().dot(b.normalize())<.999)break;
            surfaceRoute.removeFirst();
        }
        var point=surfaceRoute.peekFirst();
        Vec3 delta=point.center().subtract(center);
        Vec3 step=delta.normalize().scale(Math.min(delta.length(),speed));
        var blocks=localSupport();
        if(!level().noCollision(this,getBoundingBox().expandTowards(step))
                || SpiderSurfaceRoute.support(blocks,getBoundingBox().move(step),point.face())==null) {
            surfaceRoute.clear();setDeltaMovement(Vec3.ZERO);routeRetry=tickCount+10;return false;
        }
        // Attach to the current grip, never prematurely to a future waypoint.
        Direction grip=SpiderSurfaceRoute.support(blocks,getBoundingBox(),attachedSurface());
        if(grip==null) { detach();return false; }
        attach(grip);
        smoothSupport=SpiderSupportSurface.contact(blocks,getBoundingBox(),grip);
        syncSupport();
        Vec3 forward=SpiderSurfaceMotion.tangent(grip,step).normalize();
        crawlHeading=SpiderSurfaceMotion.turn(grip.getUnitVec3(),crawlHeading,forward,.42);
        // Turn before translating through a reversal. Keep the collision-tested
        // route vector rather than steering off its narrow support corridor.
        double alignment=forward.lengthSqr()<.001?1:crawlHeading.dot(forward);
        step=step.scale(Math.clamp((alignment-.2)/.8,0,1));
        setNoGravity(true);setDeltaMovement(step);
        getNavigation().stop();return true;
    }

    /** Follow actual, intact generated silk, including the links between anchors. */
    private boolean webStillSupports() {
        return silkPatch!=null && silkEdge>=0 && silkEdge<silkPatch.edges().size()
                && LimboWebSystem.supports(level(),silkPatch,silkEdge);
    }
    private boolean crawlWeb(Vec3 goal) {
        if (!level().dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)) return false;
        Vec3 center = position().add(0, getBbHeight() * .5, 0);
        Vec3 wanted = goal.subtract(position());
        boolean resting=wanted.lengthSqr()<.04;
        Vec3 best = null;
        Direction webFace = attachedSurface();
        long bestKey = 0;
        int bestEdge = -1;
        double score = -1;
        var nearby=WebPatchGenerator.around(level(),center,5);
        if(onWeb() && silkPatch!=null && nearby.stream().noneMatch(p->p.key()==silkKey))nearby.add(silkPatch);
        net.krodark.asterion.update.underworld.WebPatch bestPatch=null;
        for (var patch : nearby) {
            for (int edgeIndex = 0; edgeIndex < patch.edges().size(); edgeIndex++) {
                if(!LimboWebSystem.supports(level(),patch,edgeIndex))continue;
                var edge = patch.edges().get(edgeIndex);
                Vec3 a = patch.anchors().get(edge.a()), b = patch.anchors().get(edge.b());
                Vec3 ab = b.subtract(a);
                if (ab.lengthSqr() < .001) continue;
                Direction support = Math.abs(ab.normalize().y)>.8?Direction.EAST:Direction.DOWN;
                double clearance=support.getAxis()==Direction.Axis.Y?getBbHeight()*.5+.08:getBbWidth()*.5+.08;
                double along = patch.nearestAlong(edgeIndex,center.add(support.getUnitVec3().scale(clearance)));
                Vec3 contact = patch.point(edgeIndex,along);
                boolean current=onWeb() && silkKey==patch.key() && silkEdge==edgeIndex;
                double reach=current?.55:getBbWidth()*.5+.35;
                if (contact.distanceToSqr(center.add(support.getUnitVec3().scale(clearance))) > reach*reach) continue;
                Vec3 axis = ab.normalize();
                if (axis.dot(wanted) < 0) axis = axis.scale(-1);
                if(!current && !resting && axis.dot(wanted.normalize())<.45)continue;
                // Keep the body above horizontal silk, with a stable side on vertical threads.
                double advance=resting?0:.25*crawlSpeed*pace/ab.length();
                double nextAlong=Math.clamp(along+(axis.dot(ab)>0?advance:-advance),0,1);
                boolean landing=current && (axis.dot(ab)>0?along>.85:along<.15);
                if(landing) {
                    var ground=SpiderSupportSurface.contact(localSupport(),getBoundingBox().move(axis.scale(.5)).move(0,.25,0),Direction.DOWN);
                    if(ground!=null) {
                        Vec3 correction=ground.correction(getBoundingBox(),Vec3.ZERO);
                        Vec3 ontoBank=axis.multiply(1,0,1).normalize().scale(.3);
                        if(correction.length()<.35 && level().noCollision(this,getBoundingBox().expandTowards(correction))
                                && level().noCollision(this,getBoundingBox().move(correction).expandTowards(ontoBank))) {
                            move(MoverType.SELF,correction);
                            move(MoverType.SELF,ontoBank);
                            entityData.set(WEB,false);silkEdge=-1;entityData.set(SILK_EDGE,-1);silkPatch=null;
                            attach(Direction.DOWN);refreshSupport();webReleaseUntil=tickCount+12;
                            return false;
                        }
                    }
                }
                Vec3 nextContact=patch.point(edgeIndex,nextAlong);
                if(!resting && nextContact.distanceToSqr(contact)<.0001)continue;
                double candidate = (resting?0:axis.dot(wanted.normalize())) - contact.distanceTo(center) * .3;
                if (current) candidate += .8;
                if(!resting && nextContact.distanceToSqr(contact)<.001)candidate-=.9;
                Vec3 step = nextContact.subtract(support.getUnitVec3().scale(clearance)).subtract(center);
                if (step.length() > .32) step = step.normalize().scale(.32);
                if (candidate > score && level().noCollision(this, getBoundingBox().expandTowards(step))) {
                    best = step; score = candidate; webFace = support; bestKey = patch.key(); bestEdge = edgeIndex; bestPatch=patch;
                }
            }
        }
        if (best == null) return false;
        getNavigation().stop();
        entityData.set(WEB, true);
        entityData.set(SURFACE, webFace.ordinal());
        smoothSupport = null; syncSupport();
        silkKey = bestKey; silkEdge = bestEdge;
        silkPatch=bestPatch;
        entityData.set(SILK_KEY,bestKey);entityData.set(SILK_EDGE,bestEdge);
        crawlHeading = SpiderSurfaceMotion.heading(webFace,best,crawlHeading,false);
        surfaceGrace = 2;
        setNoGravity(true); resetFallDistance();
        setDeltaMovement(best);
        return true;
    }
    @Override public void tick() {
        if(restoreSilk && level() instanceof ServerLevel) {
            restoreSilk=false;
            for(var patch:WebPatchGenerator.around(level(),position(),5))if(patch.key()==savedSilkKey
                    && savedSilkEdge>=0 && savedSilkEdge<patch.edges().size() && LimboWebSystem.supports(level(),patch,savedSilkEdge)) {
                silkPatch=patch;silkKey=patch.key();silkEdge=savedSilkEdge;
                entityData.set(WEB,true);entityData.set(SILK_KEY,silkKey);entityData.set(SILK_EDGE,silkEdge);setNoGravity(true);break;
            }
        }
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
        UUID observing=player==null?null:player.getUUID();
        if(!java.util.Objects.equals(observing,observedPrey)) {
            observedPrey=observing;approachTicks=0;previousDistance=Double.POSITIVE_INFINITY;previousWaitingDistance=Double.POSITIVE_INFINITY;
        }
        double distance = player == null ? Double.POSITIVE_INFINITY : Math.sqrt(distanceToSqr(player));
        boolean approaching = distance + .06 < previousDistance;
        previousDistance = distance;
        boolean noticed = player != null && seenBy(player);
        boolean night = SpiderBehavior.night(level.getGameTime());
        switch (state()) {
            case HANGING -> {
                getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
                if (!(onWeb()?webStillSupports():touching(Direction.UP))) {
                    state(State.WANDERING); detach(); break;
                }
                if (player != null && distance < 30 && approaching) {
                    approachTicks = Math.min(80,approachTicks+1);
                    if (approachTicks >= 14) state(State.WAITING);
                } else approachTicks = Math.max(0,approachTicks-2);
                if (noticed && player != null && distance < 24) state(State.WAITING);
                else if (night && stateTicks > restDuration) {
                    patrolGoal = null;
                    state(night && random.nextBoolean() ? State.WANDERING_CAMOUFLAGED : State.WANDERING);
                }
            }
            case WAITING -> {
                getNavigation().stop(); setDeltaMovement(Vec3.ZERO);
                if(!(onWeb()?webStillSupports():touching(Direction.UP))) { state(State.WANDERING); detach(); break; }
                if (approaching) approachTicks = Math.min(80,approachTicks+1);
                if (player != null && SpiderBehavior.ambush(approachTicks,distance,previousWaitingDistance,noticed,stateTicks)) {
                    state(State.ATTACKING);
                    detach();
                    Vec3 jump = player.position().subtract(position()).normalize().scale(.55);
                    setDeltaMovement(jump.x,Math.min(-.08,jump.y),jump.z);
                    playSound(SoundEvents.SPIDER_AMBIENT,1.5F,.52F);
                } else if (player == null || distance > 38 || stateTicks > 260) state(State.HANGING);
                previousWaitingDistance=distance;
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
                } else if (homebound || !night && stateTicks>100 || stateTicks > roamDuration
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
                if (getBoundingBox().inflate(.65).intersects(player.getBoundingBox()) && getSensing().hasLineOfSight(player)) {
                    state(State.ATTACKING); getNavigation().stop();
                    if (attackCooldown == 0) {
                        attackCooldown = 30;
                        if (player.hurtServer(level,damageSources().mobAttack(this),7F)) {
                            Vec3 knock = player.position().subtract(position()).multiply(1,0,1)
                                    .normalize().scale(.46).add(0,.18,0);
                            if (player instanceof ServerPlayer victim && (stateTicks<35 || random.nextInt(4) == 0))
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
                if (perch != null && stalledAttempts<2) returnHome(1.35);
                else if (player != null) {
                    Vec3 away = position().subtract(player.position()).multiply(1,0,1).normalize();
                    move(position().add(away.scale(17)),1.3);
                }
                if ((perch == null || stalledAttempts>=2) && getHealth() >= getMaxHealth()*.65F && stateTicks > 100) {
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
        boolean weaving=!homebound && (state()==State.WANDERING || state()==State.WANDERING_CAMOUFLAGED) || state()==State.HUNTING;
        if(webTrip!=null) {
            if(!weaving || tickCount>tripDeadline || !LimboWebSystem.supports(level,webTrip.patch(),0)) {
                webTrip=null;surfaceRoute.clear();thread(null);
            } else if(webTrip.visit(position(),getBoundingBox(),smoothSupport!=null || onGround() || onWeb())) {
                WebPatchGenerator.finishSpin(level,webTrip.patch());webTrip=null;thread(null);surfaceRoute.clear();
            } else move(webTrip.goal(getBoundingBox()),.85);
        }
        crawl();
        if(webTrip!=null && webTrip.attached())thread(webTrip.patch().anchors().getFirst());
        else if(state()==State.HANGING || state()==State.WAITING) {
            if(onWeb() && webStillSupports())thread(silkPatch.point(silkEdge,silkPatch.nearestAlong(silkEdge,estimatedWebmaker())));
            else {
                Vec3 maker=estimatedWebmaker();
                var hit=level.clip(new net.minecraft.world.level.ClipContext(maker,maker.add(attachmentNormal().scale(4)),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,this));
                thread(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK?hit.getLocation():null);
            }
        } else thread(null);
        boolean trying=surfaceGoal!=null && surfaceGoal.distanceToSqr(position())>1
                && state()!=State.HANGING && state()!=State.WAITING && tickCount>=pauseUntil;
        if(trying && progressPosition!=null && position().distanceToSqr(progressPosition)<.0004)stalledTicks++;
        else { stalledTicks=0;if(progressPosition!=null && position().distanceToSqr(progressPosition)>.0025)stalledAttempts=0; }
        progressPosition=position();
        if(stalledTicks>=30) {
            // A valid contact is not proof of progress. Abandon a stale local
            // waypoint, replan once, then choose another patrol destination.
            surfaceRoute.clear();routeRetry=Math.min(routeRetry,tickCount);
            if(++stalledAttempts>=2 || !planSurfaceRoute(surfaceGoal)) {
                if(webTrip!=null) { webTrip=null;thread(null);nextSpin=tickCount+200; }
                if(state()==State.WANDERING || state()==State.WANDERING_CAMOUFLAGED) {
                    patrolGoal=null;patrolUntil=0;pauseUntil=0;
                    stalledAttempts=0;
                }
            }
            stalledTicks=0;
        }
        Vec3 visibleHeading=crawlHeading;
        if(state()==State.FLEEING_SEEN && player!=null) {
            Vec3 watch=SpiderSurfaceMotion.tangent(attachedSurface(),player.position().subtract(position()));
            if(watch.lengthSqr()>.01)visibleHeading=watch.normalize();
        }
        entityData.set(HEADING_X,(float)(Math.rint(visibleHeading.x*1000)/1000));
        entityData.set(HEADING_Y,(float)(Math.rint(visibleHeading.y*1000)/1000));
        entityData.set(HEADING_Z,(float)(Math.rint(visibleHeading.z*1000)/1000));
        if(tickCount>=nextSpin && webTrip==null && !onWeb() && smoothSupport!=null && !homebound
                && (state()==State.WANDERING || state()==State.WANDERING_CAMOUFLAGED)) {
            nextSpin=tickCount+160+random.nextInt(200);
            beginWeb(WebPatchGenerator.planSpin(level,getBoundingBox().getCenter(),crawlHeading));
        }
    }
    private void returnHome(double speed) {
        if (nest == null) return;
        if ((attachedSurface() != Direction.DOWN || onWeb()) && perch != null) {
            move(perch,speed);
            if (position().distanceToSqr(perch) < .45*.45 && (onWeb()?webStillSupports():attachedSurface()==Direction.UP && touching(Direction.UP))
                    && (state() != State.FLEEING_HURT || getHealth() >= getMaxHealth()*.65F)) {
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
        RagdollServerNetworking.markRagdolled(victim,86);
        if (ServerPlayNetworking.canSend(victim,RagdollImpulsePayload.TYPE))
            ServerPlayNetworking.send(victim,new RagdollImpulsePayload(position(),impulse,1.1F));
        if (ServerPlayNetworking.canSend(victim,DazePayload.TYPE))
            ServerPlayNetworking.send(victim,new DazePayload(82,5));
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level,source,amount);
        if (hurt && isAlive() && SpiderBehavior.flee(getHealth(),getMaxHealth(),amount)) {
            if(source.getEntity() instanceof Player attacker)huntTarget=attacker.getUUID();
            homebound = false;
            state(State.FLEEING_HURT);
        }
        return hurt;
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putFloat("SpiderScale", spiderScale());
        out.putInt("SpiderState",state().ordinal());
        out.putInt("SpiderSurface",attachedSurface().ordinal());
        if(onWeb()) { out.putLong("SilkKey",silkKey);out.putInt("SilkEdge",silkEdge); }
        if (nest != null) { out.putInt("NestX",nest.getX());out.putInt("NestY",nest.getY());out.putInt("NestZ",nest.getZ()); }
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        entityData.set(SIZE, Mth.clamp(in.getFloatOr("SpiderScale", SpiderDimensions.MIN_SIZE),
                SpiderDimensions.MIN_SIZE, SpiderDimensions.MAX_SIZE));
        refreshDimensions();
        setPersistenceRequired();
        if (in.getIntOr("NestY",Integer.MIN_VALUE) != Integer.MIN_VALUE)
            nest = new BlockPos(in.getIntOr("NestX",0),in.getIntOr("NestY",0),in.getIntOr("NestZ",0));
        state(State.values()[Math.clamp(in.getIntOr("SpiderState",0),0,State.values().length-1)]);
        entityData.set(SURFACE,Math.clamp(in.getIntOr("SpiderSurface",Direction.DOWN.ordinal()),
                0,Direction.values().length-1));
        savedSilkKey=in.getLongOr("SilkKey",0L);savedSilkEdge=in.getIntOr("SilkEdge",-1);restoreSilk=savedSilkEdge>=0;
        syncSupport();
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }
}
