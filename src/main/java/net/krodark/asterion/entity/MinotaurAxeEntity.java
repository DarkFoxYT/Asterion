package net.krodark.asterion.entity;

import net.krodark.asterion.AsterionConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

/** Persistent, server-authoritative weapon body. Never becomes a placed block or collectible item. */
public final class MinotaurAxeEntity extends Entity {
    public static final double GRIP_Y = 45 / 16.0;
    private static final double MODEL_MIN_Y = 15 - 6 * Math.sqrt(2);
    public static final double CENTER_Y = (114 + MODEL_MIN_Y) / 32.0;
    private static final EntityDataAccessor<Quaternionfc> ROTATION = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> THROWER = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.INT);
    private static final Vec3[] WORLD_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 2);
    private final Quaternionf rotation = new Quaternionf(), previousRotation = new Quaternionf();
    private Vec3 spin = Vec3.ZERO;
    private int quietTicks, impactCooldown;
    private boolean sleeping;
    private final java.util.Set<java.util.UUID> hitPlayers = new java.util.HashSet<>();

    public MinotaurAxeEntity(EntityType<? extends MinotaurAxeEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(ROTATION, new Quaternionf());
        data.define(SCALE, .47F * AsterionConfig.INSTANCE.minotaurScale);
        data.define(THROWER, -1);
    }
    public float modelScale() { return entityData.get(SCALE); }
    public Quaternionf renderRotation(float partial) { return new Quaternionf(previousRotation).slerp(rotation, partial); }
    public boolean sleeping() { return sleeping; }
    public int throwerId() { return entityData.get(THROWER); }
    public void setThrower(MinotaurEntity boss) { entityData.set(THROWER, boss.getId()); }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
    // The recoverable weapon must not be launched away by environmental/boss blasts.
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    public void launch(Vec3 origin, Vec3 velocity, float yaw) {
        setPos(origin);
        hitPlayers.clear();
        setDeltaMovement(velocity);
        rotation.rotationY((float)Math.toRadians(-yaw));
        previousRotation.set(rotation);
        // End-over-end rotation in the blade's plane, with a small natural wobble.
        Vector3f axis = rotation.transform(new Vector3f(0, 0, .56F));
        spin = new Vec3(axis.x, .025, axis.z);
        entityData.set(ROTATION, new Quaternionf(rotation));
        sleeping = false;
    }

    public void launchAimed(Vec3 origin, Vec3 target, float yaw, double flightTicks) {
        double drag = -Math.log(.996);
        double travel = (1 - Math.exp(-drag * flightTicks)) / drag;
        Vec3 velocity = target.subtract(origin).add(0, .075 * (flightTicks - travel) / drag, 0).scale(1 / travel);
        launch(origin, velocity, yaw);
        Vec3 direction = target.subtract(origin);
        // Put the blade's plane along its flight, so it tumbles edge-first rather than like a propeller.
        rotation.rotationY((float)(Math.atan2(direction.x, direction.z) - Math.PI / 2));
        previousRotation.set(rotation);
        entityData.set(ROTATION, new Quaternionf(rotation));
        // Arrive with the cutting head pointing down at chest height, rather than burying the handle early.
        double spinTravel = (1 - Math.pow(.994, flightTicks)) / -Math.log(.994);
        int turns = Math.max(0, (int)Math.round((.56 * spinTravel - Math.PI) / (Math.PI * 2)));
        float angularSpeed = (float)((Math.PI + turns * Math.PI * 2) / spinTravel);
        Vector3f axis = rotation.transform(new Vector3f(0, 0, angularSpeed));
        spin = new Vec3(axis.x, 0, axis.z);
    }

    @Override public void tick() {
        super.tick();
        previousRotation.set(rotation);
        if (level().isClientSide()) {
            interpolation.interpolate();
            rotation.set(entityData.get(ROTATION));
            return;
        }
        if (impactCooldown > 0) impactCooldown--;
        // A removed support wakes a settled weapon; a sleeping body otherwise costs one query.
        if (sleeping && getDeltaMovement().lengthSqr() < 1e-8 && contact(position().add(0, -.04, 0)) != null) return;
        sleeping = false;
        Vec3 velocity = getDeltaMovement();
        if (velocity.lengthSqr() > 9) velocity = velocity.normalize().scale(3);
        if (spin.lengthSqr() > 1) spin = spin.normalize();
        int steps = Math.max(4, Math.min(24, (int)Math.ceil((velocity.length() + spin.length() * half().length()) / .12)));
        double dt = 1.0 / steps, strongest = 0;
        boolean supported = false;
        Vec3 center = position();
        var server = (ServerLevel)level();
        var victims = velocity.lengthSqr() > .10 ? server.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                bounds(center).inflate(velocity.length() + half().length()), p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
                : java.util.List.<net.minecraft.server.level.ServerPlayer>of();
        for (int step = 0; step < steps; step++) {
            velocity = velocity.add(0, -.075 * dt, 0).scale(Math.pow(.996, dt));
            spin = spin.scale(Math.pow(.994, dt));
            center = center.add(velocity.scale(dt));
            rotation.premul(new Quaternionf().rotationXYZ((float)(spin.x * dt), (float)(spin.y * dt), (float)(spin.z * dt))).normalize();
            hitPlayers(server, victims, center, velocity);
            for (int iteration = 0; iteration < 6; iteration++) {
                Contact contact = contact(center);
                if (contact == null) break;
                Vec3 normal = contact.normal;
                center = center.add(normal.scale(contact.depth + .0006));
                Vec3 lever = contact.point.subtract(center);
                double speed = velocity.add(spin.cross(lever)).dot(normal);
                supported |= normal.y > .55;
                strongest = Math.max(strongest, -speed);
                if (speed >= 0) continue;
                double impulse = -(1 + (speed < -.2 ? .14 : 0)) * speed / effectiveMass(lever, normal);
                Vec3 force = normal.scale(impulse);
                velocity = velocity.add(force);
                spin = spin.add(inverseInertia(lever.cross(force)));
                Vec3 contactVelocity = velocity.add(spin.cross(lever));
                Vec3 tangent = contactVelocity.subtract(normal.scale(contactVelocity.dot(normal)));
                double sliding = tangent.length();
                if (sliding > 1e-6) {
                    tangent = tangent.scale(1 / sliding);
                    Vec3 friction = tangent.scale(-Math.min(.62 * impulse, sliding / effectiveMass(lever, tangent)));
                    velocity = velocity.add(friction);
                    spin = spin.add(inverseInertia(lever.cross(friction)));
                }
            }
        }
        if (supported) spin = spin.scale(.92);
        boolean slow = velocity.horizontalDistanceSqr() < .0025 && Math.abs(velocity.y) < .09 && spin.lengthSqr() < .004;
        quietTicks = slow && (supported || contact(center.add(0, -.04, 0)) != null) ? quietTicks + 1 : 0;
        if (quietTicks > 12) { sleeping = true; velocity = Vec3.ZERO; spin = Vec3.ZERO; }
        setPos(center);
        setDeltaMovement(velocity);
        entityData.set(ROTATION, new Quaternionf(rotation));
        if (strongest > .3 && impactCooldown == 0) {
            impactCooldown = 8;
            server.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, .65F);
            server.sendParticles(ParticleTypes.POOF, getX(), getY() - bounds(center).getYsize() * .4, getZ(), 10, .4, .12, .4, .025);
        }
    }

    private void hitPlayers(ServerLevel server, java.util.List<net.minecraft.server.level.ServerPlayer> victims,
                            Vec3 center, Vec3 velocity) {
        if (velocity.lengthSqr() < .10) return;
        Vec3[] axes = axes();
        float scale = modelScale();
        // Separate the broad cutting head from the narrow handle; sample every physics substep.
        Vec3 blade = center.add(axes[1].scale(2.05 * scale));
        Vec3 handle = center.add(axes[1].scale(-1.25 * scale));
        for (var victim : victims) {
            if (hitPlayers.contains(victim.getUUID())) continue;
            boolean edge = overlaps(blade, new Vec3(2, 1.30, .20).scale(scale), axes, victim.getBoundingBox());
            if (!edge && !overlaps(handle, new Vec3(.24, 2.0, .20).scale(scale), axes, victim.getBoundingBox())) continue;
            Vec3 closest = victim.getBoundingBox().getCenter();
            if (server.clip(new net.minecraft.world.level.ClipContext(center, closest,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this))
                    .getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            Entity owner = server.getEntity(throwerId());
            var damage = owner instanceof net.minecraft.world.entity.LivingEntity living
                    ? damageSources().mobProjectile(this, living) : damageSources().generic();
            if (victim.hurtServer(server, damage, edge ? 20F : 10F)) {
                hitPlayers.add(victim.getUUID());
                Vec3 impulse = velocity.normalize().scale(edge ? 1.25 : .65).add(0, .25, 0);
                victim.setDeltaMovement(victim.getDeltaMovement().add(impulse));
                victim.hurtMarked = true;
                server.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.4F, .7F);
                server.sendParticles(ParticleTypes.CRIT, closest.x, closest.y, closest.z, 12, .3, .4, .3, .08);
            }
        }
    }

    private static boolean overlaps(Vec3 center, Vec3 half, Vec3[] axes, AABB box) {
        Vec3 delta = box.getCenter().subtract(center);
        Vec3 other = new Vec3(box.getXsize()/2, box.getYsize()/2, box.getZsize()/2);
        Vec3[] candidates = new Vec3[15];
        System.arraycopy(axes, 0, candidates, 0, 3);
        System.arraycopy(WORLD_AXES, 0, candidates, 3, 3);
        int i = 6;
        for (Vec3 a : axes) for (Vec3 b : WORLD_AXES) candidates[i++] = a.cross(b);
        for (Vec3 axis : candidates) {
            if (axis.lengthSqr() < 1e-10) continue;
            if (Math.abs(delta.dot(axis)) > radius(half, axes, axis) + radius(other, WORLD_AXES, axis)) return false;
        }
        return true;
    }

    // Include the blade plane and the rotated pommel, rather than using an item-sized hitbox.
    private Vec3 half() { return new Vec3(2, (114 - MODEL_MIN_Y) / 32.0, 2.75 / 16).scale(modelScale()); }
    private Vec3[] axes() {
        Vec3[] result = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            Vec3 v = WORLD_AXES[i];
            Vector3f axis = rotation.transform(new Vector3f((float)v.x, (float)v.y, (float)v.z));
            result[i] = new Vec3(axis.x, axis.y, axis.z);
        }
        return result;
    }
    private AABB bounds(Vec3 center) {
        Vec3[] axes = axes(); Vec3 half = half();
        double x = radius(half, axes, WORLD_AXES[0]), y = radius(half, axes, WORLD_AXES[1]), z = radius(half, axes, WORLD_AXES[2]);
        return new AABB(center.x - x, center.y - y, center.z - z, center.x + x, center.y + y, center.z + z);
    }
    @Override protected AABB makeBoundingBox(Vec3 position) {
        return rotation == null ? super.makeBoundingBox(position) : bounds(position);
    }
    private record Contact(Vec3 normal, double depth, Vec3 point) { }
    private Contact contact(Vec3 center) {
        Vec3[] axes = axes(); Vec3 half = half();
        Contact deepest = null;
        for (var shape : level().getBlockCollisions(this, bounds(center).deflate(.00035))) for (AABB box : shape.toAabbs()) {
            Vec3 delta = box.getCenter().subtract(center);
            Vec3 boxHalf = new Vec3(box.getXsize() / 2, box.getYsize() / 2, box.getZsize() / 2);
            Vec3[] candidates = new Vec3[15];
            System.arraycopy(axes, 0, candidates, 0, 3); System.arraycopy(WORLD_AXES, 0, candidates, 3, 3);
            int i = 6; for (Vec3 a : axes) for (Vec3 b : WORLD_AXES) candidates[i++] = a.cross(b);
            double depth = Double.POSITIVE_INFINITY; Vec3 normal = null;
            for (Vec3 raw : candidates) {
                if (raw.lengthSqr() < 1e-10) continue;
                Vec3 axis = raw.normalize();
                double overlap = radius(half, axes, axis) + radius(boxHalf, WORLD_AXES, axis) - Math.abs(delta.dot(axis));
                if (overlap <= .00045) { normal = null; break; }
                if (overlap < depth) { depth = overlap; normal = axis.scale(delta.dot(axis) < 0 ? 1 : -1); }
            }
            if (normal == null || deepest != null && depth <= deepest.depth) continue;
            Vec3 point = center;
            double[] extents = {half.x, half.y, half.z};
            for (i = 0; i < 3; i++) {
                double dot = axes[i].dot(normal);
                if (Math.abs(dot) > .001) point = point.add(axes[i].scale(-Math.signum(dot) * extents[i]));
            }
            point = new Vec3(Math.clamp(point.x, box.minX, box.maxX), Math.clamp(point.y, box.minY, box.maxY), Math.clamp(point.z, box.minZ, box.maxZ));
            deepest = new Contact(normal, depth, point);
        }
        return deepest;
    }
    private static double radius(Vec3 half, Vec3[] axes, Vec3 dir) {
        return half.x * Math.abs(axes[0].dot(dir)) + half.y * Math.abs(axes[1].dot(dir)) + half.z * Math.abs(axes[2].dot(dir));
    }
    private Vec3 inverseInertia(Vec3 torque) {
        Vector3f local = new Quaternionf(rotation).conjugate().transform(new Vector3f((float)torque.x, (float)torque.y, (float)torque.z));
        Vec3 h = half();
        local.set((float)(3 * local.x / (h.y*h.y + h.z*h.z)), (float)(3 * local.y / (h.x*h.x + h.z*h.z)), (float)(3 * local.z / (h.x*h.x + h.y*h.y)));
        rotation.transform(local);
        return new Vec3(local.x, local.y, local.z);
    }
    private double effectiveMass(Vec3 lever, Vec3 normal) { return 1 + normal.dot(inverseInertia(lever.cross(normal)).cross(lever)); }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        out.putFloat("qx", rotation.x); out.putFloat("qy", rotation.y); out.putFloat("qz", rotation.z); out.putFloat("qw", rotation.w);
        out.putDouble("spin_x", spin.x); out.putDouble("spin_y", spin.y); out.putDouble("spin_z", spin.z);
        out.putFloat("axe_scale", modelScale()); out.putBoolean("sleeping", sleeping);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        rotation.set(in.getFloatOr("qx", 0), in.getFloatOr("qy", 0), in.getFloatOr("qz", 0), in.getFloatOr("qw", 1));
        if (!rotation.isFinite() || rotation.lengthSquared() < .001) rotation.identity(); else rotation.normalize();
        previousRotation.set(rotation);
        spin = new Vec3(in.getDoubleOr("spin_x", 0), in.getDoubleOr("spin_y", 0), in.getDoubleOr("spin_z", 0));
        if (!Double.isFinite(spin.lengthSqr())) spin = Vec3.ZERO;
        entityData.set(SCALE, Math.clamp(in.getFloatOr("axe_scale", .94F), .3525F, 1.88F));
        entityData.set(ROTATION, new Quaternionf(rotation));
        sleeping = in.getBooleanOr("sleeping", false);
    }
}
