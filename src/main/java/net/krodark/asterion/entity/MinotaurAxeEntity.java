package net.krodark.asterion.entity;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.network.DazePayload;
import net.krodark.asterion.network.ragdoll.RagdollImpulsePayload;
import net.krodark.asterion.network.ragdoll.RagdollServerNetworking;
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

 
public final class MinotaurAxeEntity extends Entity {
    public static final double GRIP_Y = 45 / 16.0;
    private static final double MODEL_MIN_Y = -6 * Math.sqrt(2);
    public static final double CENTER_Y = (99 + MODEL_MIN_Y) / 32.0;
    private static final double SWORD_MIN_Y = -13 - 6 * Math.sqrt(2);
    public static final double SWORD_CENTER_Y = (78 + SWORD_MIN_Y) / 32.0 + 6 / 16.0;
    private static final EntityDataAccessor<Boolean> SWORD = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Quaternionfc> ROTATION = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> THROWER = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.INT);
    private static final Vec3[] WORLD_AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
    private final InterpolationHandler interpolation = new InterpolationHandler(this, 2);
    private final Quaternionf rotation = new Quaternionf(), previousRotation = new Quaternionf();
    private final Quaternionf colliderRotation = new Quaternionf();
    private Vec3 colliderPosition;
    private boolean colliderSword;
    private float colliderScale;
    private java.util.List<net.krodark.asterion.physics.ModelCollider.Box> localColliders,worldColliders;
    private AABB localColliderBounds,worldColliderBounds;
    private Vec3 spin = Vec3.ZERO;
    private int quietTicks, impactCooldown;
    private boolean sleeping;
    private boolean harmless;
    private boolean freeFlight;
    private boolean embedded;
    private static final EntityDataAccessor<Integer> IMPALED = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.INT);
    private int impaleTicks;
    private void releaseImpale() { entityData.set(IMPALED, -1); impaleTicks = 0; }
    private Vec3 embeddedSupport = Vec3.ZERO;
    private Vec3 sweepTarget;
    private Quaternionf sweepRotation;
    private final java.util.ArrayDeque<Vec3[]> trail = new java.util.ArrayDeque<>();
    public Vec3[][] trailPoints(float partial) {
        var result=new java.util.ArrayList<Vec3[]>(trail);
        if(!result.isEmpty()) {
            Vec3[] latest=result.getLast(), previous=result.size()>1?result.get(result.size()-2):latest;
            result.set(result.size()-1,new Vec3[]{previous[0].lerp(latest[0],partial),previous[1].lerp(latest[1],partial)});
        }
        return result.toArray(Vec3[][]::new);
    }
    private void tickWeaponEffects() {
        Vec3[] axes=axes();
        double speed=position().distanceTo(new Vec3(xo,yo,zo));
        if(speed<.025 || tickCount<2) {trail.clear();return;}
        Vec3 root,tip;
        if(isSword()) {
            root=position().add(axes[1].scale((25/16.0-modelCenterY())*modelScale()));
            tip=position().add(axes[1].scale((84/16.0-modelCenterY())*modelScale()));
        }else {
            Vec3 head=position().add(axes[1].scale((80/16.0-modelCenterY())*modelScale()));
            root=head.add(axes[0].scale(-1.6*modelScale()));tip=head.add(axes[0].scale(1.6*modelScale()));
        }
        if(!trail.isEmpty() && trail.getLast()[1].distanceToSqr(tip)>144)trail.clear();
        trail.addLast(new Vec3[]{root,tip});while(trail.size()>9)trail.removeFirst();
        if(speed>.15 && tickCount%2==0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK,tip.x,tip.y,tip.z,0,.015,0);
            level().addParticle(isSword()?ParticleTypes.ENCHANTED_HIT:ParticleTypes.CRIT,root.x,root.y,root.z,0,.01,0);
        }
    }
    private static final EntityDataAccessor<Integer> CHAIN_SIDE = SynchedEntityData.defineId(MinotaurAxeEntity.class, EntityDataSerializers.INT);
    private java.util.UUID ownerUUID;
    private boolean returning;
    private int returnTicks;
    private net.krodark.asterion.physics.SegmentedChain chain;
    public int chainSide() { return entityData.get(CHAIN_SIDE); }
    public void tether(MinotaurEntity owner, int side, boolean sword) {
        setThrower(owner); ownerUUID = owner.getUUID();
        entityData.set(CHAIN_SIDE, sword ? side : 0); entityData.set(SWORD, sword);
        if (!sword) { ownerUUID=null; returning=false; }
    }
    public void recall() {
        if (!isSword() || chainSide()==0) return;
        if (!returning) returnTicks = 0;
        returning = true; sleeping = false; embedded=false; sweepTarget=null;sweepRotation=null;
    }
    public void sweepToward(Vec3 target) {
        if (!isSword() || chainSide()==0 || returning) return;
        sweepTarget=target; sleeping=false; embedded=false; harmless=false;
    }
    public void sweepToward(Vec3 target,Quaternionf orientation) {
        sweepToward(target);sweepRotation=orientation;
    }
    public boolean embedded() { return embedded; }
    public Vec3 chainAnchor() {
        Entity owner = level().getEntity(throwerId());
        if (!(owner instanceof MinotaurEntity boss)) return position();
        Vec3 forward = Vec3.directionFromRotation(0, boss.yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return boss.position().add(right.scale(boss.getBbWidth() * .65 * chainSide()))
                .add(forward.scale(.7)).add(0, boss.getBbHeight() * .72, 0);
    }
    public Vec3[] chainPoints(float partial) { return chain == null ? null : chain.rendered(partial); }
    public boolean climbableNear(Vec3 body) { return chainSide() != 0 && chain != null && chain.near(body, .75); }
    public Vec3 chainGripPoint(Vec3 body) { return chainSide()!=0 && chain!=null?chain.closestPoint(body):null; }
    @Override public boolean isPickable() { return chainSide()!=0; }
    @Override public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, Vec3 hit) {
        return net.krodark.asterion.physics.ChainGrip.grab(player,this)
                ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS;
    }
    private void tickChain() {
        if (chainSide() == 0 || !(level().getEntity(throwerId()) instanceof MinotaurEntity)) return;
        Vec3 start = chainAnchor();
        Vector3f pommel = rotation.transform(new Vector3f(0, (float)(-half().y * .9), 0));
        Vec3 end = position().add(pommel.x, pommel.y, pommel.z);
        double length = Math.max(start.distanceTo(end) + .6, 2);
        if (chain == null) chain = new net.krodark.asterion.physics.SegmentedChain(start, end, 64, length);
        chain.payout(length);
        chain.step(start, end, true, (from, to) -> {
            var fromBlock = net.minecraft.core.BlockPos.containing(from);
            if (fromBlock.equals(net.minecraft.core.BlockPos.containing(to)) && level().getBlockState(fromBlock).isAir()) return to;
            var hit = level().clip(new net.minecraft.world.level.ClipContext(from, to,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to
                    : hit.getLocation().add(hit.getDirection().getUnitVec3().scale(.025));
        }, level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
                new AABB(start, end).inflate(1), p -> !p.isSpectator()
                        && !net.krodark.asterion.physics.ChainGrip.holds(p,this)).stream()
                .map(p -> p.position().add(0, .9, 0)).toList());
    }
    private final java.util.Set<java.util.UUID> hitPlayers = new java.util.HashSet<>();

    public MinotaurAxeEntity(EntityType<? extends MinotaurAxeEntity> type, Level level) { super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder data) {
        data.define(ROTATION, new Quaternionf());
        data.define(SCALE, .47F * AsterionConfig.INSTANCE.minotaurScale);
        data.define(THROWER, -1);
        data.define(SWORD, false);
        data.define(CHAIN_SIDE, 0);
        data.define(IMPALED, -1);
    }
    public float modelScale() { return entityData.get(SCALE); }
    public boolean isSword() { return entityData.get(SWORD); }
    public double modelCenterY() { return isSword() ? SWORD_CENTER_Y : CENTER_Y; }
    public void disarm() { harmless = true; }
    public void drop(Vec3 origin, Vec3 velocity, float yaw, boolean sword, int side) {
        entityData.set(SWORD, sword);
        launch(origin, velocity, yaw);
        harmless = true;
        rotation.rotateZ((float)Math.toRadians(sword ? side * 18 : 45));
        if (!sword) rotation.rotateY((float)Math.PI / 2);
        previousRotation.set(rotation);
        entityData.set(ROTATION, new Quaternionf(rotation));
        spin = new Vec3(.06 * side, .025, .09 * side);
        setPos(origin);
    }
    public Quaternionf renderRotation(float partial) { return new Quaternionf(previousRotation).slerp(rotation, partial); }
    public boolean sleeping() { return sleeping; }
    public int throwerId() { return entityData.get(THROWER); }
    public void setThrower(MinotaurEntity boss) { entityData.set(THROWER, boss.getId()); }
    @Override public InterpolationHandler getInterpolation() { return interpolation; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }
     
    @Override public boolean ignoreExplosion(net.minecraft.world.level.Explosion explosion) { return true; }

    public void launch(Vec3 origin, Vec3 velocity, float yaw) {
        releaseImpale();
        freeFlight = true;
        embedded=false; sweepTarget=null;sweepRotation=null;
        setPos(origin);
        hitPlayers.clear();
        setDeltaMovement(velocity);
        rotation.rotationY((float)Math.toRadians(-yaw));
        previousRotation.set(rotation);
         
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
        if(isSword()) {
            rotation.set(net.krodark.asterion.physics.SwordAttackMotion.flightRotation(velocity));
            previousRotation.set(rotation);entityData.set(ROTATION,new Quaternionf(rotation));
            spin=Vec3.ZERO;return;
        }
         
        rotation.rotationY((float)(Math.atan2(direction.x, direction.z) - Math.PI / 2));
        previousRotation.set(rotation);
        entityData.set(ROTATION, new Quaternionf(rotation));
         
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
            tickChain();
            tickWeaponEffects();
            return;
        }
        if (entityData.get(IMPALED) >= 0 && level() instanceof ServerLevel server) {
            Entity target = server.getEntity(entityData.get(IMPALED));
            Entity owner = server.getEntity(throwerId());
            if (!(target instanceof net.minecraft.server.level.ServerPlayer victim) || !victim.isAlive()
                    || victim.isSpectator() || victim.isCreative() || !(owner instanceof MinotaurEntity boss)
                    || boss.isDefeatedBoss() || ++impaleTicks > 100) releaseImpale();
            else {
                Vec3 torso = victim.position().add(0, victim.getBbHeight() * .55, 0);
                if (returning) {
                    Vec3 toward = chainAnchor().subtract(torso);
                    if (toward.lengthSqr() < 16) { releaseImpale(); }
                    else {
                        Vec3 pull = toward.normalize().scale(Math.min(.85, toward.length() * .09));
                        victim.setDeltaMovement(victim.getDeltaMovement().lerp(pull, .35));
                        victim.hurtMarked = true; victim.resetFallDistance();
                        RagdollServerNetworking.suppressThrowFallDamage(victim, 40);
                    }
                }
                if (entityData.get(IMPALED) >= 0) {
                    entityData.set(ROTATION, new Quaternionf(rotation));
                    setPos(torso); setDeltaMovement(Vec3.ZERO); spin=Vec3.ZERO;
                    tickChain(); return;
                }
            }
        }
        if (ownerUUID != null && level() instanceof ServerLevel server) {
            Entity owner = server.getEntity(ownerUUID);
            if (owner instanceof MinotaurEntity boss && !boss.isDefeatedBoss()) {
                if (throwerId() != boss.getId()) setThrower(boss);
                Vec3 offset = chainAnchor().subtract(position());
                if (offset.lengthSqr() > 32 * 32 || tickCount > 240) recall();
                if (returning) {
                    harmless = true; sleeping = false;
                    if (offset.lengthSqr() < 1.2 || ++returnTicks > 160) {
                        boss.catchChainedWeapon(this); discard(); return;
                    }
                    Vec3 desired = offset.normalize().scale(Math.min(2.5, offset.length() * .4));
                    setDeltaMovement(getDeltaMovement().lerp(desired, .48).add(0, .075, 0));
                    spin = spin.scale(.8);
                    if (offset.lengthSqr() < 36) rotation.slerp(new Quaternionf().rotationY(
                            -boss.yBodyRot * (float)Math.PI / 180), .16F).normalize();
                } else if (sweepTarget != null) {
                    Vec3 toward=sweepTarget.subtract(position());
                    setDeltaMovement(getDeltaMovement().scale(.70).add(toward.scale(.18)).add(0,.075,0));
                    spin=Vec3.ZERO;
                    if(sweepRotation!=null)rotation.slerp(sweepRotation,.25F).normalize();
                } else if (offset.lengthSqr() > 28 * 28) {
                    Vec3 inward = offset.normalize();
                    Vec3 velocity = getDeltaMovement();
                    setDeltaMovement(velocity.add(inward.scale(Math.max(0, -velocity.dot(inward)) + .12)));
                }
            } else if (owner == null && tickCount < 100) {
                // The owner chunk can arrive after its projectile when loading a save.
            } else {
                ownerUUID = null; entityData.set(CHAIN_SIDE, 0); harmless = true;
            }
        }
        if (impactCooldown > 0) impactCooldown--;
        if (embedded) {
            if (level().getBlockState(net.minecraft.core.BlockPos.containing(embeddedSupport)).getCollisionShape(level(),
                    net.minecraft.core.BlockPos.containing(embeddedSupport)).isEmpty()) { embedded=false; sleeping=false; }
            else { setDeltaMovement(Vec3.ZERO); spin=Vec3.ZERO; tickChain(); return; }
        }
         
        if (sleeping && getDeltaMovement().lengthSqr() < 1e-8 && contact(position().add(0, -.04, 0)) != null) {
            tickChain(); return;
        }
        sleeping = false;
        Vec3 velocity = getDeltaMovement();
        if (isSword() && freeFlight && sweepTarget == null && !returning && velocity.lengthSqr() > .01)
            rotation.slerp(net.krodark.asterion.physics.SwordAttackMotion.flightRotation(velocity), .28F).normalize();
        if (velocity.lengthSqr() > 9) velocity = velocity.normalize().scale(3);
        if (spin.lengthSqr() > 1) spin = spin.normalize();
        int steps = Math.max(4, Math.min(24, (int)Math.ceil((velocity.length() + spin.length() * half().length()) / .12)));
        double dt = 1.0 / steps, strongest = 0;
        boolean supported = false;
        Vec3 center = position();
        var server = (ServerLevel)level();
        var victims = !harmless && velocity.lengthSqr() > .10 ? server.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                bounds(center).inflate(velocity.length() + half().length()), p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
                : java.util.List.<net.minecraft.server.level.ServerPlayer>of();
        flight: for (int step = 0; step < steps; step++) {
            velocity = velocity.add(0, -.075 * dt, 0).scale(Math.pow(.996, dt));
            spin = spin.scale(Math.pow(.994, dt));
            center = center.add(velocity.scale(dt));
            rotation.premul(new Quaternionf().rotationXYZ((float)(spin.x * dt), (float)(spin.y * dt), (float)(spin.z * dt))).normalize();
            hitPlayers(server, victims, center, velocity);
            if (entityData.get(IMPALED) >= 0) { velocity=Vec3.ZERO; spin=Vec3.ZERO; break flight; }
            for (int iteration = 0; iteration < 6; iteration++) {
                Contact contact = contact(center);
                if (contact == null) break;
                freeFlight = false;
                Vec3 normal = contact.normal;
                center = center.add(normal.scale(contact.depth + .0006));
                Vec3 lever = contact.point.subtract(center);
                double speed = velocity.add(spin.cross(lever)).dot(normal);
                // Only blade cubes can bite into a surface; never the shaft or pommel.
                if (!isSword() && contact.blade && !returning && (speed < -.22 || quietTicks>4)
                        && axes()[1].dot(normal)<-.3) {
                    center=center.subtract(normal.scale(Math.clamp(-speed * .12, .12, .32)));
                    embeddedSupport=contact.point.subtract(normal.scale(.08));
                    if (normal.y > .7 && server.getFluidState(net.minecraft.core.BlockPos.containing(embeddedSupport)).isEmpty()) {
                        Vec3 cut = new Vec3(velocity.x, 0, velocity.z);
                        if (cut.lengthSqr() < .001) cut = new Vec3(axes()[0].x, 0, axes()[0].z);
                        cut = cut.normalize().scale(.8);
                        net.krodark.asterion.game.WeaponScars.line(server, contact.point.subtract(cut), contact.point.add(cut));
                    }
                    embedded=true; sleeping=true; velocity=Vec3.ZERO; spin=Vec3.ZERO;
                    strongest=Math.max(strongest,-speed); break flight;
                }
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
        if (supported) {
            spin = spin.scale(.92);
            // A loose axe rolls onto its heavy head instead of sleeping on a huge invisible slab.
            if (!isSword() && !embedded && velocity.lengthSqr()<.05) {
                Vec3 bladeAxis=axes()[1];
                spin=spin.add(bladeAxis.cross(new Vec3(0,-1,0)).scale(.035));
                if(bladeAxis.y>.98)spin=spin.add(axes()[2].scale(.025));
            }
        }
        boolean slow = velocity.horizontalDistanceSqr() < .0025 && Math.abs(velocity.y) < .09 && spin.lengthSqr() < .004;
        quietTicks = slow && (supported || contact(center.add(0, -.04, 0)) != null) ? quietTicks + 1 : 0;
        if (quietTicks > 12 && (isSword() || embedded)) { sleeping = true; velocity = Vec3.ZERO; spin = Vec3.ZERO; }
        setPos(center);
        setDeltaMovement(velocity);
        entityData.set(ROTATION, new Quaternionf(rotation));
        tickChain();
        if (strongest > .3 && impactCooldown == 0) {
            impactCooldown = 8;
            server.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, .65F);
            server.sendParticles(ParticleTypes.POOF, getX(), getY() - bounds(center).getYsize() * .4, getZ(), 10, .4, .12, .4, .025);
        }
    }

    private void hitPlayers(ServerLevel server, java.util.List<net.minecraft.server.level.ServerPlayer> victims,
                            Vec3 center, Vec3 velocity) {
        if (velocity.lengthSqr() < .10) return;
        var cubes=modelBoxes(center);
        for (var victim : victims) {
            if (hitPlayers.contains(victim.getUUID())) continue;
            boolean edge=false, touched=false;
            for(var cube:cubes) if(net.krodark.asterion.physics.ModelCollider.contact(cube,victim.getBoundingBox())!=null) {
                touched=true; edge|=cube.blade();
            }
            if (!touched) continue;
            Vec3 closest = victim.getBoundingBox().getCenter();
            if (server.clip(new net.minecraft.world.level.ClipContext(center, closest,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this))
                    .getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
            Entity owner = server.getEntity(throwerId());
            var damage = owner instanceof net.minecraft.world.entity.LivingEntity living
                    ? damageSources().mobProjectile(this, living) : damageSources().generic();
            Vec3 torsoDelta = closest.subtract(center);
            Vec3 bladeAxis = axes()[1];
            boolean central = edge && torsoDelta.subtract(bladeAxis.scale(torsoDelta.dot(bladeAxis))).lengthSqr() < .30 * .30;
            boolean canImpale = isSword() && central && !returning && sweepTarget == null
                    && owner instanceof MinotaurEntity boss && boss.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS;
            if (victim.hurtServer(server, damage, canImpale ? 12F : edge ? 20F : 10F)) {
                hitPlayers.add(victim.getUUID());
                if (canImpale && victim.isAlive()) {
                    entityData.set(IMPALED, victim.getId()); impaleTicks = 0;
                    spin = Vec3.ZERO; harmless = true;
                }
                Vec3 impulse = velocity.normalize().scale(canImpale ? .45D : edge ? 2.8D : 1.35D)
                        .add(0, edge ? .72D : .42D, 0);
                victim.setDeltaMovement(impulse);
                victim.hurtMarked = true;
                victim.resetFallDistance();
                if (!canImpale) RagdollServerNetworking.markRagdolled(victim, 86);
                RagdollServerNetworking.suppressThrowFallDamage(victim, 100);
                if (!canImpale && ServerPlayNetworking.canSend(victim, RagdollImpulsePayload.TYPE))
                    ServerPlayNetworking.send(victim, new RagdollImpulsePayload(
                            owner == null ? center : owner.position(), impulse, edge ? 1.75F : 1.35F));
                if (ServerPlayNetworking.canSend(victim, DazePayload.TYPE))
                    ServerPlayNetworking.send(victim, new DazePayload(edge ? 82 : 68, edge ? 5 : 4));
                server.playSound(null, victim.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.4F, .7F);
                server.sendParticles(ParticleTypes.CRIT, closest.x, closest.y, closest.z, 12, .3, .4, .3, .08);
            }
        }
    }

    private Vec3 half() {
        return (isSword() ? new Vec3(2.5 / 16, (78 - SWORD_MIN_Y) / 32.0, 14.2 / 16)
                : new Vec3(2, (99 - MODEL_MIN_Y) / 32.0, 2.75 / 16)).scale(modelScale());
    }
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
        modelBoxes(center);return worldColliderBounds;
    }
    private java.util.List<net.krodark.asterion.physics.ModelCollider.Box> modelBoxes(Vec3 center) {
        boolean changed=localColliders==null || colliderSword!=isSword() || colliderScale!=modelScale() || !colliderRotation.equals(rotation);
        if(changed) {
            localColliders=net.krodark.asterion.physics.ModelCollider.load(isSword()?"sword":"axe").world(Vec3.ZERO,rotation,modelScale(),
                    new Vec3(0,modelCenterY()-(isSword()?6/16.0:0),0),isSword()?0:-Math.PI/2);
            localColliderBounds=net.krodark.asterion.physics.ModelCollider.bounds(localColliders);
            colliderRotation.set(rotation);colliderSword=isSword();colliderScale=modelScale();
        }
        if(!changed && center.equals(colliderPosition))return worldColliders;
        worldColliders=new java.util.ArrayList<>(localColliders.size());
        for(var box:localColliders)worldColliders.add(new net.krodark.asterion.physics.ModelCollider.Box(box.center().add(center),box.half(),box.axes(),box.blade()));
        colliderPosition=center;worldColliderBounds=localColliderBounds.move(center);return worldColliders;
    }
    @Override protected AABB makeBoundingBox(Vec3 position) {
        return rotation == null ? super.makeBoundingBox(position) : bounds(position);
    }
    private record Contact(Vec3 normal, double depth, Vec3 point, boolean blade) { }
    private Contact contact(Vec3 center) {
        Contact deepest=null;
        var cubes=modelBoxes(center);
        for(var shape:level().getBlockCollisions(this,worldColliderBounds.deflate(.00035)))
            for(var box:shape.toAabbs()) for(var cube:cubes) {
                var hit=net.krodark.asterion.physics.ModelCollider.contact(cube,box);
                if(hit!=null && (deepest==null || hit.depth()>deepest.depth))
                    deepest=new Contact(hit.normal(),hit.depth(),hit.point(),hit.blade());
            }
        return deepest;
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
        out.putBoolean("sword", isSword()); out.putBoolean("harmless", harmless);
        out.putBoolean("embedded",embedded); out.putBoolean("free_flight",freeFlight);
        out.putDouble("embed_x",embeddedSupport.x); out.putDouble("embed_y",embeddedSupport.y); out.putDouble("embed_z",embeddedSupport.z);
        if (ownerUUID != null) out.putString("chain_owner", ownerUUID.toString());
        out.putInt("chain_side", chainSide()); out.putBoolean("returning", returning);
        out.putInt("return_ticks", returnTicks);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        entityData.set(SWORD, in.getBooleanOr("sword", false));
        harmless = in.getBooleanOr("harmless", false);
        try { ownerUUID = java.util.UUID.fromString(in.getStringOr("chain_owner", "")); }
        catch (IllegalArgumentException ignored) { ownerUUID = null; }
        if(!isSword())ownerUUID=null; // Migrate axes saved with the old sword tether behavior.
        entityData.set(CHAIN_SIDE, ownerUUID == null ? 0 : in.getIntOr("chain_side", 0));
        returning = isSword() && in.getBooleanOr("returning", false);
        embedded=in.getBooleanOr("embedded",false); freeFlight=in.getBooleanOr("free_flight",false);
        embeddedSupport=new Vec3(in.getDoubleOr("embed_x",0),in.getDoubleOr("embed_y",0),in.getDoubleOr("embed_z",0));
        returnTicks = Math.clamp(in.getIntOr("return_ticks", 0), 0, 160);
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
