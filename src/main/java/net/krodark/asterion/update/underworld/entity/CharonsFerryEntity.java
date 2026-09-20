package net.krodark.asterion.update.underworld.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.update.underworld.world.UnderworldTerrain;
import net.krodark.asterion.update.underworld.world.FerryHull;
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
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(
            CharonsFerryEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(
            CharonsFerryEntity.class, EntityDataSerializers.FLOAT);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);
    private int departureWait = 40;
    private int returnWait;
    private float previousVisualRoll;
    private float visualRoll;
    private float previousVisualPitch;
    private float visualPitch;
    private float shoreFactor = 1;
    private int shoreX = Integer.MIN_VALUE, shoreZ = Integer.MIN_VALUE;
    private double surgeSpeed, heaveSpeed;
    public static final int EMERGENCE_TICKS = 110;

    public CharonsFerryEntity(EntityType<? extends CharonsFerryEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void berth() {
        surgeSpeed = heaveSpeed = 0;
        double z = UnderworldTerrain.FERRY_Z;
        setPos(UnderworldTerrain.riverCenter(z), UnderworldTerrain.WATER_Y + .65, z);
        setYRot(0F);
        setXRot(0F);
        entityData.set(ROLL, 0F);
        entityData.set(PITCH, 0F);
        previousVisualRoll = visualRoll = 0F;
        previousVisualPitch = visualPitch = 0F;
        entityData.set(SAILING, false);
        entityData.set(EMERGENCE, EMERGENCE_TICKS);
        entityData.set(PAID, "");
        departureWait = 40;
        returnWait = 0;
    }

    // The supplied model's main deck ends at 21 model pixels (16 pixels per block).
    public double deckY() { return deckHeightAt(getX(), getZ()); }
    public float rockingRoll() { return entityData.get(ROLL); }
    public float rockingRoll(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, previousVisualRoll, visualRoll);
    }
    public float rockingPitch(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, previousVisualPitch, visualPitch);
    }

    public double deckHeightAt(double x, double z) {
        double yaw = Math.toRadians(getYRot());
        double dx = x - getX(), dz = z - getZ();
        double localX = -dx * Math.cos(yaw) - dz * Math.sin(yaw);
        double localZ = dx * Math.sin(yaw) - dz * Math.cos(yaw);
        double pitch = Math.toRadians(entityData.get(PITCH)), roll = Math.toRadians(rockingRoll());
        return getY() + FerryHull.PIVOT + (FerryHull.DECK - FerryHull.PIVOT + FerryHull.RENDER_OFFSET) / (Math.cos(pitch) * Math.cos(roll))
                + localX * Math.tan(roll) / Math.cos(pitch) - localZ * Math.tan(pitch);
    }

    @Override protected boolean canAddPassenger(Entity passenger) {
        return (passenger instanceof CharonEntity || passenger instanceof Player) && getPassengers().isEmpty();
    }

    @Override protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!(passenger instanceof CharonEntity) && !(passenger instanceof Player)) {
            super.positionRider(passenger, move);
            return;
        }
        // Charon keeps the stern; the temporary player control seat sits centrally.
        Vec3 seat = deckPoint(0, passenger instanceof Player ? -.15 : 1.0);
        move.accept(passenger, seat.x, seat.y, seat.z);
        // The player's look yaw is the steering input; only lock Charon to the hull.
        if (passenger instanceof CharonEntity) passenger.setYRot(getYRot());
        passenger.resetFallDistance();
    }
    public boolean playerControlled() { return getFirstPassenger() instanceof Player; }
    public boolean beginPlayerControl(Player player) {
        if (level().isClientSide() || playerControlled()) return false;
        for (Entity passenger : java.util.List.copyOf(getPassengers())) {
            passenger.stopRiding();
            if (passenger instanceof CharonEntity) passenger.setInvisible(true);
        }
        entityData.set(SAILING, false);
        return player.startRiding(this);
    }
    public void finishPlayerControl() {
        if (level().isClientSide()) return;
        entityData.set(SAILING, false);
        if (level() instanceof ServerLevel server
                && server.getEntity(CharonEntity.SHARED_ID) instanceof CharonEntity charon) {
            charon.setInvisible(false);
            if (!charon.isPassenger() && getPassengers().isEmpty()) charon.startRiding(this);
        }
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
        data.define(ROLL, 0F);
        data.define(PITCH, 0F);
    }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean canBeCollidedWith(Entity other) { return false; }
    @Override public boolean canCollideWith(Entity other) { return false; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return false; }
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    public Vec3 deckPoint(double localX, double localZ) {
        return position().add(FerryHull.world(new Vec3(localX,FerryHull.DECK,localZ),getYRot(),entityData.get(PITCH),rockingRoll()));
    }
    public Vec3 deckLocal(double x,double z) {
        return FerryHull.local(new Vec3(x-getX(),deckHeightAt(x,z)-getY(),z-getZ()),getYRot(),entityData.get(PITCH),rockingRoll());
    }
    public boolean overlapsDeck(AABB bounds) {
        Vec3 p=deckLocal((bounds.minX+bounds.maxX)*.5,(bounds.minZ+bounds.maxZ)*.5);
        return FerryHull.deckDistance(p.x,p.z)<.03;
    }
    public Vec3 collideDeckMovement(Entity walker,Vec3 movement) {
        // A solid landing is an exit, not an invisible end rail.
        var landing=net.minecraft.core.BlockPos.containing(walker.getX()+movement.x,
                deckHeightAt(walker.getX()+movement.x,walker.getZ()+movement.z)-.15,walker.getZ()+movement.z);
        if(level().getBlockState(landing).isSolidRender())return movement;
        Vec3 start=deckLocal(walker.getX(),walker.getZ());
        Vec3 end=deckLocal(walker.getX()+movement.x,walker.getZ()+movement.z);
        double radius=Math.min(.30,walker.getBbWidth()*.45);
        // Do not push a newly boarding player backwards because their feet straddle an edge.
        radius=Math.min(radius,Math.max(0,-FerryHull.deckDistance(start.x,start.z)));
        Vec3 safe=FerryHull.constrain(start.x,start.z,end.x,end.z,radius);
        Vec3 target=deckPoint(safe.x,safe.z);
        return new Vec3(target.x-walker.getX(),movement.y,target.z-walker.getZ());
    }
    public static Vec3 collideNearbyHulls(Entity walker,Vec3 movement) {
        if(!walker.level().dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL)
                || walker.isSpectator() || walker instanceof Player p && p.getAbilities().flying)return movement;
        for(var boat:walker.level().getEntitiesOfClass(CharonsFerryEntity.class,walker.getBoundingBox().inflate(4))) {
            if(walker.getY()>=boat.deckHeightAt(walker.getX(),walker.getZ())-.65)continue;
            double centerOffset=Math.min(.6,walker.getBbHeight()*.5);
            Vec3 center=walker.position().add(0,centerOffset,0);
            Vec3 candidate=center.add(movement);
            Vec3 local=FerryHull.local(candidate.subtract(boat.position()),boat.getYRot(),boat.entityData.get(PITCH),boat.rockingRoll());
            if(local.y<3.0/16 || local.y>FerryHull.DECK)continue;
            double radius=walker.getBbWidth()*.48,distance=FerryHull.hullDistance(local.x,local.y,local.z);
            if(distance>=radius)continue;
            double gx=FerryHull.hullDistance(local.x+.01,local.y,local.z)-FerryHull.hullDistance(local.x-.01,local.y,local.z);
            double gz=FerryHull.hullDistance(local.x,local.y,local.z+.01)-FerryHull.hullDistance(local.x,local.y,local.z-.01);
            double length=Math.hypot(gx,gz);
            if(length<1e-8) { gx=local.x>=0?1:-1;gz=0;length=1; }
            double push=radius-distance+.015;
            Vec3 corrected=boat.position().add(FerryHull.world(local.add(gx/length*push,0,gz/length*push),
                    boat.getYRot(),boat.entityData.get(PITCH),boat.rockingRoll()));
            movement=new Vec3(corrected.x-center.x,movement.y,corrected.z-center.z);
        }
        return movement;
    }
    public boolean supports(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator() || entity.isPassenger()
                || entity instanceof Player player && player.getAbilities().flying) return false;
        double offset = entity.getY() - deckHeightAt(entity.getX(), entity.getZ());
        if (!entity.onGround() && offset > .06) return false;
        boolean recovering = carries(entity) && offset < 0 && offset >= -.28 && overlapsDeck(entity.getBoundingBox());
        return (Math.abs(offset) < .72 || recovering) && entity.getDeltaMovement().y <= .08
                && overlapsDeck(entity.getBoundingBox());
    }

    public static CharonsFerryEntity supporting(Entity entity) {
        for (CharonsFerryEntity ferry : entity.level().getEntitiesOfClass(CharonsFerryEntity.class,
                entity.getBoundingBox().inflate(4.0, 2.0, 4.0)))
            if (ferry.supports(entity)) return ferry;
        return null;
    }
    public static CharonsFerryEntity landing(Entity entity,Vec3 movement) {
        if(movement.y>=0 || !entity.level().dimension().equals(net.krodark.asterion.Asterion.LIMBO_LEVEL))return null;
        for(var boat:entity.level().getEntitiesOfClass(CharonsFerryEntity.class,
                entity.getBoundingBox().expandTowards(movement).inflate(4))) {
            double deck=boat.deckHeightAt(entity.getX()+movement.x,entity.getZ()+movement.z);
            if(entity.getY()>=boat.deckHeightAt(entity.getX(),entity.getZ())-.05
                    && entity.getY()+movement.y<=deck && boat.overlapsDeck(entity.getBoundingBox().move(movement)))return boat;
        }
        return null;
    }

    @Override public void tick() {
        super.tick();
        if (tickCount <= 1) {
            previousVisualRoll = visualRoll = rockingRoll();
            previousVisualPitch = visualPitch = entityData.get(PITCH);
        }
        previousVisualRoll = visualRoll;
        visualRoll = net.minecraft.util.Mth.lerp(.55F, visualRoll, rockingRoll());
        previousVisualPitch = visualPitch;
        visualPitch = net.minecraft.util.Mth.lerp(.55F, visualPitch, entityData.get(PITCH));
        if (level().isClientSide()) {
            double beforeX = getX(), beforeZ = getZ();
            float beforeYaw = getYRot();
            var supported = level().getEntities(this, getBoundingBox().inflate(1, 2.1, 1), this::supports);
            interpolation.interpolate();
            for (Entity walker : supported) {
                if (walker instanceof Player player && player.isLocalPlayer()) {
                    double turn = -Math.toRadians(net.minecraft.util.Mth.wrapDegrees(getYRot() - beforeYaw));
                    double rx = walker.getX() - beforeX, rz = walker.getZ() - beforeZ;
                    double x = getX() + rx * Math.cos(turn) - rz * Math.sin(turn);
                    double z = getZ() + rx * Math.sin(turn) + rz * Math.cos(turn);
                    walker.setYRot(walker.getYRot() + net.minecraft.util.Mth.wrapDegrees(getYRot() - beforeYaw));
                    walker.setPos(x, deckHeightAt(x, z), z);
                    walker.setOnGround(true);
                    walker.resetFallDistance();
                }
            }
            return;
        }
        if (!playerControlled() && level() instanceof ServerLevel server
                && server.getEntity(CharonEntity.SHARED_ID) instanceof CharonEntity charon
                && charon.isInvisible()) finishPlayerControl();
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
            // Moored ferries keep following the swell through the same buoyancy step below.
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

        if (emergenceTicks() < EMERGENCE_TICKS) return;
        double oldX = getX(), oldY = getY(), oldZ = getZ();
        double tangent = (UnderworldTerrain.riverCenter(oldZ + .5)
                - UnderworldTerrain.riverCenter(oldZ - .5));
        Player pilot = getFirstPassenger() instanceof Player player ? player : null;
        double desiredSpeed = pilot != null ? Math.clamp(pilot.zza, -1F, 1F) * .092 : sailing() ? .072 : 0;
        surgeSpeed += Math.clamp(desiredSpeed - surgeSpeed, -.0035, .0025);
        float pilotYaw = pilot == null ? getYRot() : pilot.getYRot();
        double nextZ = pilot == null ? Math.min(UnderworldTerrain.END_Z - 54,
                oldZ + surgeSpeed / Math.sqrt(1 + tangent * tangent))
                : oldZ + Math.cos(Math.toRadians(pilotYaw)) * surgeSpeed;
        double nextX = pilot == null ? (surgeSpeed > .0001 ? UnderworldTerrain.riverCenter(nextZ) : oldX)
                : oldX - Math.sin(Math.toRadians(pilotYaw)) * surgeSpeed;
        int sx = (int)Math.floor(nextX), sz = (int)Math.floor(nextZ);
        if (sx != shoreX || sz != shoreZ || tickCount % 20 == 0) {
            shoreX = sx; shoreZ = sz;
            shoreFactor = net.krodark.asterion.update.underworld.world.WaterShoreline.sample(level(), sx, sz);
        }
        double wave = UnderworldTerrain.waveHeight(nextX, nextZ, level().getGameTime());
        double bow = UnderworldTerrain.waveHeight(nextX - Math.sin(Math.toRadians(getYRot())) * 2.4,
                nextZ + Math.cos(Math.toRadians(getYRot())) * 2.4, level().getGameTime());
        double stern = UnderworldTerrain.waveHeight(nextX + Math.sin(Math.toRadians(getYRot())) * 3.2,
                nextZ - Math.cos(Math.toRadians(getYRot())) * 3.2, level().getGameTime());
        double port = UnderworldTerrain.waveHeight(nextX - Math.cos(Math.toRadians(getYRot())) * .7,
                nextZ - Math.sin(Math.toRadians(getYRot())) * .7, level().getGameTime());
        double starboard = UnderworldTerrain.waveHeight(nextX + Math.cos(Math.toRadians(getYRot())) * .7,
                nextZ + Math.sin(Math.toRadians(getYRot())) * .7, level().getGameTime());
        // Buoyancy follows the hull's footprint, smoothing little chop instead of snapping to one point.
        double targetY = UnderworldTerrain.WATER_Y + .65 + (wave * 2 + bow + stern + port + starboard) * shoreFactor / 6;
        // Damped vertical inertia: the displaced hull volume restores the waterline gradually.
        heaveSpeed = Math.clamp(heaveSpeed + (targetY - oldY) * .075 - heaveSpeed * .42, -.14, .14);
        double nextY = oldY + heaveSpeed;
        float oldYaw = getYRot();
        float targetYaw = pilot != null ? pilotYaw : sailing() ? (float)Math.toDegrees(Math.atan2(-tangent, 1.0)) : oldYaw;
        setYRot(oldYaw + net.minecraft.util.Mth.wrapDegrees(targetYaw - oldYaw) * (pilot != null ? .12F : .065F));
        entityData.set(PITCH, net.minecraft.util.Mth.lerp(.22F, entityData.get(PITCH),
                Math.clamp((float)Math.toDegrees(Math.atan2((bow - stern) * shoreFactor, 5.6)), -12F, 12F)));
        setXRot(entityData.get(PITCH));
        entityData.set(ROLL, net.minecraft.util.Mth.lerp(.2F, rockingRoll(),
                Math.clamp((float)Math.toDegrees(Math.atan2((port - starboard) * shoreFactor, 1.4)), -10F, 10F)));
        setPos(nextX, nextY, nextZ);
        if (tickCount % 20 == 0) net.krodark.asterion.update.underworld.FerryJourneyState.get((ServerLevel)level()).track(this);
        setDeltaMovement(Vec3.ZERO);

        Vec3 movement = new Vec3(nextX - oldX, nextY - oldY, nextZ - oldZ);
        for (Entity walker : walkers) {
            if (level().isClientSide() && !(walker instanceof Player player && player.isLocalPlayer())) continue;
            double turn = -Math.toRadians(net.minecraft.util.Mth.wrapDegrees(getYRot() - oldYaw));
            walker.setYRot(walker.getYRot() + net.minecraft.util.Mth.wrapDegrees(getYRot() - oldYaw));
            double relativeX = walker.getX() - oldX, relativeZ = walker.getZ() - oldZ;
            double x = nextX + relativeX * Math.cos(turn) - relativeZ * Math.sin(turn);
            double z = nextZ + relativeX * Math.sin(turn) + relativeZ * Math.cos(turn);
            walker.setPos(x, deckHeightAt(x, z), z);
            if (walker.getDeltaMovement().y < 0)
                walker.setDeltaMovement(walker.getDeltaMovement().multiply(1, 0, 1));
            walker.setOnGround(true);
            walker.resetFallDistance();
        }
        if (pilot == null && nextZ >= UnderworldTerrain.END_Z - 54) entityData.set(SAILING, false);
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
