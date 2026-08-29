package net.krodark.asterion.client.ragdoll;

import net.krodark.asterion.Asterion;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.krodark.asterion.network.ragdoll.RagdollKillPayload;
import net.krodark.asterion.network.ragdoll.RagdollBlockImpactPayload;
import net.krodark.asterion.network.ragdoll.RagdollEntityImpactPayload;
import net.krodark.asterion.network.ragdoll.RagdollFallDamagePayload;
import net.krodark.asterion.network.ragdoll.TumbleExitPayload;
import net.krodark.asterion.network.ragdoll.RagdollArmorImpactPayload;
import net.krodark.asterion.network.ragdoll.RagdollPosePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;

public final class DismembermentEngine {
    public static final DismembermentEngine INSTANCE = new DismembermentEngine();
    private static final boolean RAGDOLL_CAPE_ENABLED = false;
    private final List<RigidBodyPiece> pieces = new ArrayList<>();
    private final Map<Integer, Set<Integer>> detached = new HashMap<>();
    private final Set<Integer> ragdolled = new HashSet<>();
    private final Set<Integer> remoteDriven = new HashSet<>();
    private final Map<Integer, Integer> remotePoseSequences = new HashMap<>();
    private final Map<Integer, Integer> remotePoseTicks = new HashMap<>();
    private int poseSequence;
    private final Map<Long, Float> regionalTrauma = new HashMap<>();
    private final Map<Integer, Map<Integer, String>> detachedModelPaths = new HashMap<>();
    private final Map<Integer, Map<Integer, BodyGeometry>> renderedPoseCache = new HashMap<>();
    private final Set<Integer> playerTumbles = new HashSet<>();
    private final Map<Integer, Integer> islandSleepTicks = new HashMap<>();
    private final Map<Long, Integer> blockImpactAges = new HashMap<>();
    private final Map<Integer, Integer> entityImpactAges = new HashMap<>();
    private final Map<Integer, Integer> rigidSoundAges = new HashMap<>();
    private final Map<Integer, Integer> tumbleStartedAt = new HashMap<>();
    private final Map<Integer, Integer> ragdollStartedAt = new HashMap<>();
    private final Map<Integer, Integer> electrifiedUntil = new HashMap<>();
    private final Map<Integer, WailingState> wailing = new HashMap<>();
    private final Map<Integer, Integer> playerFracturedLegs = new HashMap<>();
    private final Set<Integer> appliedFracturePoses = new HashSet<>();
    private final List<RecentExplosion> recentExplosions = new ArrayList<>();
    private RigidBodyPiece grabbed;
    private double grabDistance;
    private Vec3 smoothedGrabTarget;
    private int traumaDecayTicker;
    private int lastFallDamageTick = -1000;
    private long lastAuthorityTick = Long.MIN_VALUE;
    private Map<Long, RigidBodyPiece> solverIndex;
    private final Map<RigidBodyPiece, Vec3> tickIncomingVelocities = new IdentityHashMap<>();
    private final Set<RigidBodyPiece> tickCollidedParts = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Integer> tickSupportedIslands = new HashSet<>();
    private final Set<Integer> tickAnatomicalIslands = new HashSet<>();
    private final Set<Integer> tickGroundedPlayerIslands = new HashSet<>();
    private final Map<Integer, Double> tickArmorLoads = new HashMap<>();
    private final List<RigidBodyPiece> tickActivePieces = new ArrayList<>();
    private final Map<Long, RigidBodyPiece> tickSolverIndex = new HashMap<>();
    private final List<RigidBodyPiece> selfCollisionCandidates = new ArrayList<>();
    private final Map<RigidBodyPiece, AABB> selfCollisionBounds = new IdentityHashMap<>();

    private DismembermentEngine() { }

    public boolean impact(Entity entity, int region, Vec3 point, Vec3 direction, double force) {
        if (!inAsterion(entity)) return false;
        return impact(entity, region, point, direction, force, false);
    }

    public void externalDamage(Entity entity, Vec3 sourcePosition, Vec3 impulse,
                               float severity, boolean radial) {
        if (!inAsterion(entity)) return;
        if (entity == null || !ragdolled.contains(entity.getId())) return;
        Vec3 boundedImpulse = impulse.length() > 2.4 ? impulse.normalize().scale(2.4) : impulse;
        if (radial) {
            RigidBodyPiece torso = find(entity.getId(), 1);
            Vec3 angularCenter = torso == null ? entity.position() : torso.position;
            for (RigidBodyPiece part : pieces) if (part.entityId == entity.getId()) {
                Vec3 direction = sourcePosition == null ? boundedImpulse
                        : RagdollMath.safeNormalize(part.position.subtract(sourcePosition), boundedImpulse);
                double attenuation = 0.72 + 0.28 * Math.min(1.0, part.radius() / 0.35);
                part.velocity = part.velocity.add(direction.scale(boundedImpulse.length() * attenuation));
                part.angularVelocity = part.angularVelocity.add(direction.cross(
                        part.position.subtract(angularCenter)).scale(0.16));
                addRigidBruise(part, direction.scale(-1), null, Math.max(0.18f, severity * 0.72f));
            }
        } else {
            RigidBodyPiece struck = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            Vec3 sample = sourcePosition == null ? entity.getBoundingBox().getCenter() : sourcePosition;
            for (RigidBodyPiece part : pieces) if (part.entityId == entity.getId()) {
                double distance = part.position.distanceToSqr(sample);
                if (distance < bestDistance) { bestDistance = distance; struck = part; }
            }
            if (struck != null) {
                double inverseMassResponse = 1.0 / Math.sqrt(Math.max(0.08, struck.mass()));
                struck.velocity = struck.velocity.add(boundedImpulse.scale(inverseMassResponse));
                Vec3 lever = sample.subtract(struck.position);
                struck.angularVelocity = struck.angularVelocity.add(lever.cross(boundedImpulse).scale(0.34));
                addRigidBruise(struck, boundedImpulse.scale(-1), sample, severity);
            }
        }
        wakeGroup(entity.getId());
    }

    public void applyExplosion(Minecraft client, Vec3 center, float radius) {
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            return;
        }
        if (center == null || !Float.isFinite(radius) || radius <= 0.0f) {
            return;
        }
        recentExplosions.removeIf(blast -> traumaDecayTicker - blast.createdTick > 6);
        recentExplosions.add(new RecentExplosion(
                center, radius, traumaDecayTicker));
        int localPlayerId = client.player == null ? Integer.MIN_VALUE : client.player.getId();
        Map<Integer, List<RigidBodyPiece>> assemblies = new HashMap<>();
        for (RigidBodyPiece part : pieces) {
            if (part.entityId != localPlayerId) {
                assemblies.computeIfAbsent(part.entityId, ignored -> new ArrayList<>()).add(part);
            }
        }
        for (Map.Entry<Integer, List<RigidBodyPiece>> entry : assemblies.entrySet()) {
            applyExplosionToAssembly(client, entry.getKey(), entry.getValue(), center, radius);
        }
    }

    private void applyExplosionToAssembly(Minecraft client, int entityId,
                                          List<RigidBodyPiece> assembly, Vec3 center, float radius) {
        if (assembly.isEmpty() || client.level == null) return;
        double reach = Math.max(1.5, radius * 2.0 + 1.0);
        RigidBodyPiece torso = assembly.stream().filter(part -> part.region == 1)
                .findFirst().orElse(assembly.getFirst());
        double distance = torso.position.distanceTo(center);
        if (distance > reach) return;
        double exposure = 1.0;
        Entity context = client.level.getEntity(entityId);
        if (context == null) context = client.player;
        if (context != null) {
            BlockHitResult obstruction = client.level.clip(new ClipContext(center, torso.position,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            if (obstruction.getType() != HitResult.Type.MISS
                    && obstruction.getLocation().distanceToSqr(center)
                    + 0.04 < torso.position.distanceToSqr(center)) exposure = 0.22;
        }
        double falloff = Mth.clamp(1.0 - distance / reach, 0.0, 1.0);
        double speedChange = Math.min(3.2,
                (0.34 + radius * 0.30) * falloff * falloff * exposure);
        if (speedChange < 0.025) return;
        for (RigidBodyPiece part : assembly) {
            Vec3 direction = RagdollMath.safeNormalize(part.position.subtract(center), new Vec3(0, 1, 0));
            Vec3 impulse = direction.scale(speedChange * part.mass()
                    * (isAttachmentRegion(part.region) ? 0.56 : 1.0));
            Vec3 applicationPoint = part.position.subtract(direction.scale(part.radius() * 0.68));
            part.applyImpulse(applicationPoint, impulse);
            part.sleeping = false;
            part.supportTicks = 0;
        }
        wakeGroup(entityId);
    }

    public boolean impact(Entity entity, int region, Vec3 point, Vec3 direction, double force, boolean cutting) {
        if (!inAsterion(entity)) return false;
        if (isRagdollExcluded(entity)) return false;
        RagdollConfig config = RagdollRuntime.INSTANCE.config;
        if (!config.dismemberment || ragdolled.contains(entity.getId())) return false;
        long traumaKey = ((long) entity.getId() << 32) ^ (region & 0xffffffffL);
        float accumulated = regionalTrauma.getOrDefault(traumaKey, 0.0f) * 0.84f
                + (float) force * (cutting ? 1.42f : 0.62f);
        regionalTrauma.put(traumaKey, accumulated);
        if (accumulated < config.dismemberForce) return false;
        Set<Integer> regions = detached.computeIfAbsent(entity.getId(), ignored -> new HashSet<>());
        boolean catastrophic = (region == 0 || region == 1)
                || force >= config.dismemberForce * 3.0f;
        Identifier texture = resolveTexture(entity);
        if (catastrophic) return ragdoll(entity, region, point, direction, force, true);
        else if (regions.add(region))
            spawn(entity, region, -1, point, direction, force, true, texture);
        trim(config.maxRigidBodies);
        return false;
    }

    public boolean ragdoll(Entity entity, int impactRegion, Vec3 point, Vec3 direction,
                           double force, boolean requestServerKill) {
        if (!inAsterion(entity)) return false;
        if (isRagdollExcluded(entity) || ragdolled.contains(entity.getId())) return false;
        pieces.removeIf(piece -> piece.entityId == entity.getId());
        detached.remove(entity.getId());
        detachedModelPaths.remove(entity.getId());
        islandSleepTicks.remove(entity.getId());
        ragdolled.add(entity.getId());
        ragdollStartedAt.put(entity.getId(), traumaDecayTicker);
        Set<Integer> regions = detached.computeIfAbsent(entity.getId(), ignored -> new HashSet<>());
        Identifier texture = resolveTexture(entity);
        for (int bodyRegion : new int[] {1, 0, 2, 3, 4, 5}) if (regions.add(bodyRegion))
            spawn(entity, bodyRegion, bodyRegion == 1 ? -1
                            : bodyRegion == 0 && impactRegion == 0 && force > RagdollRuntime.INSTANCE.config.dismemberForce * 2.0
                            ? -1 : 1,
                    point, direction, Math.max(0.7, force * 0.42), false, texture);
        spawnCape(entity);
        spawnElytraWings(entity);
        applyPlayerGlobalFlightPose(entity);
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity instanceof Player player && minecraft.player == player)) {
            List<RigidBodyPiece> assembly = pieces.stream()
                    .filter(part -> part.entityId == entity.getId()).toList();
            for (RecentExplosion blast : recentExplosions)
                if (traumaDecayTicker - blast.createdTick <= 6)
                    applyExplosionToAssembly(minecraft, entity.getId(), assembly,
                            blast.center, blast.radius);
        }
        RigidBodyPiece struck = find(entity.getId(), impactRegion);
        if (entity instanceof Player) {
            Vec3 coherentVelocity = entity.getDeltaMovement().add(direction.scale(Math.min(2.4, 0.22 * force)));
            for (RigidBodyPiece part : pieces) if (part.entityId == entity.getId()) {
                part.velocity = coherentVelocity;
                part.angularVelocity = Vec3.ZERO;
            }
        }
        if (struck != null) {
            if (!(entity instanceof Player)) {
                struck.velocity = struck.velocity.add(direction.scale(Math.min(2.4, 0.22 * force)));
                Vec3 lever = point.subtract(struck.position);
                struck.angularVelocity = struck.angularVelocity.add(lever.cross(direction).scale(0.38 * force));
            }
            addRigidBruise(struck, direction.scale(-1), point, 1.0f);
        }
        trim(RagdollRuntime.INSTANCE.config.maxRigidBodies);
        if (requestServerKill && ClientPlayNetworking.canSend(RagdollKillPayload.TYPE))
            ClientPlayNetworking.send(new RagdollKillPayload(entity.getId()));
        return true;
    }

    private void spawn(Entity entity, int region, int parentRegion, Vec3 impact, Vec3 direction,
                       double force, boolean separated, Identifier texture) {
        if (find(entity.getId(), region) != null) return;
        AABB box = entity.getBoundingBox();
        double width = box.getXsize(), height = box.getYsize(), depth = box.getZsize();
        Vec3 fallbackOffset = switch (region) {
            case 0 -> new Vec3(0, height * 0.39, 0);
            case 2 -> new Vec3(-width * 0.62, height * 0.12, 0);
            case 3 -> new Vec3(width * 0.62, height * 0.12, 0);
            case 4 -> new Vec3(-width * 0.22, -height * 0.31, 0);
            case 5 -> new Vec3(width * 0.22, -height * 0.31, 0);
            default -> Vec3.ZERO;
        };
        Vec3 fallbackHalf = switch (region) {
            case 0 -> new Vec3(width * 0.34, height * 0.13, depth * 0.34);
            case 2, 3 -> new Vec3(width * 0.16, height * 0.23, depth * 0.18);
            case 4, 5 -> new Vec3(width * 0.18, height * 0.24, depth * 0.2);
            default -> new Vec3(width * 0.38, height * 0.22, depth * 0.28);
        };
        BodyGeometry geometry = resolveGeometry(entity, region, fallbackOffset, fallbackHalf);
        Map<Integer, String> assignedPaths = detachedModelPaths.get(entity.getId());
        boolean duplicateModelPart = false;
        if (geometry.modelPath != null && assignedPaths != null)
            for (Map.Entry<Integer, String> entry : assignedPaths.entrySet())
                if (entry.getKey() != region && geometry.modelPath.equals(entry.getValue())) {
                    duplicateModelPart = true;
                    break;
                }
        if (duplicateModelPart) {
            geometry = new BodyGeometry(fallbackOffset, fallbackHalf,
                    resolveFaceUvs(entity, region), null, new Quaternionf());
        }
        if (geometry.modelPath != null)
            detachedModelPaths.computeIfAbsent(entity.getId(), ignored -> new HashMap<>())
                    .put(region, geometry.modelPath);
        Vec3 offset = geometry.offset;
        Vec3 center = box.getCenter().add(offset);
        Vec3 half = geometry.halfExtents;
        long seed = RagdollMath.mix(entity.getId() * 31L + region * 7919L);
        Vec3 lateral = new Vec3(RagdollMath.unit(seed) - 0.5, RagdollMath.unit(RagdollMath.mix(seed)) * 0.35,
                RagdollMath.unit(RagdollMath.mix(seed + 7)) - 0.5);
        Vec3 velocity = entity.getDeltaMovement().add(direction.scale(0.12 * force))
                .add(lateral.scale(separated && !(entity instanceof Player) ? 0.32 : 0.0));
        Vec3 spin = separated && !(entity instanceof Player)
                ? lateral.add(0.2, 0.1, -0.15).scale(0.22 + force * 0.045) : Vec3.ZERO;
        float jointLength = (float) Math.max(0.12, offset.length());
        if (parentRegion >= 0) {
            BodyGeometry parentGeometry = resolveGeometry(entity, parentRegion, Vec3.ZERO, fallbackHalf);
            jointLength = (float) Math.max(0.12, offset.subtract(parentGeometry.offset).length());
        }
        RigidBodyPiece piece = new RigidBodyPiece(entity.getId(), region, parentRegion,
                entity instanceof Player, center, velocity, spin, half,
                partMass(entity, region), jointLength, texture,
                RagdollPalette.forEntity(entity, RagdollRuntime.INSTANCE.config), geometry.faceUvs,
                geometry.overlayFaceUvs, geometry.orientation);
        snapshotEquipment(piece, entity);
        RigidBodyPiece parent = parentRegion < 0 ? null : find(entity.getId(), parentRegion);
        if (parent != null) {
            Vec3 socket = geometry.jointOffset != null
                    ? box.getCenter().add(geometry.jointOffset)
                    : surfacePointTowards(piece, parent.position).lerp(
                            surfacePointTowards(parent, piece.position), 0.5);
            piece.childJointAnchor = localPoint(piece, socket);
            piece.parentJointAnchor = localPoint(parent, socket);
            Vector3f restOffset = new Vector3f((float) (piece.position.x - parent.position.x),
                    (float) (piece.position.y - parent.position.y),
                    (float) (piece.position.z - parent.position.z));
            new Quaternionf(parent.orientation).conjugate().transform(restOffset);
            piece.jointRestOffset = new Vec3(restOffset.x, restOffset.y, restOffset.z);
            piece.anchoredJoint = true;
            piece.jointRestOrientation.set(new Quaternionf(parent.orientation).conjugate()
                    .mul(piece.orientation).normalize());
            configureJointMotor(piece);
        }
        pieces.add(piece);
    }

    private static void snapshotEquipment(RigidBodyPiece piece, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        piece.headEquipment = living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.HEAD).copy();
        piece.chestEquipment = living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.CHEST).copy();
        piece.legEquipment = living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.LEGS).copy();
        piece.footEquipment = living.getItemBySlot(
                net.minecraft.world.entity.EquipmentSlot.FEET).copy();
    }

    private static Vec3 transformedLocalAxis(RigidBodyPiece body, Vec3 axis) {
        Vector3f transformed = body.orientation.transform(new Vector3f(
                (float) axis.x, (float) axis.y, (float) axis.z));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    private static float[][] splitVerticalUvs(float[][] source, boolean lower) {
        float[][] result = new float[source.length][];
        for (int face = 0; face < source.length; face++) {
            result[face] = source[face].clone();
            if (face == 2 || face == 3) continue;
            float minimum = Float.POSITIVE_INFINITY, maximum = Float.NEGATIVE_INFINITY;
            for (int i = 1; i < result[face].length; i += 2) {
                minimum = Math.min(minimum, result[face][i]);
                maximum = Math.max(maximum, result[face][i]);
            }
            float range = maximum - minimum;
            if (range <= 1.0e-6f) continue;
            for (int i = 1; i < result[face].length; i += 2) {
                float normalized = (result[face][i] - minimum) / range;
                result[face][i] = minimum + range * (lower ? 0.5f + normalized * 0.5f
                        : normalized * 0.5f);
            }
        }
        return result;
    }

    private void spawnCape(Entity entity) {
        if (!RAGDOLL_CAPE_ENABLED) return;
        if (!(entity instanceof net.minecraft.client.player.AbstractClientPlayer player)
                || player.getSkin().cape() == null
                || !player.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.CAPE)) return;
        RigidBodyPiece torso = find(entity.getId(), 1);
        if (torso == null || find(entity.getId(), 6) != null) return;
        double playerScale = Math.max(.55, Math.min(2.4, torso.halfExtents.x / .225));
        Vec3 half = new Vec3(.28125 * playerScale, .45 * playerScale, .028 * playerScale);
        Vec3 parentAnchor = new Vec3(0, -torso.halfExtents.y * .82,
                torso.halfExtents.z + .045);
        Vec3 localCenter = parentAnchor.subtract(new Vec3(0, -half.y, 0));
        Vec3 center = worldAnchor(torso, localCenter);
        RigidBodyPiece cape = new RigidBodyPiece(entity.getId(), 6, 1, true,
                center, torso.velocity, torso.angularVelocity.scale(.35), half, .032,
                (float) center.distanceTo(torso.position), player.getSkin().cape().texturePath(),
                torso.bloodRgb, capeFaceUvs(), null, new Quaternionf(torso.orientation));
        cape.parentJointAnchor = parentAnchor;
        cape.childJointAnchor = new Vec3(0, -half.y, 0);
        cape.jointRestOffset = localCenter;
        cape.anchoredJoint = true;
        cape.jointRestOrientation.identity();
        cape.velocity = torso.velocityAt(worldAnchor(torso, parentAnchor));
        configureJointMotor(cape);
        pieces.add(cape);
    }

    private void spawnElytraWings(Entity entity) {
        if (!(entity instanceof Player player)) return;
        var chest = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        if (!isElytra(chest)) return;
        RigidBodyPiece torso = find(entity.getId(), 1);
        if (torso == null) return;
        Identifier texture = Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");
        double scale = Math.max(0.55, Math.min(2.4, torso.halfExtents.x / 0.225));
        Vec3 half = new Vec3(0.3125 * scale, 0.625 * scale, 0.026 * scale);
        for (int region : new int[] {7, 8}) {
            if (find(entity.getId(), region) != null) continue;
            double sign = region == 7 ? -1.0 : 1.0;
            Vec3 parentAnchor = new Vec3(sign * torso.halfExtents.x * 0.72,
                    -torso.halfExtents.y * 0.30, torso.halfExtents.z + 0.060);
            Vec3 localCenter = parentAnchor.add(sign * half.x * 0.92, half.y * 0.82, 0.018);
            Vec3 center = worldAnchor(torso, localCenter);
            Quaternionf relativeWingPose = vanillaElytraPose(player, region == 7, sign);
            Quaternionf orientation = new Quaternionf(torso.orientation).mul(relativeWingPose).normalize();
            RigidBodyPiece wing = new RigidBodyPiece(entity.getId(), region, 1, true,
                    center, torso.velocity, Vec3.ZERO, half, 0.024 * scale,
                    (float) localCenter.length(), texture, torso.bloodRgb,
                    elytraWingFaceUvs(region == 7), null, orientation);
            wing.parentJointAnchor = parentAnchor;
            wing.childJointAnchor = new Vec3(-sign * half.x * 0.92, -half.y * 0.82, 0);
            wing.jointRestOffset = localCenter;
            wing.anchoredJoint = true;
            wing.jointRestOrientation.set(new Quaternionf(torso.orientation).conjugate()
                    .mul(orientation).normalize());
            wing.velocity = torso.velocityAt(worldAnchor(torso, parentAnchor));
            configureJointMotor(wing);
            pieces.add(wing);
        }
    }

    private void splitClothPanel(RigidBodyPiece rootPanel, int childRegion) {
        if (find(rootPanel.entityId, childRegion) != null) return;
        RigidBodyPiece owner = find(rootPanel.entityId, rootPanel.parentRegion);
        if (owner == null) return;
        Vec3 socket = worldAnchor(owner, rootPanel.parentJointAnchor);
        Vec3 direction = RagdollMath.safeNormalize(rootPanel.position.subtract(socket),
                transformedLocalAxis(rootPanel, new Vec3(0, 1, 0)));
        double length = Math.max(0.10, rootPanel.halfExtents.y * 2.0);
        Vec3 middle = socket.add(direction.scale(length * 0.50));
        float[][] sourceUvs = rootPanel.faceUvs;
        rootPanel.position = socket.add(direction.scale(length * 0.25));
        rootPanel.previous = rootPanel.position;
        rootPanel.halfExtents = new Vec3(rootPanel.halfExtents.x,
                rootPanel.halfExtents.y * 0.50, rootPanel.halfExtents.z);
        rootPanel.partMass *= 0.54;
        rootPanel.faceUvs = splitVerticalUvs(sourceUvs, false);
        rootPanel.childJointAnchor = localPoint(rootPanel, socket);
        rootPanel.parentJointAnchor = localPoint(owner, socket);

        Vec3 childCenter = socket.add(direction.scale(length * 0.75));
        RigidBodyPiece child = new RigidBodyPiece(rootPanel.entityId, childRegion,
                rootPanel.region, true, childCenter, rootPanel.velocity,
                rootPanel.angularVelocity, rootPanel.halfExtents,
                rootPanel.mass() * 0.46 / 0.54, (float) (length * 0.50),
                rootPanel.texture, rootPanel.bloodRgb, splitVerticalUvs(sourceUvs, true),
                null, new Quaternionf(rootPanel.orientation));
        child.parentJointAnchor = localPoint(rootPanel, middle);
        child.childJointAnchor = localPoint(child, middle);
        Vector3f childRest = new Vector3f((float) (child.position.x - rootPanel.position.x),
                (float) (child.position.y - rootPanel.position.y),
                (float) (child.position.z - rootPanel.position.z));
        new Quaternionf(rootPanel.orientation).conjugate().transform(childRest);
        child.jointRestOffset = new Vec3(childRest.x, childRest.y, childRest.z);
        child.anchoredJoint = true;
        child.jointRestOrientation.identity();
        configureJointMotor(child);
        pieces.add(child);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Quaternionf vanillaElytraPose(Player player, boolean left, double sign) {
        try {
            ModelPart root = ElytraModel.createLayer().bakeRoot();
            ElytraModel model = new ElytraModel(root);
            EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
            EntityRenderState state = renderer.createRenderState(player, 1.0f);
            if (state instanceof HumanoidRenderState humanoidState) model.setupAnim(humanoidState);
            ModelPart wing = root.getChild(left ? "left_wing" : "right_wing");
            return new Quaternionf().rotationZYX(wing.zRot, wing.yRot, wing.xRot).normalize();
        } catch (RuntimeException ignored) {
            return new Quaternionf()
                    .rotateY((float) (sign * (player.isFallFlying() ? 0.18 : 0.08)))
                    .rotateZ((float) (sign * (player.isFallFlying() ? 0.34 : 0.16)));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyPlayerGlobalFlightPose(Entity entity) {
        if (!(entity instanceof Player player) || !player.isFallFlying()) return;
        try {
            EntityRenderer renderer = Minecraft.getInstance()
                    .getEntityRenderDispatcher().getRenderer(player);
            EntityRenderState renderState = renderer.createRenderState(player, 1.0f);
            if (!(renderState instanceof AvatarRenderState state) || !state.isFallFlying) return;

            float baseYaw = (float) Math.toRadians(180.0f - state.bodyRot);
            float flightPitch = (float) Math.toRadians(
                    state.fallFlyingScale() * (-90.0f - state.xRot));
            Quaternionf base = new Quaternionf().rotationY(baseYaw);
            Quaternionf flight = new Quaternionf().rotationX(flightPitch);
            if (state.shouldApplyFlyingYRot) flight.rotateY(state.flyingYRot);
            Quaternionf worldDelta = new Quaternionf(base).mul(flight)
                    .mul(new Quaternionf(base).conjugate()).normalize();
            Vec3 pivot = player.position();
            for (RigidBodyPiece piece : pieces) {
                if (piece.entityId != player.getId()) continue;
                Vector3f relative = worldDelta.transform(new Vector3f(
                        (float) (piece.position.x - pivot.x),
                        (float) (piece.position.y - pivot.y),
                        (float) (piece.position.z - pivot.z)));
                piece.position = pivot.add(relative.x, relative.y, relative.z);
                piece.previous = piece.position;
                piece.orientation.set(new Quaternionf(worldDelta)
                        .mul(piece.orientation)).normalize();
                piece.previousOrientation.set(piece.orientation);
            }
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isElytra(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.is(Items.ELYTRA)) return true;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId.getPath().contains("elytra")) return true;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.assetId().isPresent()
                && equippable.assetId().get().identifier().getPath().contains("elytra");
    }

    private void synchronizePlayerAttachments(ClientLevel level) {
        for (int entityId : new HashSet<>(ragdolled)) {
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof Player player)) continue;
            boolean capeVisible = RAGDOLL_CAPE_ENABLED
                    && player instanceof net.minecraft.client.player.AbstractClientPlayer clientPlayer
                    && clientPlayer.getSkin().cape() != null
                    && clientPlayer.isModelPartShown(
                    net.minecraft.world.entity.player.PlayerModelPart.CAPE);
            if (!capeVisible) {
                pieces.removeIf(piece -> {
                    boolean remove = piece.entityId == entityId
                            && (piece.region == 6 || piece.region == 13);
                    if (remove && grabbed == piece) grabbed = null;
                    return remove;
                });
            } else if (find(entityId, 6) == null) {
                spawnCape(player);
            }
            boolean equipped = isElytra(player.getItemBySlot(
                    net.minecraft.world.entity.EquipmentSlot.CHEST));
            if (!equipped) {
                pieces.removeIf(piece -> {
                    boolean remove = piece.entityId == entityId
                            && (piece.region == 7 || piece.region == 8
                            || piece.region == 14 || piece.region == 15);
                    if (remove && grabbed == piece) grabbed = null;
                    return remove;
                });
            } else if (find(entityId, 7) == null || find(entityId, 8) == null) {
                spawnElytraWings(player);
            }
            boolean rightHanded = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
            synchronizeHeldItemGrip(player, 2, 30,
                    rightHanded ? player.getMainHandItem() : player.getOffhandItem());
            synchronizeHeldItemGrip(player, 3, 31,
                    rightHanded ? player.getOffhandItem() : player.getMainHandItem());
            for (RigidBodyPiece piece : pieces)
                if (piece.entityId == entityId && isAnatomicalRegion(piece.region)) {
                    piece.partMass = partMass(player, piece.region);
                    snapshotEquipment(piece, player);
                }
        }
    }

    private void synchronizeHeldItemGrip(Player player, int armRegion, int gripRegion,
                                         net.minecraft.world.item.ItemStack stack) {
        RigidBodyPiece existing = find(player.getId(), gripRegion);
        if (stack.isEmpty()) {
            if (existing != null) pieces.remove(existing);
            return;
        }
        if (existing != null) {
            existing.partMass = heldItemWeight(stack);
            existing.halfExtents = heldItemExtents(stack);
            existing.heldItem = stack.copy();
            return;
        }
        RigidBodyPiece arm = find(player.getId(), armRegion);
        if (arm == null) return;
        Vec3 gripPoint = worldAnchor(arm, arm.childJointAnchor.scale(-1.0));
        Vec3 half = heldItemExtents(stack);
        RigidBodyPiece grip = new RigidBodyPiece(player.getId(), gripRegion, armRegion, true,
                gripPoint, arm.velocityAt(gripPoint), arm.angularVelocity, half,
                heldItemWeight(stack), (float) half.y, arm.texture, arm.bloodRgb,
                fullFaceUvs(), null, new Quaternionf(arm.orientation));
        grip.parentJointAnchor = localPoint(arm, gripPoint);
        grip.childJointAnchor = Vec3.ZERO;
        Vector3f gripRest = new Vector3f((float) (grip.position.x - arm.position.x),
                (float) (grip.position.y - arm.position.y),
                (float) (grip.position.z - arm.position.z));
        new Quaternionf(arm.orientation).conjugate().transform(gripRest);
        grip.jointRestOffset = new Vec3(gripRest.x, gripRest.y, gripRest.z);
        grip.anchoredJoint = true;
        grip.jointRestOrientation.identity();
        grip.heldItem = stack.copy();
        configureJointMotor(grip);
        pieces.add(grip);
    }

    private static double heldItemWeight(net.minecraft.world.item.ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        double material = path.contains("netherite") ? 1.72
                : path.contains("gold") ? 1.52
                : path.contains("diamond") ? 1.32
                : path.contains("iron") ? 1.18
                : path.contains("copper") ? 1.12
                : path.contains("stone") ? 1.06
                : path.contains("wood") || path.contains("wooden") ? 0.72 : 1.0;
        double shape = path.contains("hammer") || path.contains("mace") ? 0.24
                : path.contains("pick") || path.contains("axe") ? 0.18
                : path.contains("gun") || path.contains("rifle") || path.contains("shotgun") ? 0.17
                : path.contains("sword") || path.contains("katana") ? 0.135
                : stack.getItem() instanceof BlockItem ? 0.12 : 0.065;
        double stackLoad = 1.0 + Math.sqrt(Math.max(0, stack.getCount() - 1)) * 0.16;
        double modelLoad = 1.0 + Math.min(0.28, stack.getMaxDamage() / 5000.0);
        return Mth.clamp(shape * material * stackLoad * modelLoad, 0.035, 0.62);
    }

    private static Vec3 heldItemExtents(net.minecraft.world.item.ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        if (path.contains("gun") || path.contains("rifle") || path.contains("shotgun"))
            return new Vec3(0.080, 0.255, 0.075);
        if (path.contains("sword") || path.contains("axe") || path.contains("pick")
                || path.contains("mace") || path.contains("hammer"))
            return new Vec3(0.065, 0.205, 0.060);
        if (stack.getItem() instanceof BlockItem) return new Vec3(0.105, 0.105, 0.105);
        return new Vec3(0.050, 0.105, 0.040);
    }

    private static double partMass(Entity entity, int region) {
        double scale = entity instanceof Player ? entity.getBbWidth() / .60
                : entity.getBbHeight() / 1.8;
        scale = Math.max(.55, Math.min(2.4, scale));
        double fraction = switch (region) {
            case 0 -> .075;      // head; neck should not outweigh the torso
            case 1 -> .43;       // torso/pelvis
            case 2, 3 -> .036;   // upper arms
            case 4, 5 -> .070;   // thighs
            case 9, 10 -> .039;  // forearms
            case 11, 12 -> .075; // shins
            case 6 -> .032;      // cape/cloth
            case 7, 8 -> .024;   // independent elytra wings
            default -> .10;
        };
        double armor = entity instanceof Player player ? armorMassForRegion(player, region) : 0.0;
        return (fraction + armor) * scale;
    }

    private static double armorMassForRegion(Player player, int region) {
        double helmet = armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        double chest = armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
        double legs = armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
        double boots = armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
        return switch (region) {
            case 0 -> helmet * 0.45;
            case 1 -> chest * 0.64 + legs * 0.20;
            case 2, 3, 9, 10 -> chest * 0.09;
            case 4, 5 -> legs * 0.20;
            case 11, 12 -> legs * 0.20 + boots * 0.50;
            default -> 0.0;
        };
    }

    private static double playerArmorWeight(Player player) {
        return armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD))
                + armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST))
                + armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS))
                + armorItemWeight(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
    }

    private static double armorItemWeight(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        if (isElytra(stack)) return 0.055;
        if (path.contains("netherite")) return 0.340;
        if (path.contains("gold")) return 0.285;
        if (path.contains("diamond")) return 0.235;
        if (path.contains("iron")) return 0.190;
        if (path.contains("chain")) return 0.135;
        if (path.contains("turtle")) return 0.080;
        if (path.contains("leather")) return 0.040;
        return stack.has(DataComponents.EQUIPPABLE) ? 0.095 : 0.0;
    }

    private static Vec3 surfacePointTowards(RigidBodyPiece body, Vec3 target) {
        Vector3f localDirection = new Vector3f((float) (target.x - body.position.x),
                (float) (target.y - body.position.y), (float) (target.z - body.position.z));
        new Quaternionf(body.orientation).conjugate().transform(localDirection);
        double length = Math.sqrt(localDirection.lengthSquared());
        if (length < 1.0e-6) return body.position;
        double dx = localDirection.x / length, dy = localDirection.y / length, dz = localDirection.z / length;
        double scale = Math.min(Math.abs(dx) < 1.0e-7 ? Double.POSITIVE_INFINITY : body.halfExtents.x / Math.abs(dx),
                Math.min(Math.abs(dy) < 1.0e-7 ? Double.POSITIVE_INFINITY : body.halfExtents.y / Math.abs(dy),
                        Math.abs(dz) < 1.0e-7 ? Double.POSITIVE_INFINITY : body.halfExtents.z / Math.abs(dz)));
        Vector3f world = body.orientation.transform(new Vector3f((float) (dx * scale),
                (float) (dy * scale), (float) (dz * scale)));
        return body.position.add(world.x, world.y, world.z);
    }

    private static void configureJointMotor(RigidBodyPiece child) {
        child.jointType = child.parentRegion < 0 ? RigidBodyPiece.JointType.ROOT
                : child.region == 9 || child.region == 10 || child.region == 11 || child.region == 12
                ? RigidBodyPiece.JointType.HINGE
                : isClothRegion(child.region) ? RigidBodyPiece.JointType.CLOTH
                : isGripRegion(child.region) ? RigidBodyPiece.JointType.GRIP
                : RigidBodyPiece.JointType.CONE_TWIST;
        child.angularStiffness = child.playerBody
                ? (child.jointType == RigidBodyPiece.JointType.GRIP ? 0.085f
                : child.region == 0 ? 0.022f : child.region == 6 ? 0.002f
                : child.jointType == RigidBodyPiece.JointType.HINGE ? 0.005f
                : child.region >= 7 ? 0.004f : 0.0f)
                : (child.region == 0 ? 0.034f
                : isLimbRegion(child.region) ? 0.008f : 0.006f);
        child.angularDamping = child.playerBody
                ? (child.jointType == RigidBodyPiece.JointType.GRIP ? 0.14f
                : child.region == 0 ? 0.070f : child.region == 6 ? 0.025f
                : child.jointType == RigidBodyPiece.JointType.HINGE ? 0.032f
                : child.region >= 7 ? 0.035f : 0.0f)
                : (child.region == 0 ? 0.060f
                : isLimbRegion(child.region) ? 0.036f : 0.028f);
        child.angularLimit = switch (child.region) {
            case 0 -> child.playerBody ? radians(48, 58, 38) : radians(54, 62, 46);
            case 2, 3 -> child.playerBody ? radians(165, 145, 160) : radians(142, 112, 126);
            case 4, 5 -> child.playerBody ? radians(152, 118, 132) : radians(132, 92, 112);
            case 9, 10 -> radians(148, 13, 16);
            case 11, 12 -> radians(138, 9, 11);
            case 6 -> radians(52, 22, 14);
            case 7, 8 -> radians(42, 34, 52);
            case 30, 31 -> radians(7, 7, 9);
            default -> radians(52, 42, 40);
        };
    }

    private static Vec3 radians(double x, double y, double z) {
        return new Vec3(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
    }

    static boolean isAnatomicalRegion(int region) {
        return region >= 0 && region <= 5 || region >= 9 && region <= 12;
    }

    static boolean isLimbRegion(int region) {
        return region >= 2 && region <= 5 || region >= 9 && region <= 12;
    }

    static boolean isClothRegion(int region) {
        return region >= 6 && region <= 8 || region >= 13 && region <= 19;
    }

    static boolean isGripRegion(int region) { return region == 30 || region == 31; }

    static boolean isAttachmentRegion(int region) {
        return isClothRegion(region) || isGripRegion(region);
    }

    static int semanticRegion(int region) {
        return switch (region) {
            case 9 -> 2;
            case 10 -> 3;
            case 11 -> 4;
            case 12 -> 5;
            default -> region;
        };
    }

    private static Vec3 localPoint(RigidBodyPiece body, Vec3 worldPoint) {
        Vector3f local = new Vector3f((float) (worldPoint.x - body.position.x),
                (float) (worldPoint.y - body.position.y), (float) (worldPoint.z - body.position.z));
        new Quaternionf(body.orientation).conjugate().transform(local);
        return new Vec3(local.x, local.y, local.z);
    }

    private static Vec3 worldAnchor(RigidBodyPiece body, Vec3 localAnchor) {
        Vector3f world = body.orientation.transform(new Vector3f(
                (float) localAnchor.x, (float) localAnchor.y, (float) localAnchor.z));
        return body.position.add(world.x, world.y, world.z);
    }

    private BodyGeometry resolveGeometry(Entity entity, int region, Vec3 fallbackOffset, Vec3 fallbackHalf) {
        BodyGeometry cached = renderedPoseCache.getOrDefault(entity.getId(), Map.of()).get(region);
        if (cached != null) return cached;
        Set<String> used = new HashSet<>(detachedModelPaths.getOrDefault(entity.getId(), Map.of()).values());
        return calculateGeometry(entity, region, fallbackOffset, fallbackHalf, true, used);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BodyGeometry calculateGeometry(Entity entity, int region,
                                                   Vec3 fallbackOffset, Vec3 fallbackHalf,
                                                   boolean applyAnimation, Set<String> excludedPaths) {
        try {
            EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer living))
                return new BodyGeometry(fallbackOffset, fallbackHalf, resolveFaceUvs(entity, region), null, new Quaternionf());
            EntityModel model = living.getModel();
            if (applyAnimation) {
                EntityRenderState liveState = renderer.createRenderState(entity, 1.0f);
                model.setupAnim(liveState);
            }
            List<ModelCube> cubes = new ArrayList<>();
            model.root().visit(new PoseStack(), (pose, path, index, cube) ->
                    cubes.add(new ModelCube(cube, pose.copy(), path, transformedBounds(pose, cube))));
            if (cubes.isEmpty()) return new BodyGeometry(fallbackOffset, fallbackHalf,
                    resolveFaceUvs(entity, region), null, new Quaternionf());

            float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (ModelCube sample : cubes) {
                minX = Math.min(minX, sample.bounds[0]); maxX = Math.max(maxX, sample.bounds[1]);
                minY = Math.min(minY, sample.bounds[2]); maxY = Math.max(maxY, sample.bounds[3]);
                minZ = Math.min(minZ, sample.bounds[4]); maxZ = Math.max(maxZ, sample.bounds[5]);
            }
            float spanX = Math.max(1.0e-4f, maxX - minX), spanY = Math.max(1.0e-4f, maxY - minY);
            float spanZ = Math.max(1.0e-4f, maxZ - minZ);
            double wantedX = switch (region) { case 2 -> 0.08; case 3 -> 0.92; case 4 -> 0.32; case 5 -> 0.68; default -> 0.5; };
            double wantedY = switch (region) { case 0 -> 0.08; case 1 -> 0.36; case 2, 3 -> 0.38; default -> 0.82; };
            double wantedZ = 0.5;
            ModelCube selected = null;
            if (model instanceof HumanoidModel humanoid) {
                ModelPart basePart = switch (region) {
                    case 0 -> humanoid.head;
                    case 2 -> humanoid.rightArm;
                    case 3 -> humanoid.leftArm;
                    case 4 -> humanoid.rightLeg;
                    case 5 -> humanoid.leftLeg;
                    default -> humanoid.body;
                };
                if (!basePart.isEmpty()) {
                    ModelPart.Cube baseCube = basePart.getRandomCube(
                            RandomSource.create(0x5A17L + region * 31L));
                    for (ModelCube sample : cubes) if (sample.cube == baseCube) {
                        selected = sample;
                        break;
                    }
                }
            }
            double best = Double.POSITIVE_INFINITY;
            for (ModelCube sample : cubes) {
                if (selected != null) break;
                if (excludedPaths.contains(sample.path)) continue;
                double cx = (((sample.bounds[0] + sample.bounds[1]) * 0.5f) - minX) / spanX;
                double cy = (((sample.bounds[2] + sample.bounds[3]) * 0.5f) - minY) / spanY;
                double cz = (((sample.bounds[4] + sample.bounds[5]) * 0.5f) - minZ) / spanZ;
                double dx = cx - wantedX, dy = cy - wantedY, dz = cz - wantedZ;
                double score = dx * dx + dy * dy * 1.45 + dz * dz * 0.38
                        + semanticPathPenalty(sample.path, region);
                if (score < best) { best = score; selected = sample; }
            }
            if (selected == null) return new BodyGeometry(fallbackOffset, fallbackHalf,
                    resolveFaceUvs(entity, region), null, new Quaternionf());
            float[] b = selected.bounds;
            AABB box = entity.getBoundingBox();
            boolean playerGeometry = entity instanceof Player && model instanceof HumanoidModel;
            double scale = playerGeometry ? box.getXsize() / 0.60 * 0.96 : box.getYsize() / spanY;
            double modelCx = (minX + maxX) * 0.5, modelCy = (minY + maxY) * 0.5;
            double modelCz = (minZ + maxZ) * 0.5;
            double cubeCx = (b[0] + b[1]) * 0.5, cubeCy = (b[2] + b[3]) * 0.5;
            double cubeCz = (b[4] + b[5]) * 0.5;
            Vec3 modelOffset;
            Vec3 jointOffset;
            Vector3f pivot = selected.pose.pose().transformPosition(new Vector3f());
            if (playerGeometry) {
                Vec3 modelCenterClearance = switch (region) {
                    case 2 -> new Vec3(0.060, 0.0, 0.0);
                    case 3 -> new Vec3(-0.060, -0.018, 0.0);
                    case 4, 5 -> new Vec3(0.0, -0.032, 0.0);
                    default -> Vec3.ZERO;
                };
                Vec3 jointPivotClearance = switch (region) {
                    case 2 -> new Vec3(0.050, 0.055, 0.0);
                    case 3 -> new Vec3(-0.050, 0.055, 0.0);
                    case 4, 5 -> new Vec3(0.0, -0.032, 0.0);
                    default -> Vec3.ZERO;
                };
                Vec3 fromFeet = new Vec3(-cubeCx * scale,
                        (1.5 - cubeCy) * scale, cubeCz * scale).add(modelCenterClearance);
                float bodyYaw = ((Player) entity).getPreciseBodyRotation(1.0f);
                Vec3 worldCenter = entity.position().add(RagdollMath.rotateY(
                        fromFeet, Math.toRadians(180.0f - bodyYaw)));
                modelOffset = worldCenter.subtract(box.getCenter());
                Vec3 pivotFromFeet = new Vec3(-pivot.x * scale,
                        (1.5 - pivot.y) * scale, pivot.z * scale).add(jointPivotClearance);
                jointOffset = entity.position().add(RagdollMath.rotateY(pivotFromFeet,
                        Math.toRadians(180.0f - bodyYaw))).subtract(box.getCenter());
            } else {
                modelOffset = new Vec3(-(cubeCx - modelCx) * scale,
                        -(cubeCy - modelCy) * scale, (cubeCz - modelCz) * scale);
                Vec3 pivotOffset = new Vec3(-(pivot.x - modelCx) * scale,
                        -(pivot.y - modelCy) * scale, (pivot.z - modelCz) * scale);
                jointOffset = RagdollMath.rotateY(pivotOffset, Math.toRadians(entity.getYRot()));
            }
            Vec3 offset = playerGeometry ? modelOffset
                    : RagdollMath.rotateY(modelOffset, Math.toRadians(entity.getYRot()));
            ModelPart.Cube source = selected.cube;
            Vector3f sourceX = new Vector3f((source.maxX - source.minX) / 32.0f, 0, 0);
            Vector3f sourceY = new Vector3f(0, (source.maxY - source.minY) / 32.0f, 0);
            Vector3f sourceZ = new Vector3f(0, 0, (source.maxZ - source.minZ) / 32.0f);
            selected.pose.pose().transformDirection(sourceX);
            selected.pose.pose().transformDirection(sourceY);
            selected.pose.pose().transformDirection(sourceZ);
            Vec3 half = new Vec3(Math.max(0.035, sourceX.length() * scale),
                    Math.max(0.035, sourceY.length() * scale), Math.max(0.035, sourceZ.length() * scale));
            Quaternionf partRotation = selected.pose.pose().getUnnormalizedRotation(new Quaternionf()).normalize();
            float bodyYaw = entity instanceof LivingEntity livingEntity
                    ? livingEntity.getPreciseBodyRotation(1.0f) : entity.getYRot();
            Quaternionf orientation = new Quaternionf().rotationY((float) Math.toRadians(180.0f - bodyYaw))
                    .rotateZ((float) Math.PI).mul(partRotation).normalize();
            float[][] overlayUvs = null;
            if (model instanceof PlayerModel playerModel) {
                ModelPart overlayPart = switch (region) {
                    case 0 -> playerModel.hat;
                    case 2 -> playerModel.rightSleeve;
                    case 3 -> playerModel.leftSleeve;
                    case 4 -> playerModel.rightPants;
                    case 5 -> playerModel.leftPants;
                    default -> playerModel.jacket;
                };
                if (!overlayPart.isEmpty())
                    overlayUvs = uvFaces(overlayPart.getRandomCube(
                            RandomSource.create(0x0A71E2L + region * 43L)));
            }
            return new BodyGeometry(offset, half, uvFaces(source), overlayUvs,
                    orientation, selected.path, jointOffset);
        } catch (RuntimeException ignored) {
            return new BodyGeometry(fallbackOffset, fallbackHalf,
                    resolveFaceUvs(entity, region), null, new Quaternionf());
        }
    }

    private static float[] transformedBounds(PoseStack.Pose pose, ModelPart.Cube cube) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int xi = 0; xi < 2; xi++) for (int yi = 0; yi < 2; yi++) for (int zi = 0; zi < 2; zi++) {
            Vector3f point = new Vector3f((xi == 0 ? cube.minX : cube.maxX) / 16.0f,
                    (yi == 0 ? cube.minY : cube.maxY) / 16.0f,
                    (zi == 0 ? cube.minZ : cube.maxZ) / 16.0f);
            pose.pose().transformPosition(point);
            minX = Math.min(minX, point.x); maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y); maxY = Math.max(maxY, point.y);
            minZ = Math.min(minZ, point.z); maxZ = Math.max(maxZ, point.z);
        }
        return new float[] { minX, maxX, minY, maxY, minZ, maxZ };
    }

    private record ModelCube(ModelPart.Cube cube, PoseStack.Pose pose, String path, float[] bounds) { }
    private record BodyGeometry(Vec3 offset, Vec3 halfExtents, float[][] faceUvs, float[][] overlayFaceUvs,
                                Quaternionf orientation, String modelPath, Vec3 jointOffset) {
        private BodyGeometry(Vec3 offset, Vec3 halfExtents, float[][] faceUvs,
                             float[][] overlayFaceUvs, Quaternionf orientation) {
            this(offset, halfExtents, faceUvs, overlayFaceUvs, orientation, null, null);
        }
    }

    public void captureRenderedPose(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ragdolled.contains(entityId)) return;
        Entity entity = minecraft.level.getEntity(entityId);
        if (!(entity instanceof LivingEntity)) return;
        AABB box = entity.getBoundingBox();
        double width = box.getXsize(), height = box.getYsize(), depth = box.getZsize();
        Map<Integer, BodyGeometry> pose = new HashMap<>();
        for (int region = 0; region <= 5; region++) {
            Vec3 fallbackOffset = switch (region) {
                case 0 -> new Vec3(0, height * 0.39, 0);
                case 2 -> new Vec3(-width * 0.62, height * 0.12, 0);
                case 3 -> new Vec3(width * 0.62, height * 0.12, 0);
                case 4 -> new Vec3(-width * 0.22, -height * 0.31, 0);
                case 5 -> new Vec3(width * 0.22, -height * 0.31, 0);
                default -> Vec3.ZERO;
            };
            Vec3 fallbackHalf = switch (region) {
                case 0 -> new Vec3(width * 0.34, height * 0.13, depth * 0.34);
                case 2, 3 -> new Vec3(width * 0.16, height * 0.23, depth * 0.18);
                case 4, 5 -> new Vec3(width * 0.18, height * 0.24, depth * 0.20);
                default -> new Vec3(width * 0.38, height * 0.22, depth * 0.28);
            };
            BodyGeometry geometry = calculateGeometry(entity, region, fallbackOffset, fallbackHalf,
                    false, pose.values().stream().map(BodyGeometry::modelPath)
                            .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
            pose.put(region, geometry);
        }
        renderedPoseCache.put(entityId, pose);
    }

    private static double semanticPathPenalty(String path, int region) {
        String name = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        boolean left = name.contains("left") || name.contains("_l") || name.endsWith("/l");
        boolean right = name.contains("right") || name.contains("_r") || name.endsWith("/r");
        double side = switch (region) {
            case 2, 4 -> left ? 0.28 : right ? -0.28 : 0.0;
            case 3, 5 -> right ? 0.28 : left ? -0.28 : 0.0;
            default -> 0.0;
        };
        return side + switch (region) {
            case 0 -> name.contains("head") || name.contains("skull") ? -0.85 : 0.0;
            case 1 -> name.contains("body") || name.contains("torso") || name.contains("chest") ? -0.72 : 0.0;
            case 2, 3 -> name.contains("arm") || name.contains("front") || name.contains("fore") ? -0.42 : 0.0;
            case 4, 5 -> name.contains("leg") || name.contains("hind") || name.contains("rear") ? -0.38 : 0.0;
            default -> 0.0;
        };
    }

    private static float[][] resolveFaceUvs(Entity entity, int region) {
        try {
            EntityRenderer<?, ?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            if (renderer instanceof LivingEntityRenderer<?, ?, ?> living) {
                EntityModel<?> model = living.getModel();
                ModelPart part = null;
                if (model instanceof HumanoidModel<?> humanoid) part = switch (region) {
                    case 0 -> humanoid.head;
                    case 2 -> humanoid.rightArm;
                    case 3 -> humanoid.leftArm;
                    case 4 -> humanoid.rightLeg;
                    case 5 -> humanoid.leftLeg;
                    default -> humanoid.body;
                };
                if (part != null && !part.isEmpty())
                    return uvFaces(part.getRandomCube(RandomSource.create(region * 7919L + entity.getId())));
                List<ModelPart> parts = new ArrayList<>();
                for (Object value : model.allParts()) {
                    ModelPart candidate = (ModelPart) value;
                    if (!candidate.isEmpty()) parts.add(candidate);
                }
                parts.sort(java.util.Comparator.comparingDouble(candidate -> candidate.y));
                if (!parts.isEmpty()) {
                    int last = parts.size() - 1;
                    int index = switch (region) {
                        case 0 -> 0;
                        case 4 -> last;
                        case 5 -> Math.max(0, last - 1);
                        case 2 -> Math.max(0, parts.size() / 2 - 1);
                        case 3 -> Math.min(last, parts.size() / 2 + 1);
                        default -> parts.size() / 2;
                    };
                    return uvFaces(parts.get(index).getRandomCube(RandomSource.create(region * 3571L)));
                }
            }
        } catch (RuntimeException ignored) { }
        return fullFaceUvs();
    }

    static float[][] uvFaces(ModelPart.Cube cube) {
        float[][] result = new float[6][];
        int[][] cornerOrder = {
                {0, 4, 6, 2}, {1, 3, 7, 5}, {0, 1, 5, 4},
                {2, 6, 7, 3}, {0, 2, 3, 1}, {4, 5, 7, 6}
        };
        for (ModelPart.Polygon polygon : cube.polygons) {
            float ax = Math.abs(polygon.normal().x()), ay = Math.abs(polygon.normal().y());
            int face = ax > ay && ax > Math.abs(polygon.normal().z())
                    ? (polygon.normal().x() < 0 ? 0 : 1)
                    : ay > Math.abs(polygon.normal().z()) ? (polygon.normal().y() < 0 ? 2 : 3)
                    : (polygon.normal().z() < 0 ? 4 : 5);
            float[] ordered = new float[8];
            boolean[] assigned = new boolean[4];
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                int corner = (Math.abs(vertex.x() - cube.maxX) < Math.abs(vertex.x() - cube.minX) ? 1 : 0)
                        | (Math.abs(vertex.y() - cube.maxY) < Math.abs(vertex.y() - cube.minY) ? 2 : 0)
                        | (Math.abs(vertex.z() - cube.maxZ) < Math.abs(vertex.z() - cube.minZ) ? 4 : 0);
                for (int slot = 0; slot < 4; slot++) if (cornerOrder[face][slot] == corner) {
                    ordered[slot * 2] = vertex.u();
                    ordered[slot * 2 + 1] = vertex.v();
                    assigned[slot] = true;
                    break;
                }
            }
            if (assigned[0] && assigned[1] && assigned[2] && assigned[3]) result[face] = ordered;
        }
        float[] smallest = null;
        float smallestArea = Float.POSITIVE_INFINITY;
        for (ModelPart.Polygon polygon : cube.polygons) {
            float minU = Float.POSITIVE_INFINITY, minV = Float.POSITIVE_INFINITY;
            float maxU = Float.NEGATIVE_INFINITY, maxV = Float.NEGATIVE_INFINITY;
            for (ModelPart.Vertex vertex : polygon.vertices()) {
                minU = Math.min(minU, vertex.u()); maxU = Math.max(maxU, vertex.u());
                minV = Math.min(minV, vertex.v()); maxV = Math.max(maxV, vertex.v());
            }
            float area = Math.max(0.0f, maxU - minU) * Math.max(0.0f, maxV - minV);
            if (area > 0.0f && area < smallestArea) {
                smallestArea = area;
                smallest = new float[] {minU, maxV, maxU, maxV, maxU, minV, minU, minV};
            }
        }
        if (smallest == null) smallest = new float[] {0, 0, 0, 0, 0, 0, 0, 0};
        for (int face = 0; face < result.length; face++)
            if (result[face] == null) result[face] = smallest.clone();
        return result;
    }

    private static float[][] fullFaceUvs() {
        float[][] result = new float[6][];
        for (int i = 0; i < result.length; i++)
            result[i] = new float[] { 0, 1, 1, 1, 1, 0, 0, 0 };
        return result;
    }

    private static float[][] capeFaceUvs() {
        try {
            ModelPart cape = PlayerCapeModel.createCapeLayer().bakeRoot().getChild("cape");
            if (!cape.isEmpty()) return flipCapeVertical(
                    uvFaces(cape.getRandomCube(RandomSource.create(0xCA9EL))));
        } catch (RuntimeException ignored) { }
        return flipCapeVertical(new float[][] {
                uvRect(0, 1, 1, 17, 64, 32),
                uvRect(11, 1, 12, 17, 64, 32),
                uvRect(1, 0, 11, 1, 64, 32),
                uvRect(12, 0, 22, 1, 64, 32),
                uvRect(1, 1, 11, 17, 64, 32),
                uvRect(12, 1, 22, 17, 64, 32)
        });
    }

    private static float[][] flipCapeVertical(float[][] source) {
        float[][] result = new float[source.length][];
        for (int face = 0; face < source.length; face++) {
            float[] uv = source[face];
            result[face] = new float[] {uv[6], uv[7], uv[4], uv[5],
                    uv[2], uv[3], uv[0], uv[1]};
        }
        return result;
    }

    private static float[][] elytraWingFaceUvs(boolean left) {
        try {
            ModelPart root = ElytraModel.createLayer().bakeRoot();
            ModelPart wing = root.getChild(left ? "left_wing" : "right_wing");
            if (!wing.isEmpty()) return uvFaces(wing.getRandomCube(RandomSource.create(left ? 7L : 8L)));
        } catch (RuntimeException ignored) { }
        return fullFaceUvs();
    }

    private static float[] uvRect(float u0, float v0, float u1, float v1,
                                  float width, float height) {
        return new float[] {u0 / width, v1 / height, u1 / width, v1 / height,
                u1 / width, v0 / height, u0 / width, v0 / height};
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Identifier resolveTexture(Entity entity) {
        try {
            EntityRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            EntityRenderState state = renderer.createRenderState(entity, 1.0f);
            if (renderer instanceof LivingEntityRenderer living && state instanceof LivingEntityRenderState)
                return living.getTextureLocation((LivingEntityRenderState) state);
        } catch (RuntimeException ignored) { }
        return Identifier.withDefaultNamespace("textures/block/red_concrete.png");
    }

    private static boolean isRagdollExcluded(Entity entity) {
        if (entity == null) return true;
        if (entity instanceof MinotaurEntity) return true;
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return path.equals("ender_dragon");
    }

    public boolean handleRightClick(Minecraft client, boolean down, boolean pressed) {
        if (!down) {
            grabbed = null;
            smoothedGrabTarget = null;
            return false;
        }
        if (pressed && grabbed == null && client.level != null && client.player != null) {
            Vec3 eye = client.player.getEyePosition();
            Vec3 look = client.player.getViewVector(1.0f).normalize();
            Vec3 end = eye.add(look.scale(8.0));
            BlockHitResult blockHit = client.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, client.player));
            double nearest = blockHit.getType() == HitResult.Type.MISS
                    ? 8.0 : eye.distanceTo(blockHit.getLocation());
            for (RigidBodyPiece piece : pieces) {
                Vec3 hit = boundsAt(piece, piece.position).inflate(0.08).clip(eye, end).orElse(null);
                if (hit == null) continue;
                double distance = eye.distanceTo(hit);
                if (distance < nearest) { nearest = distance; grabbed = piece; }
            }
            if (grabbed != null) {
                grabDistance = Math.max(1.25, nearest);
                smoothedGrabTarget = grabbed.position;
            }
        }
        if (grabbed == null || client.player == null) return false;
        Vec3 target = client.player.getEyePosition()
                .add(client.player.getViewVector(1.0f).normalize().scale(grabDistance));
        smoothedGrabTarget = smoothedGrabTarget == null ? target : smoothedGrabTarget.lerp(target,
                RagdollRuntime.INSTANCE.config.grabSmoothing);
        Vec3 error = smoothedGrabTarget.subtract(grabbed.position);
        if (RagdollRuntime.INSTANCE.config.ragdollGrabBreak
                && error.length() > RagdollRuntime.INSTANCE.config.ragdollGrabBreakDistance) {
            grabbed = null;
            smoothedGrabTarget = null;
            return false;
        }
        double spring = RagdollRuntime.INSTANCE.config.grabStrength * 0.42;
        Vec3 acceleration = error.scale(spring).subtract(grabbed.velocity.scale(0.16));
        if (acceleration.length() > 0.42) acceleration = acceleration.normalize().scale(0.42);
        grabbed.velocity = grabbed.velocity.add(acceleration).scale(0.94);
        grabbed.angularVelocity = grabbed.angularVelocity.scale(0.88);
        wakeGroup(grabbed.entityId);
        return true;
    }

    public void togglePlayerTumble(Minecraft client) {
        if (client.level == null || client.player == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            return;
        }
        int entityId = client.player.getId();
        if (playerTumbles.contains(entityId)) {
            if (isElectrified(entityId)) return;
            RagdollConfig config = RagdollRuntime.INSTANCE.config;
            int elapsed = traumaDecayTicker - tumbleStartedAt.getOrDefault(entityId, traumaDecayTicker);
            if (!config.ragdollManualExit || elapsed < config.ragdollMinExitTicks
                    || !hasGroundContact(entityId)) return;
            Vec3 exit = findSafeTumbleExit(client, entityId);
            Vec3 exitVelocity = ragdollVelocity(entityId);
            removeRagdoll(entityId);
            if (exit != null) {
                if (ClientPlayNetworking.canSend(TumbleExitPayload.TYPE))
                    ClientPlayNetworking.send(new TumbleExitPayload(exit.x, exit.y, exit.z,
                            exitVelocity.x, exitVelocity.y, exitVelocity.z));
                client.player.setPos(exit.x, exit.y, exit.z);
            }
            client.player.setDeltaMovement(exitVelocity);
            return;
        }
        if (ragdolled.contains(entityId)) return;
        Vec3 look = client.player.getViewVector(1.0f);
        Vec3 launch = client.player.getDeltaMovement();
        if (ragdoll(client.player, 1, client.player.getBoundingBox().getCenter(), look,
                Math.max(0.15, launch.length() * 0.35), false)) {
            playerTumbles.add(entityId);
            tumbleStartedAt.put(entityId, traumaDecayTicker);
            for (RigidBodyPiece part : pieces) if (part.entityId == entityId) {
                part.velocity = launch;
                part.angularVelocity = Vec3.ZERO;
                part.bounces = 0;
            }
            applyFracturePose(entityId);
        }
    }

    public void forcePlayerTumble(Minecraft client, Vec3 sourcePosition, Vec3 impulse, float force) {
        if (client.player == null || client.level == null
                || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        int entityId = client.player.getId();
        if (!playerTumbles.contains(entityId)) togglePlayerTumble(client);
        applyFracturePose(entityId);
        if (playerTumbles.contains(entityId))
            externalDamage(client.player, sourcePosition, impulse, Math.max(0.1f, force), true);
    }

    public void electrify(Minecraft client, LivingEntity entity, Vec3 sourcePosition,
                          Vec3 impulse, int durationTicks) {
        if (client.level == null || entity == null || !inAsterion(entity)
                || isRagdollExcluded(entity)) return;
        int entityId = entity.getId();
        electrifiedUntil.merge(entityId, traumaDecayTicker + Math.max(1, durationTicks), Math::max);
        if (client.player == entity) {
            if (playerTumbles.contains(entityId))
                externalDamage(client.player, sourcePosition, impulse, 0.65F, true);
        } else if (!ragdolled.contains(entityId)) {
            Vec3 direction = impulse.lengthSqr() > 1.0E-8D ? impulse.normalize() : new Vec3(0, -1, 0);
            ragdoll(entity, 1, entity.getBoundingBox().getCenter(), direction, 1.15D, false);
        }
        if (ragdolled.contains(entityId)) applyWailing(entityId, 0.012F, durationTicks, 3);
    }

    public boolean isElectrified(int entityId) {
        return electrifiedUntil.getOrDefault(entityId, Integer.MIN_VALUE) > traumaDecayTicker;
    }

    public void reconcilePlayerAuthority(Minecraft client, Vec3 position, Vec3 velocity,
                                         long serverTick) {
        if (client.player == null || serverTick <= lastAuthorityTick
                || !playerTumbles.contains(client.player.getId())) return;
        lastAuthorityTick = serverTick;
        int entityId = client.player.getId();
        Vec3 positionalError = position.subtract(client.player.position());
        double errorLength = positionalError.length();
        if (errorLength > 4.0D) {
            for (RigidBodyPiece part : pieces) if (part.entityId == entityId) {
                part.position = part.position.add(positionalError);
                part.previous = part.previous.add(positionalError);
                part.velocity = velocity;
                part.sleeping = false;
            }
            client.player.setPos(position.x, position.y, position.z);
        } else if (errorLength > 0.45D) {
            Vec3 correction = positionalError.scale(Math.min(0.22, 0.18 / errorLength));
            for (RigidBodyPiece part : pieces) if (part.entityId == entityId) {
                part.position = part.position.add(correction);
                part.previous = part.previous.add(correction);
            }
            client.player.setPos(client.player.position().add(correction));
        }
        RigidBodyPiece torso = find(entityId, 1);
        if (torso != null && torso.velocity.subtract(velocity).length() > 0.35)
            torso.velocity = torso.velocity.lerp(velocity, 0.08);
    }

    public void setPlayerFracture(Minecraft client, int region) {
        if (client.player == null) return;
        int entityId = client.player.getId();
        if (region != 4 && region != 5) {
            RigidBodyPiece leg = find(entityId, playerFracturedLegs.getOrDefault(entityId, -1));
            RigidBodyPiece parent = leg == null ? null : find(entityId, leg.parentRegion);
            if (leg != null && parent != null) leg.jointRestOrientation.set(
                    new Quaternionf(parent.orientation).conjugate().mul(leg.orientation).normalize());
            playerFracturedLegs.remove(entityId);
            appliedFracturePoses.remove(entityId);
            return;
        }
        Integer oldRegion = playerFracturedLegs.put(entityId, region);
        if (oldRegion == null || oldRegion != region) appliedFracturePoses.remove(entityId);
        applyFracturePose(entityId);
    }

    private void applyFracturePose(int entityId) {
        Integer region = playerFracturedLegs.get(entityId);
        if (region == null || appliedFracturePoses.contains(entityId)) return;
        RigidBodyPiece leg = find(entityId, region);
        RigidBodyPiece torso = leg == null ? null : find(entityId, leg.parentRegion);
        if (leg == null || torso == null) return;
        float outward = region == 4 ? -0.52f : 0.52f;
        Quaternionf brokenOffset = new Quaternionf().rotationXYZ(0.68f,
                region == 4 ? -0.20f : 0.20f, outward);
        leg.jointRestOrientation.mul(brokenOffset).normalize();
        leg.orientation.set(new Quaternionf(torso.orientation)
                .mul(leg.jointRestOrientation).normalize());
        leg.previousOrientation.set(leg.orientation);
        leg.angularVelocity = leg.angularVelocity.add(region == 4
                ? new Vec3(0.03, -0.015, -0.025) : new Vec3(0.03, 0.015, 0.025));
        leg.sleeping = false;
        appliedFracturePoses.add(entityId);
        wakeGroup(entityId);
    }

    public void applyPlayerTumbleInput(Minecraft client, float strafe, float forward) {
        if (client.player == null || !playerTumbles.contains(client.player.getId())) return;
        RigidBodyPiece torso = find(client.player.getId(), 1);
        if (torso == null) return;
        if (Math.max(Math.abs(strafe), Math.abs(forward)) > 0.001f
                && torso.angularVelocity.length() < 0.30) {
            Vector3f localTorque = new Vector3f(forward * 0.028f, -strafe * 0.026f, strafe * 0.010f);
            torso.orientation.transform(localTorque);
            torso.angularVelocity = torso.angularVelocity.add(localTorque.x, localTorque.y, localTorque.z);
            torso.sleeping = false;
            wakeGroup(torso.entityId);
        }
    }

    public void followPlayerTumble(Minecraft client) {
        if (client.player == null || !playerTumbles.contains(client.player.getId())) return;
        RigidBodyPiece torso = find(client.player.getId(), 1);
        Vec3 trackingPosition = findSafeTumbleExit(client, client.player.getId());
        if (trackingPosition != null) {
            client.player.setPos(trackingPosition.x, trackingPosition.y, trackingPosition.z);
            if (torso != null && ClientPlayNetworking.canSend(TumbleExitPayload.TYPE))
                ClientPlayNetworking.send(new TumbleExitPayload(
                        trackingPosition.x, trackingPosition.y, trackingPosition.z,
                        torso.velocity.x, torso.velocity.y, torso.velocity.z));
        }
        client.player.setDeltaMovement(Vec3.ZERO);
    }

    public boolean isPlayerTumbling(int entityId) { return playerTumbles.contains(entityId); }

    public Vec3 ragdollVelocity(int entityId) {
        RigidBodyPiece torso = find(entityId, 1);
        return torso == null ? Vec3.ZERO : torso.velocity;
    }

    public int ragdollElapsedTicks(int entityId) {
        return Math.max(0, traumaDecayTicker - ragdollStartedAt.getOrDefault(entityId, traumaDecayTicker));
    }

    RigidWoundPose woundPose(int entityId, int region, Vec3 uvw, Vec3 modelNormal) {
        RigidBodyPiece body = find(entityId, region);
        if (body == null) return null;
        Vec3 h = body.halfExtents;
        double x = Mth.lerp(uvw.x, -h.x, h.x);
        double y = Mth.lerp(uvw.y, h.y, -h.y);
        double z = Mth.lerp(uvw.z, -h.z, h.z);
        Vec3 localNormal = RagdollMath.safeNormalize(modelNormal, new Vec3(0, 0, 1));
        double ax = Math.abs(localNormal.x), ay = Math.abs(localNormal.y), az = Math.abs(localNormal.z);
        if (ax >= ay && ax >= az) x = Math.copySign(h.x, localNormal.x);
        else if (ay >= az) y = Math.copySign(h.y, localNormal.y);
        else z = Math.copySign(h.z, localNormal.z);
        Vector3f point = body.orientation.transform(new Vector3f((float) x, (float) y, (float) z));
        Vector3f transformedNormal = body.orientation.transform(new Vector3f(
                (float) localNormal.x, (float) localNormal.y, (float) localNormal.z));
        Vec3 position = body.position.add(point.x, point.y, point.z);
        Vec3 normal = RagdollMath.safeNormalize(new Vec3(transformedNormal.x,
                transformedNormal.y, transformedNormal.z), new Vec3(0, 1, 0));
        return new RigidWoundPose(position, normal, body.velocityAt(position));
    }

    record RigidWoundPose(Vec3 position, Vec3 normal, Vec3 velocity) { }

    WoundProjection projectWound(int entityId, Vec3 worldPoint, Vec3 worldNormal) {
        RigidBodyPiece closest = null;
        Vec3 closestLocal = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (RigidBodyPiece body : pieces) {
            if (body.entityId != entityId || !isAnatomicalRegion(body.region)) continue;
            Vector3f localVector = new Vector3f(
                    (float) (worldPoint.x - body.position.x),
                    (float) (worldPoint.y - body.position.y),
                    (float) (worldPoint.z - body.position.z));
            new Quaternionf(body.orientation).conjugate().transform(localVector);
            Vec3 local = new Vec3(
                    Mth.clamp(localVector.x, -body.halfExtents.x, body.halfExtents.x),
                    Mth.clamp(localVector.y, -body.halfExtents.y, body.halfExtents.y),
                    Mth.clamp(localVector.z, -body.halfExtents.z, body.halfExtents.z));
            Vector3f projected = body.orientation.transform(new Vector3f(
                    (float) local.x, (float) local.y, (float) local.z));
            Vec3 surface = body.position.add(projected.x, projected.y, projected.z);
            double distance = surface.distanceToSqr(worldPoint);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = body;
                closestLocal = local;
            }
        }
        if (closest == null) return null;
        Vector3f localNormalVector = new Quaternionf(closest.orientation).conjugate()
                .transform(new Vector3f((float) worldNormal.x,
                        (float) worldNormal.y, (float) worldNormal.z));
        Vec3 localNormal = RagdollMath.safeNormalize(new Vec3(localNormalVector.x,
                localNormalVector.y, localNormalVector.z), new Vec3(0, 0, 1));
        Vec3 h = closest.halfExtents;
        Vec3 uvw = new Vec3(
                Mth.clamp((closestLocal.x + h.x) / Math.max(1.0e-5, h.x * 2.0), 0.0, 1.0),
                Mth.clamp((h.y - closestLocal.y) / Math.max(1.0e-5, h.y * 2.0), 0.0, 1.0),
                Mth.clamp((closestLocal.z + h.z) / Math.max(1.0e-5, h.z * 2.0), 0.0, 1.0));
        return new WoundProjection(closest.region, uvw, localNormal);
    }

    record WoundProjection(int region, Vec3 modelPosition, Vec3 modelNormal) { }

    public void releaseRagdoll(int entityId) {
        if (isElectrified(entityId)) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getId() == entityId && playerTumbles.contains(entityId)) {
            if (!hasGroundContact(entityId)) return;
            Vec3 exit = findSafeTumbleExit(client, entityId);
            Vec3 exitVelocity = ragdollVelocity(entityId);
            removeRagdoll(entityId);
            if (exit != null) {
                if (ClientPlayNetworking.canSend(TumbleExitPayload.TYPE))
                    ClientPlayNetworking.send(new TumbleExitPayload(exit.x, exit.y, exit.z,
                            exitVelocity.x, exitVelocity.y, exitVelocity.z));
                client.player.setPos(exit.x, exit.y, exit.z);
            }
            client.player.setDeltaMovement(exitVelocity);
        } else removeRagdoll(entityId);
    }

    public boolean hasGroundContact(int entityId) {
        for (RigidBodyPiece part : pieces) {
            if (part.entityId == entityId && part.playerBody && isAnatomicalRegion(part.region)
                    && part.region != 0 && part.supportTicks >= 2) return true;
        }
        return false;
    }

    public Vec3 ragdollHandPosition(int entityId, boolean rightArm) {
        RigidBodyPiece arm = find(entityId, rightArm ? 2 : 3);
        if (arm == null) return null;
        Vector3f localHand = new Vector3f(0.0F, (float) -arm.halfExtents.y, 0.0F);
        arm.orientation.transform(localHand);
        return arm.position.add(localHand.x, localHand.y, localHand.z);
    }

    public void applyWailing(int entityId, float stiffness, int durationTicks, int intervalTicks) {
        if (!ragdolled.contains(entityId)) return;
        wailing.put(entityId, new WailingState(Math.max(0.002f, stiffness),
                traumaDecayTicker + Math.max(1, durationTicks), Math.max(1, intervalTicks), traumaDecayTicker));
        wakeGroup(entityId);
    }

    public void stopWailing(int entityId) { wailing.remove(entityId); }

    public Vec3 playerTumbleCameraPosition(int entityId, float partialTick) {
        if (!playerTumbles.contains(entityId)) return null;
        RigidBodyPiece head = find(entityId, 0);
        RigidBodyPiece target = head == null ? find(entityId, 1) : head;
        return target == null ? null : target.previous.lerp(target.position, Mth.clamp(partialTick, 0, 1));
    }

    public Vec3 tumbleCameraAnchor(int entityId, float partialTick) {
        if (!playerTumbles.contains(entityId)) return null;
        RigidBodyPiece torso = find(entityId, 1);
        return torso == null ? null : torso.previous.lerp(torso.position,
                Mth.clamp(partialTick, 0, 1));
    }

    public Vec3 pushCameraOutsideRagdoll(int entityId, Vec3 camera) {
        Vec3 result = camera;
        for (int iteration = 0; iteration < 4; iteration++) {
            PointEscape deepest = null;
            for (RigidBodyPiece part : pieces) {
                if (part.entityId != entityId || part.region == 0
                        || isAttachmentRegion(part.region)) continue;
                PointEscape escape = pointEscape(part, result, -0.105);
                if (escape != null && (deepest == null || escape.depth > deepest.depth))
                    deepest = escape;
            }
            if (deepest == null) break;
            result = result.add(deepest.outward.scale(deepest.depth + 0.012));
        }
        return result;
    }

    private Vec3 findSafeTumbleExit(Minecraft client, int entityId) {
        RigidBodyPiece torso = find(entityId, 1);
        if (torso == null || client.player == null || client.level == null) return null;
        double halfHeight = client.player.getBoundingBox().getYsize() * 0.5;
        Vec3 base = new Vec3(torso.position.x, torso.position.y - halfHeight + 0.08, torso.position.z);
        AABB playerBox = client.player.getBoundingBox();
        for (int step = 0; step <= 28; step++) {
            Vec3 candidate = base.add(0, step * 0.075, 0);
            AABB destination = playerBox.move(candidate.subtract(client.player.position())).deflate(0.001);
            if (client.level.noCollision(client.player, destination)) return candidate;
        }
        return client.player.position();
    }

    private void removeRagdoll(int entityId) {
        pieces.removeIf(part -> {
            if (part.entityId != entityId) return false;
            if (grabbed == part) grabbed = null;
            return true;
        });
        playerTumbles.remove(entityId);
        appliedFracturePoses.remove(entityId);
        tumbleStartedAt.remove(entityId);
        ragdollStartedAt.remove(entityId);
        wailing.remove(entityId);
        detached.remove(entityId);
        detachedModelPaths.remove(entityId);
        ragdolled.remove(entityId);
        islandSleepTicks.remove(entityId);
        rigidSoundAges.remove(entityId);
        remoteDriven.remove(entityId);
        remotePoseSequences.remove(entityId);
        remotePoseTicks.remove(entityId);
    }

    public void applyRemotePose(Minecraft client, RagdollPosePayload payload) {
        if (client.level == null || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            return;
        }
        if (payload.parts().isEmpty()) {
            return;
        }
        if (client.player != null && payload.entityId() == client.player.getId()) {
            return;
        }
        Integer previousSequence = remotePoseSequences.get(payload.entityId());
        if (previousSequence != null
                && Integer.compareUnsigned(payload.sequence(), previousSequence) <= 0) return;
        Entity owner = client.level.getEntity(payload.entityId());
        if (!ragdolled.contains(payload.entityId())) {
            if (owner == null) return;
            ragdoll(owner, 1, owner.getBoundingBox().getCenter(), Vec3.ZERO, 0.0, false);
        }
        remoteDriven.add(payload.entityId());
        remotePoseSequences.put(payload.entityId(), payload.sequence());
        remotePoseTicks.put(payload.entityId(), traumaDecayTicker);
        for (RagdollPosePayload.Part snapshot : payload.parts()) {
            RigidBodyPiece part = find(payload.entityId(), snapshot.region());
            if (part == null) continue;
            Vec3 transmittedVelocity = new Vec3(snapshot.vx(), snapshot.vy(), snapshot.vz());
            double speed = transmittedVelocity.length();
            Vec3 target = new Vec3(snapshot.x(), snapshot.y(), snapshot.z())
                    .add(transmittedVelocity.scale(Mth.clamp(0.65 + speed * 0.18, 0.65, 1.15)));
            double error = part.position.distanceTo(target);
            double positionBlend = Mth.clamp(0.58 + speed * 0.10 + error * 0.16, 0.58, 0.94);
            Quaternionf targetRotation = new Quaternionf(snapshot.qx(), snapshot.qy(),
                    snapshot.qz(), snapshot.qw());
            if (targetRotation.lengthSquared() < 0.0001f) targetRotation.identity();
            else targetRotation.normalize();
            part.position = error > 4.0 ? target : part.position.lerp(target, positionBlend);
            part.orientation.slerp(targetRotation,
                    (float) Mth.clamp(positionBlend + speed * 0.025, 0.62, 0.96)).normalize();
            part.velocity = part.velocity.lerp(transmittedVelocity,
                    Mth.clamp(0.70 + speed * 0.06, 0.70, 0.96));
            part.sleeping = false;
        }
    }

    private void sendPoseSnapshots() {
        if ((traumaDecayTicker & 1) != 0 || !ClientPlayNetworking.canSend(RagdollPosePayload.TYPE)) return;
        for (int entityId : ragdolled) {
            if (remoteDriven.contains(entityId)) continue;
            List<RagdollPosePayload.Part> snapshot = new ArrayList<>(10);
            for (RigidBodyPiece part : pieces) {
                if (part.entityId != entityId || snapshot.size() >= 16) continue;
                Quaternionf q = part.orientation;
                snapshot.add(new RagdollPosePayload.Part(part.region,
                        (float) part.position.x, (float) part.position.y, (float) part.position.z,
                        q.x, q.y, q.z, q.w,
                        (float) part.velocity.x, (float) part.velocity.y, (float) part.velocity.z));
            }
            if (!snapshot.isEmpty())
                ClientPlayNetworking.send(new RagdollPosePayload(entityId, ++poseSequence,
                        List.copyOf(snapshot)));
        }
    }

    public void tick(ClientLevel level, Entity collisionContext) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) { clear(); return; }
        electrifiedUntil.entrySet().removeIf(entry -> {
            if (entry.getValue() > traumaDecayTicker) return false;
            if (collisionContext.getId() != entry.getKey()) removeRagdoll(entry.getKey());
            return true;
        });
        tickWailing();
        remotePoseTicks.entrySet().removeIf(entry -> {
            if (traumaDecayTicker - entry.getValue() <= 60) return false;
            remoteDriven.remove(entry.getKey());
            remotePoseSequences.remove(entry.getKey());
            return true;
        });
        recentExplosions.removeIf(blast -> traumaDecayTicker - blast.createdTick > 6);
        synchronizePlayerAttachments(level);
        updateEnvironmentalEffects(level);
        if (++traumaDecayTicker % 20 == 0) {
            regionalTrauma.replaceAll((key, value) -> value * 0.94f);
            regionalTrauma.entrySet().removeIf(entry -> entry.getValue() < 0.035f);
        }
        Map<RigidBodyPiece, Vec3> incomingVelocities = tickIncomingVelocities;
        Set<RigidBodyPiece> collidedParts = tickCollidedParts;
        Set<Integer> supportedAnatomicalIslands = tickSupportedIslands;
        Map<Integer, Double> playerArmorLoads = tickArmorLoads;
        List<RigidBodyPiece> active = tickActivePieces;
        incomingVelocities.clear();
        collidedParts.clear();
        supportedAnatomicalIslands.clear();
        playerArmorLoads.clear();
        active.clear();
        for (RigidBodyPiece part : pieces) {
            if (part.playerBody && part.physicsBlend < 1.0f)
                part.physicsBlend = Math.min(1.0f, part.physicsBlend + 0.125f);
            part.previous = part.position;
            part.previousOrientation.set(part.orientation);
            incomingVelocities.put(part, part.velocity);
            if (isAnatomicalRegion(part.region) && part.region != 0 && part.supportTicks > 0)
                supportedAnatomicalIslands.add(part.entityId);
            if (part == grabbed || part.position.distanceToSqr(collisionContext.position()) <= 128.0 * 128.0)
                active.add(part);
            if (part.playerBody && !playerArmorLoads.containsKey(part.entityId)) {
                Entity owner = level.getEntity(part.entityId);
                if (owner instanceof Player player)
                    playerArmorLoads.put(part.entityId, playerArmorWeight(player));
            }
        }
        tickSolverIndex.clear();
        solverIndex = tickSolverIndex;
        for (RigidBodyPiece part : pieces) solverIndex.put(pieceKey(part.entityId, part.region), part);

        boolean hasPlayerRagdoll = false;
        for (RigidBodyPiece part : active) {
            if (part.playerBody) {
                hasPlayerRagdoll = true;
                break;
            }
        }
        final int configuredSubsteps = 4;
        final int globalSubsteps = hasPlayerRagdoll ? configuredSubsteps : Math.max(1, configuredSubsteps - 1);
        final double angularDamping = Math.sqrt(0.996);
        for (RigidBodyPiece part : active)
            part.jointImpulse = part.jointImpulse.scale(part.playerBody ? 0.08 : 0.16);
        for (int globalStep = 0; globalStep < globalSubsteps; globalStep++) {
            if (globalStep == 0) warmStartJoints(active);
            for (RigidBodyPiece part : active) {
                if (part.sleeping) {
                    if (!supportedAnatomicalIslands.contains(part.entityId)) {
                        wakeGroup(part.entityId);
                    } else if (!resolveEntityCollisions(level, part)) {
                        continue;
                    } else {
                        wakeGroup(part.entityId);
                    }
                }
                var fluid = level.getFluidState(BlockPos.containing(part.position));
                boolean inWater = fluid.is(FluidTags.WATER);
                boolean inLava = fluid.is(FluidTags.LAVA);
                double linearDamping = inWater ? (part.playerBody ? 0.91 : 0.865) : inLava ? 0.78
                        : Math.sqrt(Mth.clamp(RagdollRuntime.INSTANCE.config.ragdollAirRetention, 0.90f, 1.0f));
                double buoyancyShape = part.region == 1 ? 1.28 : part.region == 0 ? 1.10 : 0.78;
                double armorLoad = playerArmorLoads.getOrDefault(part.entityId, 0.0);
                double verticalAcceleration = inWater
                        ? (part.playerBody ? 0.0105 * buoyancyShape
                        * RagdollRuntime.INSTANCE.config.playerBuoyancy - armorLoad * 0.015 : 0.011)
                        : inLava ? 0.018 : -0.055;
                verticalAcceleration *= part.physicsBlend;
                part.velocity = part.velocity.scale(linearDamping)
                        .add(0, verticalAcceleration / globalSubsteps, 0);
                if (inWater) {
                    Vec3 flow = fluid.getFlow(level, BlockPos.containing(part.position));
                    Vec3 targetFlow = flow.scale(part.playerBody ? 0.31 : 0.38);
                    part.velocity = part.velocity.add(targetFlow.subtract(
                            new Vec3(part.velocity.x, 0, part.velocity.z)).scale(0.085 / globalSubsteps));
                }
                applyJointGravityTorque(part, verticalAcceleration, globalSubsteps);
                applyHeldItemLoad(part, globalSubsteps);
                if (!part.playerBody && !inWater && !inLava && part.velocity.y < -0.18) {
                    double speed = part.velocity.length();
                    Vec3 fallDirection = RagdollMath.safeNormalize(part.velocity, new Vec3(0, -1, 0));
                    Vec3 broadAxis = axes(part)[2];
                    double exposure = Math.min(1.0, part.airborneTicks / 45.0);
                    Vec3 aeroTorque = broadAxis.cross(fallDirection)
                            .scale((0.0025 + exposure * 0.0065) * Math.min(2.2, speed));
                    part.angularVelocity = part.angularVelocity.add(aeroTorque);
                }
                double terminalSpeed = part.playerBody ? 4.2 : 2.8;
                if (part.velocity.length() > terminalSpeed)
                    part.velocity = part.velocity.normalize().scale(terminalSpeed);
                Vec3 frameMotion = part.velocity.scale(1.0 / globalSubsteps);
                if (globalStep == 0 && part.playerBody && part.region == 1)
                    emitPredictiveBlockBreak(level, part);
                double smallestHalfExtent = Math.max(0.025,
                        Math.min(part.halfExtents.x, Math.min(part.halfExtents.y, part.halfExtents.z)));
                double safeStep = Math.min(0.12, smallestHalfExtent * 0.55);
                int ccdSteps = Math.min(isAttachmentRegion(part.region) ? 32 : 24, Math.max(1,
                        (int) Math.ceil(frameMotion.length() / safeStep)));
                boolean collided = false;
                for (int ccdStep = 0; ccdStep < ccdSteps; ccdStep++)
                    collided |= moveAgainstWorld(level, part, frameMotion.scale(1.0 / ccdSteps));
                collided |= resolveEntityCollisions(level, part);

                Quaternionf oldOrientation = new Quaternionf(part.orientation);
                Quaternionf delta = new Quaternionf().rotationXYZ(
                        (float) (part.angularVelocity.x / globalSubsteps),
                        (float) (part.angularVelocity.y / globalSubsteps),
                        (float) (part.angularVelocity.z / globalSubsteps));
                part.orientation.set(delta.mul(part.orientation)).normalize();
                if (!isWorldClear(level, part, part.position)) {
                    boolean rolledClear = part.playerBody && isAnatomicalRegion(part.region)
                            && resolveWorldPenetration(level, part)
                            && isWorldClear(level, part, part.position);
                    if (!rolledClear) {
                        part.orientation.set(oldOrientation);
                        part.angularVelocity = part.angularVelocity.scale(-0.18);
                    }
                    collided = true;
                } else part.angularVelocity = part.angularVelocity.scale(
                        inWater && part.playerBody ? 0.89 : angularDamping);
                if (collided) collidedParts.add(part);
            }

            int iterations = Math.max(2,
                    (RagdollRuntime.INSTANCE.config.ragdollConstraintIterations + globalSubsteps - 1)
                            / globalSubsteps);
            for (int iteration = 0; iteration < iterations; iteration++) solveJoints(level, active);
            solveSelfCollisions(level, active);
            for (RigidBodyPiece part : active) {
                if (!(part.playerBody && isClothRegion(part.region)))
                    resolveWorldPenetration(level, part);
                if (part.playerBody) {
                    double inheritedSpeed = incomingVelocities.getOrDefault(part, Vec3.ZERO).length();
                    double allowedSpeed = Math.max(0.12, inheritedSpeed + 0.075);
                    if (part.velocity.length() > allowedSpeed)
                        part.velocity = part.velocity.normalize().scale(allowedSpeed);
                }
                double terminalSpeed = part.playerBody ? 4.2 : 2.8;
                if (part.velocity.length() > terminalSpeed)
                    part.velocity = part.velocity.normalize().scale(terminalSpeed);
                double angularLimit = isAttachmentRegion(part.region) ? 0.16
                        : part.playerBody && isLimbRegion(part.region) ? 0.72
                        : part.playerBody ? 0.52 : isLimbRegion(part.region) ? 0.90 : 0.74;
                if (part.angularVelocity.length() > angularLimit)
                    part.angularVelocity = part.angularVelocity.normalize().scale(angularLimit);
            }
            for (int iteration = 0; iteration < 3; iteration++) enforceJointInvariant(level, active);
            for (RigidBodyPiece part : active)
                if (!(part.playerBody && isClothRegion(part.region)))
                    resolveWorldPenetration(level, part);
            enforceExactAnatomicalSockets(active);
            resolveAttachmentWorldCollisions(level, active);
            resolveAttachmentBodyCollisions(level, active);
            resolveIslandPenetration(level, active);
        }

        applyGroundedDrag(
                level, active, incomingVelocities);

        for (int i = pieces.size() - 1; i >= 0; i--) {
            RigidBodyPiece part = pieces.get(i);
            boolean collided = collidedParts.contains(part);
            boolean measuredSupport = hasGroundSupport(level, part)
                    || part.contacts.values().stream().anyMatch(contact -> contact.normal.y > 0.58
                    && part.age - contact.lastAge <= 2);
            boolean supported;
            if (measuredSupport) {
                part.supportMissTicks = 0;
                part.supportTicks = Math.min(1200, part.supportTicks + 1);
                supported = true;
                if (Math.abs(part.velocity.y) < 0.09)
                    part.velocity = new Vec3(part.velocity.x, 0, part.velocity.z);
                if (!part.playerBody && part.angularVelocity.lengthSqr() < 0.030)
                    part.angularVelocity = part.angularVelocity.scale(
                            isLimbRegion(part.region) ? 0.82 : 0.70);
                if (part.playerBody && (part.region == 4 || part.region == 5))
                    applyLoadedHipBuckling(part);
            } else if (part.supportTicks > 0 && part.supportMissTicks < 3) {
                part.supportMissTicks++;
                part.supportTicks = Math.max(1, part.supportTicks - 1);
                supported = true;
            } else {
                part.supportMissTicks = 0;
                part.supportTicks = 0;
                supported = false;
            }
            int completedAirborneTicks = part.airborneTicks;
            if (collided || supported) part.airborneTicks = 0;
            else if (!level.getFluidState(BlockPos.containing(part.position)).is(FluidTags.WATER))
                part.airborneTicks = Math.min(1200, part.airborneTicks + 1);
            else part.airborneTicks = 0;
            Vec3 incomingVelocity = incomingVelocities.getOrDefault(part, part.velocity);
            if (collided) emitRigidContactSound(level, part, incomingVelocity);
            if (collided && playerTumbles.contains(part.entityId)
                    && (part.region == 1 || part.region == 4 || part.region == 5)
                    && completedAirborneTicks > 8 && incomingVelocity.y < -0.72
                    && traumaDecayTicker - lastFallDamageTick > 12
                    && ClientPlayNetworking.canSend(RagdollFallDamagePayload.TYPE)) {
                RagdollConfig config = RagdollRuntime.INSTANCE.config;
                float fallDamage = (float) Mth.clamp(
                        (Math.abs(incomingVelocity.y) - config.ragdollImpactDamageThreshold)
                                * config.ragdollImpactDamageMultiplier,
                        0.5, config.ragdollImpactDamageMax);
                ClientPlayNetworking.send(new RagdollFallDamagePayload(fallDamage));
                lastFallDamageTick = traumaDecayTicker;
            }
            if (collided) part.bounces++;
            if (collided && incomingVelocity.lengthSqr() > 0.20) {
                float severity = (float) Mth.clamp((incomingVelocity.length() - 0.32) / 1.8, 0.08, 1.0);
                float armorProtection = armorImpactProtection(level, part);
                float bluntSeverity = severity * (1.0f - armorProtection * 0.32f);
                float bleedSeverity = severity * (1.0f - armorProtection * 0.82f);
                if (armorProtection > 0.05f && severity > 0.32f
                        && part.age - part.lastArmorImpactAge > 15
                        && ClientPlayNetworking.canSend(RagdollArmorImpactPayload.TYPE)) {
                    ClientPlayNetworking.send(new RagdollArmorImpactPayload(part.region,
                            (float) (0.5 * part.mass() * incomingVelocity.lengthSqr())));
                    part.lastArmorImpactAge = part.age;
                }
                part.contusion = Math.min(1.0f, part.contusion + bluntSeverity * 0.34f);
                if (part.age - part.lastBruiseAge >= 5)
                    addRigidBruise(part, RagdollMath.safeNormalize(incomingVelocity.scale(-1),
                            new Vec3(0, 1, 0)), part.position, bluntSeverity);
                if (RagdollRuntime.INSTANCE.config.rigidImpactBleeding && part.bloodReservoir > 0.01f
                        && bleedSeverity > 0.06f && part.age - part.lastImpactBleed > 5) {
                    Vec3 normal = RagdollMath.safeNormalize(incomingVelocity.scale(-1), new Vec3(0, 1, 0));
                    RagdollRuntime.INSTANCE.emitRigidImpact(part.position, normal, incomingVelocity, bleedSeverity,
                            Math.min(part.bloodReservoir, 0.020f + bleedSeverity * 0.050f), part.bloodRgb);
                    part.bloodReservoir -= 0.014f + bleedSeverity * 0.040f;
                    part.lastImpactBleed = part.age;
                }
            }
            Vec3 frameIncoming = incomingVelocities.getOrDefault(part, Vec3.ZERO);
            double rawEnergyDelta = 0.5 * part.mass()
                    * (part.velocity.lengthSqr() - frameIncoming.lengthSqr());
            part.lastEnergyDelta = part.lastEnergyDelta * 0.82 + rawEnergyDelta * 0.18;
            if (supported && Math.abs(part.lastEnergyDelta) < 0.000015
                    && part.velocity.lengthSqr() < 0.0016) part.lastEnergyDelta = 0.0;
            if (++part.age > 6000) {
                if (grabbed == part) grabbed = null;
                pieces.remove(i);
                solverIndex.remove(pieceKey(part.entityId, part.region));
            }
            part.bruises.removeIf(bruise -> part.age - bruise.createdAge() > 4800);
            part.contacts.values().removeIf(contact -> part.age - contact.lastAge > 2);
        }
        updateSleepingIslands();
        sendPoseSnapshots();
        solverIndex = null;
    }

    private void emitRigidContactSound(ClientLevel level, RigidBodyPiece part, Vec3 incoming) {
        RagdollConfig config = RagdollRuntime.INSTANCE.config;
        if (!config.rigidBodySounds || config.rigidBodySoundVolume <= 0.001f) return;
        double speed = incoming.length();
        double normalSpeed = Math.max(Math.abs(incoming.y), speed * 0.52);
        double threshold = isClothRegion(part.region) ? 0.24 : isGripRegion(part.region) ? 0.20 : 0.15;
        if (normalSpeed < threshold) return;
        int minimumGap = normalSpeed > 0.85 ? 2 : 4;
        if (traumaDecayTicker - rigidSoundAges.getOrDefault(part.entityId, -1000) < minimumGap) return;
        rigidSoundAges.put(part.entityId, traumaDecayTicker);

        float strength = (float) Mth.clamp((normalSpeed - threshold) / 1.25, 0.0, 1.0);
        float volume = config.rigidBodySoundVolume * (0.10f + strength * 0.48f);
        long phase = RagdollMath.mix(part.entityId * 65537L + part.region * 8191L + traumaDecayTicker);
        float variation = (float) ((RagdollMath.unit(phase) - 0.5) * 0.10);
        float massPitch = (float) Mth.clamp(1.12 - Math.sqrt(part.mass()) * 0.28, 0.72, 1.18);
        var sound = isClothRegion(part.region) ? SoundEvents.WOOL_HIT
                : armorImpactProtection(level, part) > 0.18f ? SoundEvents.IRON_GOLEM_DAMAGE
                : SoundEvents.PLAYER_SMALL_FALL;
        level.playLocalSound(part.position.x, part.position.y, part.position.z, sound,
                SoundSource.PLAYERS, volume, massPitch + variation, false);
    }

    private void updateEnvironmentalEffects(ClientLevel level) {
        Set<Integer> ignited = new HashSet<>();
        Set<Integer> submerged = new HashSet<>();
        for (RigidBodyPiece part : pieces) {
            BlockPos position = BlockPos.containing(part.position);
            if (level.getFluidState(position).is(FluidTags.WATER)) submerged.add(part.entityId);
            Entity owner = level.getEntity(part.entityId);
            if ((owner != null && owner.isOnFire())
                    || level.getFluidState(position).is(FluidTags.LAVA)
                    || level.getBlockState(position).is(BlockTags.FIRE)) ignited.add(part.entityId);
        }
        Set<Integer> cremated = new HashSet<>();
        for (RigidBodyPiece part : pieces) {
            if (submerged.contains(part.entityId)) {
                part.burningTicks = 0;
                part.ignitionGrace = 0;
                continue;
            }
            if (ignited.contains(part.entityId)) part.ignitionGrace = 45;
            else if (part.playerBody && part.ignitionGrace > 0) part.ignitionGrace--;
            boolean burning = ignited.contains(part.entityId) || part.ignitionGrace > 0
                    || (!part.playerBody && part.burningTicks > 0);
            if (!burning) continue;
            part.burningTicks++;
            float maximumChar = part.playerBody ? 0.38f : 1.0f;
            part.charAmount = Math.min(maximumChar, part.charAmount + (part.playerBody ? 0.0014f : 0.0042f));
            part.sleeping = false;
            if (part.region == 1 && (part.burningTicks == 1 || part.burningTicks % 34 == 0)) {
                long soundPhase = RagdollMath.mix(part.entityId * 811L + part.burningTicks * 31L);
                float pitch = 0.82f + (float) RagdollMath.unit(soundPhase) * 0.28f;
                level.playLocalSound(part.position.x, part.position.y, part.position.z,
                        part.burningTicks == 1 ? SoundEvents.FIRE_AMBIENT : SoundEvents.CAMPFIRE_CRACKLE,
                        SoundSource.PLAYERS, part.playerBody ? 0.34f : 0.48f, pitch, false);
            }
            if ((part.burningTicks + part.region * 3) % 5 == 0) {
                long phase = RagdollMath.mix(part.entityId * 4099L + part.region * 131L + part.burningTicks);
                double sx = (RagdollMath.unit(phase) - 0.5) * part.halfExtents.x * 1.6;
                double sz = (RagdollMath.unit(RagdollMath.mix(phase)) - 0.5) * part.halfExtents.z * 1.6;
                Vec3 flame = part.position.add(sx, part.halfExtents.y * 0.78, sz);
                level.addParticle(ParticleTypes.FLAME, flame.x, flame.y, flame.z, 0, 0.012, 0);
                level.addParticle(ParticleTypes.SMOKE, flame.x, flame.y + 0.025, flame.z, 0, 0.018, 0);
            }
            if (!part.playerBody && part.burningTicks > 105 && part.burningTicks % 8 == 0) {
                int shedding = 3 + Math.min(5, (part.burningTicks - 105) / 35);
                RagdollRuntime.INSTANCE.emitAshCloud(part.position, part.velocity, shedding);
                long phase = RagdollMath.mix(part.entityId * 4099L + part.region * 131L + part.burningTicks);
                double ashX = (RagdollMath.unit(phase) - 0.5) * part.halfExtents.x * 1.8;
                double ashZ = (RagdollMath.unit(RagdollMath.mix(phase + 9)) - 0.5)
                        * part.halfExtents.z * 1.8;
                level.addParticle(part.burningTicks > 205 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                        part.position.x + ashX, part.position.y + part.halfExtents.y * 0.45,
                        part.position.z + ashZ, ashX * 0.025, 0.025, ashZ * 0.025);
            }
            if (!part.playerBody && part.burningTicks >= 260) cremated.add(part.entityId);
        }
        for (int entityId : cremated) {
            Vec3 center = Vec3.ZERO;
            int bodyParts = 0;
            for (RigidBodyPiece part : List.copyOf(pieces)) if (part.entityId == entityId) {
                center = center.add(part.position);
                bodyParts++;
                RagdollRuntime.INSTANCE.emitAshCloud(part.position, part.velocity, 28);
                for (int i = 0; i < 5; i++) {
                    long phase = RagdollMath.mix(entityId * 92821L + part.region * 619L + i * 43L);
                    double vx = (RagdollMath.unit(phase) - 0.5) * 0.12;
                    double vz = (RagdollMath.unit(RagdollMath.mix(phase + 5)) - 0.5) * 0.12;
                    level.addParticle(i == 0 ? ParticleTypes.LARGE_SMOKE
                                    : i % 2 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                            part.position.x, part.position.y, part.position.z,
                            vx, 0.035 + RagdollMath.unit(phase) * 0.07, vz);
                }
            }
            if (bodyParts > 0) {
                center = center.scale(1.0 / bodyParts);
                level.playLocalSound(center.x, center.y, center.z, SoundEvents.SOUL_SAND_BREAK,
                        SoundSource.PLAYERS, 0.82f, 0.58f, false);
                level.playLocalSound(center.x, center.y, center.z, SoundEvents.FIRE_EXTINGUISH,
                        SoundSource.PLAYERS, 0.54f, 0.72f, false);
            }
            removeRagdoll(entityId);
        }
    }

    private static float armorImpactProtection(ClientLevel level, RigidBodyPiece part) {
        if (!isAnatomicalRegion(part.region)
                || !(level.getEntity(part.entityId) instanceof Player player)) return 0.0f;
        return (float) Mth.clamp(armorMassForRegion(player, part.region) * 4.2, 0.0, 0.78);
    }

    private void applyJointGravityTorque(RigidBodyPiece part, double verticalAcceleration,
                                         int substeps) {
        if (!part.anchoredJoint || part.parentRegion < 0 || part == grabbed) return;
        if (part.region == 0 && hasNonHeadSupport(part.entityId)) return;
        if (part.region != 0 && part.supportTicks > 0 && part.velocity.lengthSqr() < 0.035) return;
        RigidBodyPiece parent = find(part.entityId, part.parentRegion);
        if (parent == null) return;
        Vec3 socket = worldAnchor(parent, part.parentJointAnchor);
        Vec3 lever = part.position.subtract(socket);
        if (lever.lengthSqr() < 1.0e-8) return;
        Vec3 force = new Vec3(0, part.mass() * verticalAcceleration, 0);
        Vec3 angularDelta = part.inverseInertia(lever.cross(force)).scale(1.0 / substeps);
        double maximumStep = isAttachmentRegion(part.region) ? 0.055 : 0.18;
        if (angularDelta.length() > maximumStep)
            angularDelta = angularDelta.normalize().scale(maximumStep);
        if (angularDelta.lengthSqr() > 1.0e-12) {
            part.angularVelocity = part.angularVelocity.add(angularDelta);
            part.sleeping = false;
        }
    }

    private void applyHeldItemLoad(RigidBodyPiece grip, int substeps) {
        if (!isGripRegion(grip.region) || grip.parentRegion < 0) return;
        RigidBodyPiece arm = find(grip.entityId, grip.parentRegion);
        if (arm == null || arm.sleeping) return;
        Vec3 shoulder = worldAnchor(arm, arm.childJointAnchor);
        Vec3 hand = grip.position;
        Vec3 force = new Vec3(0, -0.055 * grip.mass(), 0);
        Vec3 angularDelta = arm.inverseInertia(hand.subtract(shoulder).cross(force))
                .scale(0.42 / Math.max(1, substeps));
        if (angularDelta.length() > 0.028)
            angularDelta = angularDelta.normalize().scale(0.028);
        arm.angularVelocity = arm.angularVelocity.add(angularDelta);
        arm.velocity = arm.velocity.add(0,
                -0.055 * grip.mass() / Math.max(0.05, arm.mass()) * 0.045 / substeps, 0);
    }

    private boolean moveAgainstWorld(ClientLevel level, RigidBodyPiece part, Vec3 delta) {
        boolean collided = false;
        Vec3 position = part.position;
        Vec3 candidate = position.add(0, delta.y, 0);
        if (isWorldClear(level, part, candidate)) position = candidate;
        else {
            position = furthestClear(level, part, position, candidate);
            Vec3 fallback = new Vec3(0, delta.y < 0 ? 1 : -1, 0);
            Vec3 normal = worldContactNormal(level, part, candidate, fallback);
            emitBlockImpact(level, part, candidate, normal);
            applyCollisionTorque(part, normal, part.velocity);
            RigidBodyPiece.PersistentContact manifold = recordWorldContact(part, candidate, normal);
            part.velocity = applyWorldCollisionResponse(part,
                    collideWithSurface(level, part, candidate, normal, manifold), normal);
            collided = true;
        }
        candidate = position.add(delta.x, 0, 0);
        if (isWorldClear(level, part, candidate)) position = candidate;
        else {
            position = furthestClear(level, part, position, candidate);
            Vec3 fallback = new Vec3(delta.x < 0 ? 1 : -1, 0, 0);
            Vec3 normal = worldContactNormal(level, part, candidate, fallback);
            emitBlockImpact(level, part, candidate, normal);
            applyCollisionTorque(part, normal, part.velocity);
            RigidBodyPiece.PersistentContact manifold = recordWorldContact(part, candidate, normal);
            part.velocity = applyWorldCollisionResponse(part,
                    collideWithSurface(level, part, candidate, normal, manifold), normal);
            collided = true;
        }
        candidate = position.add(0, 0, delta.z);
        if (isWorldClear(level, part, candidate)) position = candidate;
        else {
            position = furthestClear(level, part, position, candidate);
            Vec3 fallback = new Vec3(0, 0, delta.z < 0 ? 1 : -1);
            Vec3 normal = worldContactNormal(level, part, candidate, fallback);
            emitBlockImpact(level, part, candidate, normal);
            applyCollisionTorque(part, normal, part.velocity);
            RigidBodyPiece.PersistentContact manifold = recordWorldContact(part, candidate, normal);
            part.velocity = applyWorldCollisionResponse(part,
                    collideWithSurface(level, part, candidate, normal, manifold), normal);
            collided = true;
        }
        part.position = position;
        return collided;
    }

    private Vec3 applyWorldCollisionResponse(RigidBodyPiece contacted, Vec3 response, Vec3 normal) {
        if (!contacted.playerBody || isAttachmentRegion(contacted.region)
                || !ragdolled.contains(contacted.entityId)) return response;
        double impactSpeed = Math.max(0.0, -contacted.velocity.dot(normal));
        if (normal.y > 0.65 && impactSpeed < 0.28) {
            double normalSpeed = response.dot(normal);
            return normalSpeed > 0.0 ? response.subtract(normal.scale(normalSpeed)) : response;
        }
        Vec3 change = response.subtract(contacted.velocity);
        double islandMass = 0.0;
        for (RigidBodyPiece piece : pieces)
            if (piece.entityId == contacted.entityId) islandMass += piece.mass();
        if (islandMass <= 1.0e-8) return response;
        Vec3 shared = change.scale(contacted.mass() / islandMass * 0.30);
        for (RigidBodyPiece piece : pieces)
            if (piece.entityId == contacted.entityId && piece != contacted) {
                piece.velocity = piece.velocity.add(shared);
                piece.sleeping = false;
            }
        return response;
    }

    private static Vec3 furthestClear(ClientLevel level, RigidBodyPiece part, Vec3 clear, Vec3 blocked) {
        Vec3 low = clear;
        Vec3 high = blocked;
        for (int iteration = 0; iteration < 10; iteration++) {
            Vec3 middle = low.lerp(high, 0.5);
            if (isWorldClear(level, part, middle)) low = middle;
            else high = middle;
        }
        return low;
    }

    private static Vec3 collideWithSurface(ClientLevel level, RigidBodyPiece part,
                                           Vec3 candidate, Vec3 normal,
                                           RigidBodyPiece.PersistentContact manifold) {
        BlockPos contact = contactBlock(part, candidate, normal);
        var block = level.getBlockState(contact).getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        SurfacePhysics surface = surfacePhysics(path, block.getFriction());
        double impactSpeed = Math.max(0.0, -part.velocity.dot(normal));
        if (impactSpeed <= 1.0e-6) return part.velocity;
        double bounceActivation = Mth.clamp((impactSpeed - 0.09) / 0.70, 0.0, 1.0);
        double surfaceRestitution = surface.restitution;
        RagdollConfig config = RagdollRuntime.INSTANCE.config;
        double restitution = manifold.stableSteps >= 2 ? 0.0
                : surfaceRestitution * config.ragdollRestitution * bounceActivation;
        double frictionScale = Mth.clamp(config.ragdollGroundFriction, 0.0f, 1.0f);
        double friction = surface.friction * frictionScale
                * (manifold.stableSteps >= 4 ? 0.42 : 0.16);
        Vec3 response = RagdollMath.reflect(part.velocity, normal, restitution, friction);
        if (Math.abs(part.velocity.dot(normal)) < 0.035)
            response = response.subtract(normal.scale(response.dot(normal)));
        return response;
    }

    private static RigidBodyPiece.PersistentContact recordWorldContact(
            RigidBodyPiece part, Vec3 point, Vec3 normal) {
        long key = contactBlock(part, point, normal).asLong();
        RigidBodyPiece.PersistentContact contact = part.contacts.get(key);
        if (contact == null) {
            if (part.contacts.size() >= 4)
                part.contacts.remove(part.contacts.keySet().iterator().next());
            contact = new RigidBodyPiece.PersistentContact(point, normal, part.age);
            part.contacts.put(key, contact);
        } else {
            contact.point = contact.point.lerp(point, 0.35);
            contact.normal = RagdollMath.safeNormalize(contact.normal.lerp(normal, 0.35), normal);
            contact.stableSteps = Math.min(240, contact.stableSteps + 1);
            contact.lastAge = part.age;
        }
        return contact;
    }

    private static SurfacePhysics surfacePhysics(String path, float blockFriction) {
        if (path.contains("blue_ice")) return new SurfacePhysics(0.045, 0.012, 0.76);
        if (path.contains("packed_ice")) return new SurfacePhysics(0.05, 0.020, 0.73);
        if (path.contains("frosted_ice") || path.equals("ice"))
            return new SurfacePhysics(0.06, 0.032, 0.70);
        if (path.contains("slime")) return new SurfacePhysics(0.76, 0.20, 0.45);
        if (path.contains("honey")) return new SurfacePhysics(0.005, 0.90, 0.32);
        if (path.contains("bed")) return new SurfacePhysics(0.34, 0.42, 0.36);
        if (path.contains("wool") || path.contains("carpet"))
            return new SurfacePhysics(0.04, 0.55, 0.40);
        if (path.contains("soul_sand")) return new SurfacePhysics(0.005, 0.72, 0.66);
        if (path.contains("mud")) return new SurfacePhysics(0.01, 0.62, 0.68);
        if (path.contains("sand") || path.contains("gravel"))
            return new SurfacePhysics(0.035, 0.58, 0.72);
        if (path.contains("snow")) return new SurfacePhysics(0.018, 0.48, 0.42);
        if (path.contains("glass")) return new SurfacePhysics(0.18, 0.28, 1.18);
        if (path.contains("leaves")) return new SurfacePhysics(0.015, 0.40, 0.48);
        if (path.contains("wood") || path.contains("planks") || path.contains("log"))
            return new SurfacePhysics(0.09, 0.46, 0.82);
        if (path.contains("iron") || path.contains("copper") || path.contains("gold")
                || path.contains("metal")) return new SurfacePhysics(0.11, 0.32, 1.28);
        if (path.contains("stone") || path.contains("brick") || path.contains("concrete"))
            return new SurfacePhysics(0.065, 0.42, 1.22);
        if (path.contains("dirt") || path.contains("grass") || path.contains("moss"))
            return new SurfacePhysics(0.035, 0.55, 0.76);
        double nativeTraction = Mth.clamp((1.0 - blockFriction) * 1.05, 0.025, 0.75);
        return new SurfacePhysics(0.055, nativeTraction, 1.0);
    }

    private record SurfacePhysics(double restitution, double friction, double trauma) { }

    private static Vec3 worldContactNormal(ClientLevel level, RigidBodyPiece part,
                                           Vec3 center, Vec3 fallback) {
        AABB broad = boundsAt(part, center).inflate(0.001);
        ObbContact shallowest = null;
        for (var shape : level.getBlockCollisions(null, broad))
            for (AABB box : shape.toAabbs()) {
                ObbContact contact = obbContact(part, center, box);
                if (contact != null && (shallowest == null || contact.depth < shallowest.depth))
                    shallowest = contact;
            }
        Vec3 normal = shallowest == null ? fallback : shallowest.normal.scale(-1);
        return normal.lengthSqr() < 1.0e-10 ? fallback : normal.normalize();
    }

    private static BlockPos contactBlock(RigidBodyPiece part, Vec3 candidate, Vec3 normal) {
        Vec3[] bodyAxes = axes(part);
        Vec3 collisionHalf = collisionHalfExtents(part);
        Vec3 towardBlock = normal.scale(-1);
        Vec3 support = bodyAxes[0].scale(Math.copySign(collisionHalf.x, bodyAxes[0].dot(towardBlock)))
                .add(bodyAxes[1].scale(Math.copySign(collisionHalf.y, bodyAxes[1].dot(towardBlock))))
                .add(bodyAxes[2].scale(Math.copySign(collisionHalf.z, bodyAxes[2].dot(towardBlock))));
        return BlockPos.containing(candidate.add(support).add(towardBlock.scale(0.035)));
    }

    private void emitBlockImpact(ClientLevel level, RigidBodyPiece part, Vec3 candidate, Vec3 normal) {
        double normalSpeed = Math.abs(part.velocity.dot(normal));
        float energy = (float) (0.5 * part.mass() * normalSpeed * normalSpeed);
        if (part.playerBody && ragdolled.contains(part.entityId)) {
            double islandEnergy = 0.0;
            for (RigidBodyPiece piece : pieces) if (piece.entityId == part.entityId) {
                double speedIntoSurface = Math.max(0.0, -piece.velocity.dot(normal));
                islandEnergy += 0.5 * piece.mass() * speedIntoSurface * speedIntoSurface;
            }
            energy = (float) Math.max(energy, islandEnergy);
        }
        if (energy < 0.025f || !ClientPlayNetworking.canSend(RagdollBlockImpactPayload.TYPE)) return;
        BlockPos block = contactBlock(part, candidate, normal);
        if (level.getBlockState(block).isAir()) return;
        long key = block.asLong();
        int previousAge = blockImpactAges.getOrDefault(key, -1000);
        if (traumaDecayTicker - previousAge < 8) return;
        blockImpactAges.put(key, traumaDecayTicker);
        Vec3 travelDirection = RagdollMath.safeNormalize(part.velocity, normal.scale(-1));
        ClientPlayNetworking.send(new RagdollBlockImpactPayload(
                block.getX(), block.getY(), block.getZ(), Math.min(12.0f, energy),
                (float) travelDirection.x, (float) travelDirection.y, (float) travelDirection.z));
    }

    private void emitPredictiveBlockBreak(ClientLevel level, RigidBodyPiece torso) {
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return;
        double speed = torso.velocity.length();
        if (speed < 0.32 || !ClientPlayNetworking.canSend(RagdollBlockImpactPayload.TYPE)) return;
        Vec3 direction = torso.velocity.scale(1.0 / speed);
        double energy = 0.0;
        for (RigidBodyPiece piece : pieces) if (piece.entityId == torso.entityId) {
            double directionalSpeed = Math.max(0.0, piece.velocity.dot(direction));
            energy += 0.5 * piece.mass() * directionalSpeed * directionalSpeed;
        }
        if (energy < 0.04) return;
        double lookAhead = Math.min(7.0, torso.radius() + 0.85 + speed * 2.4);
        Vec3 start = torso.position.add(direction.scale(torso.radius() * 0.35));
        Vec3 end = start.add(direction.scale(lookAhead));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, localPlayer));
        if (hit.getType() == HitResult.Type.MISS) return;
        BlockPos block = hit.getBlockPos();
        long key = block.asLong();
        if (traumaDecayTicker - blockImpactAges.getOrDefault(key, -1000) < 5) return;
        blockImpactAges.put(key, traumaDecayTicker);
        ClientPlayNetworking.send(new RagdollBlockImpactPayload(block.getX(), block.getY(), block.getZ(),
                (float) Math.min(12.0, energy), (float) direction.x, (float) direction.y, (float) direction.z));
    }

    private static void applyCollisionTorque(RigidBodyPiece part, Vec3 normal, Vec3 incomingVelocity) {
        double normalSpeed = -incomingVelocity.dot(normal);
        double groundTorqueThreshold = part.playerBody ? 0.24 : 0.105;
        double torqueThreshold = normal.y > 0.65 ? groundTorqueThreshold
                : part.playerBody ? 0.10 : 0.035;
        if (normalSpeed < torqueThreshold) return;
        Vec3 tangentVelocity = incomingVelocity.subtract(normal.scale(incomingVelocity.dot(normal)));
        float bruiseSeverity = (float) Mth.clamp(
                (normalSpeed + tangentVelocity.length() * 0.32 - 0.13) / 1.35, 0.0, 1.0);
        if (bruiseSeverity > 0.025f && part.age - part.lastBruiseAge >= 5)
            addRigidBruise(part, normal.scale(-1), null, Math.max(0.10f, bruiseSeverity));
        Vec3 towardContact = normal.scale(-1);
        Vec3[] bodyAxes = axes(part);
        Vec3 lever = bodyAxes[0].scale(Math.copySign(part.halfExtents.x,
                        bodyAxes[0].dot(towardContact)))
                .add(bodyAxes[1].scale(Math.copySign(part.halfExtents.y,
                        bodyAxes[1].dot(towardContact))))
                .add(bodyAxes[2].scale(Math.copySign(part.halfExtents.z,
                        bodyAxes[2].dot(towardContact))));
        Vec3 worldImpulse = normal.scale(normalSpeed * part.mass());
        Vec3 worldTorque = lever.cross(worldImpulse);
        Vector3f localTorqueVector = new Vector3f((float) worldTorque.x,
                (float) worldTorque.y, (float) worldTorque.z);
        new Quaternionf(part.orientation).conjugate().transform(localTorqueVector);
        double mass = part.mass();
        double inertiaX = Math.max(0.002, mass / 3.0
                * (part.halfExtents.y * part.halfExtents.y + part.halfExtents.z * part.halfExtents.z));
        double inertiaY = Math.max(0.002, mass / 3.0
                * (part.halfExtents.x * part.halfExtents.x + part.halfExtents.z * part.halfExtents.z));
        double inertiaZ = Math.max(0.002, mass / 3.0
                * (part.halfExtents.x * part.halfExtents.x + part.halfExtents.y * part.halfExtents.y));
        Vector3f localAngularDelta = new Vector3f((float) (localTorqueVector.x / inertiaX * 0.17),
                (float) (localTorqueVector.y / inertiaY * 0.17),
                (float) (localTorqueVector.z / inertiaZ * 0.17));
        part.orientation.transform(localAngularDelta);
        part.angularVelocity = part.angularVelocity.add(
                localAngularDelta.x, localAngularDelta.y, localAngularDelta.z);
        double maximumSpin = isAttachmentRegion(part.region) ? 0.16
                : part.playerBody && isLimbRegion(part.region) ? 0.72
                : part.playerBody ? 0.52 : 0.68;
        if (part.angularVelocity.length() > maximumSpin)
            part.angularVelocity = part.angularVelocity.normalize().scale(maximumSpin);
        part.sleeping = false;
    }

    private void applyLoadedHipBuckling(RigidBodyPiece leg) {
        if (leg.supportTicks > 14 || leg.angularVelocity.lengthSqr() > 0.0225) return;
        RigidBodyPiece torso = find(leg.entityId, 1);
        if (torso == null || Math.abs(axes(torso)[1].y) < 0.72
                || Math.abs(axes(leg)[1].y) < 0.68) return;
        Vec3 socket = worldAnchor(torso, leg.parentJointAnchor);
        Vec3 lever = leg.position.subtract(socket);
        Vec3 lateral = leg.position.subtract(torso.position).multiply(1, 0, 1);
        Vec3 outward = RagdollMath.safeNormalize(lateral,
                axes(torso)[0].scale(leg.region == 4 ? -1 : 1));
        Vec3 rotationAxis = lever.cross(outward);
        if (rotationAxis.lengthSqr() < 1.0e-9) return;
        double load = 0.0035 + Math.min(0.0035, leg.supportTicks * 0.00022);
        leg.angularVelocity = leg.angularVelocity.add(rotationAxis.normalize().scale(load));
        leg.sleeping = false;
        torso.sleeping = false;
    }

    private static void addRigidBruise(RigidBodyPiece part, Vec3 worldNormal, Vec3 worldPoint, float severity) {
        Vec3 normal = RagdollMath.safeNormalize(worldNormal, new Vec3(0, 1, 0));
        Vector3f localNormalVector = new Vector3f((float) normal.x, (float) normal.y, (float) normal.z);
        new Quaternionf(part.orientation).conjugate().transform(localNormalVector);
        Vec3 localNormal = RagdollMath.safeNormalize(new Vec3(localNormalVector.x, localNormalVector.y,
                localNormalVector.z), new Vec3(0, 1, 0));
        Vec3 local;
        if (worldPoint != null) local = localPoint(part, worldPoint);
        else {
            long phase = RagdollMath.mix(part.entityId * 65537L + part.region * 8191L + part.age * 17L);
            local = new Vec3((RagdollMath.unit(phase) - 0.5) * part.halfExtents.x * 1.12,
                    (RagdollMath.unit(RagdollMath.mix(phase + 3)) - 0.5) * part.halfExtents.y * 1.12,
                    (RagdollMath.unit(RagdollMath.mix(phase + 7)) - 0.5) * part.halfExtents.z * 1.12);
        }
        double ax = Math.abs(localNormal.x), ay = Math.abs(localNormal.y), az = Math.abs(localNormal.z);
        if (ax >= ay && ax >= az) local = new Vec3(Math.copySign(part.halfExtents.x, localNormal.x),
                Mth.clamp(local.y, -part.halfExtents.y * 0.82, part.halfExtents.y * 0.82),
                Mth.clamp(local.z, -part.halfExtents.z * 0.82, part.halfExtents.z * 0.82));
        else if (ay >= az) local = new Vec3(Mth.clamp(local.x, -part.halfExtents.x * 0.82, part.halfExtents.x * 0.82),
                Math.copySign(part.halfExtents.y, localNormal.y),
                Mth.clamp(local.z, -part.halfExtents.z * 0.82, part.halfExtents.z * 0.82));
        else local = new Vec3(Mth.clamp(local.x, -part.halfExtents.x * 0.82, part.halfExtents.x * 0.82),
                Mth.clamp(local.y, -part.halfExtents.y * 0.82, part.halfExtents.y * 0.82),
                Math.copySign(part.halfExtents.z, localNormal.z));
        int seed = (int) RagdollMath.mix(part.entityId * 92821L + part.region * 68917L + part.age * 101L);
        part.bruises.add(new RigidBruise(local, localNormal, severity, seed, part.age));
        while (part.bruises.size() > 12) part.bruises.removeFirst();
        part.contusion = Math.min(1.0f, part.contusion + severity * 0.22f);
        part.lastBruiseAge = part.age;
    }

    private static boolean resolveWorldPenetration(ClientLevel level, RigidBodyPiece part) {
        if (isWorldClear(level, part, part.position)) return false;
        boolean corrected = false;
        boolean meaningfulCorrection = false;
        int maximumIterations = part.playerBody ? 4 : 8;
        for (int iteration = 0; iteration < maximumIterations; iteration++) {
            AABB broad = boundsAt(part, part.position).inflate(0.002);
            ObbContact deepest = null;
            for (var shape : level.getBlockCollisions(null, broad))
                for (AABB box : shape.toAabbs()) {
                    ObbContact contact = obbContact(part, part.position, box);
                    if (contact != null && (deepest == null || contact.depth > deepest.depth))
                        deepest = contact;
                }
            if (deepest == null) break;
            Vec3 outward = deepest.normal.scale(-1);
            Vec3 correction = outward.scale(Math.min(part.playerBody ? 0.045 : 0.16,
                    deepest.depth + 0.0007));
            meaningfulCorrection |= correction.lengthSqr() > 0.004 * 0.004;
            part.position = part.position.add(correction);
            part.previous = part.previous.add(correction);
            double inwardSpeed = part.velocity.dot(outward);
            if (inwardSpeed < 0) {
                part.velocity = part.velocity.subtract(outward.scale(inwardSpeed));
                meaningfulCorrection |= inwardSpeed < -0.04;
            }
            if (!part.playerBody && outward.y > 0.55 && part.angularVelocity.lengthSqr() < 0.05)
                part.angularVelocity = part.angularVelocity.scale(0.62);
            corrected = true;
        }
        if (!part.playerBody && !isWorldClear(level, part, part.position)) {
            Vec3 origin = part.position;
            for (int step = 1; step <= 48; step++) {
                Vec3 candidate = origin.add(0, step * 0.025, 0);
                if (!isWorldClear(level, part, candidate)) continue;
                Vec3 correction = candidate.subtract(part.position);
                part.position = candidate;
                part.previous = part.previous.add(correction);
                part.velocity = new Vec3(part.velocity.x, 0, part.velocity.z);
                corrected = true;
                meaningfulCorrection = true;
                break;
            }
        }
        if (meaningfulCorrection) part.sleeping = false;
        return corrected;
    }

    private void applyGroundedDrag(
            ClientLevel level, List<RigidBodyPiece> active,
            Map<RigidBodyPiece, Vec3> incomingVelocities) {
        Set<Integer> ids = tickGroundedPlayerIslands;
        ids.clear();
        for (RigidBodyPiece part : active)
            if (part.playerBody && part.region != 0 && isAnatomicalRegion(part.region)
                    && part.supportTicks > 0) ids.add(part.entityId);
        for (int entityId : ids) {
            double mass = 0.0;
            Vec3 common = Vec3.ZERO;
            double maximumIncoming = 0.0;
            double maximumAngular = 0.0;
            double supportedTraction = 0.0;
            for (RigidBodyPiece part : active) {
                if (part.entityId != entityId || !isAnatomicalRegion(part.region)) continue;
                mass += part.mass();
                common = common.add(part.velocity.scale(part.mass()));
                maximumIncoming = Math.max(maximumIncoming,
                        incomingVelocities.getOrDefault(part, Vec3.ZERO).length());
                maximumAngular = Math.max(maximumAngular, part.angularVelocity.length());
                supportedTraction = Math.max(supportedTraction, supportedTraction(level, part));
            }
            if (mass <= 1.0e-8) continue;
            common = common.scale(1.0 / mass);
            boolean settledContact = maximumIncoming <= 0.34;
            double removeY = settledContact && Math.abs(common.y) < 0.16 ? common.y : 0.0;
            Vec3 horizontal = new Vec3(common.x, 0, common.z);
            double frictionScale = Mth.clamp(RagdollRuntime.INSTANCE.config.ragdollGroundFriction,
                    0.0f, 1.0f);
            double horizontalRetention = settledContact
                    ? Mth.clamp(0.985 - supportedTraction * frictionScale * 0.24, 0.74, 0.982)
                    : 0.992;
            double groundedDriftDeadZone = settledContact ? 0.026 : 0.008;
            Vec3 removeHorizontal = horizontal.lengthSqr()
                    < groundedDriftDeadZone * groundedDriftDeadZone
                    ? horizontal : horizontal.scale(1.0 - horizontalRetention);
            Vec3 commonNoise = removeHorizontal.add(0, removeY, 0);
            for (RigidBodyPiece part : active) {
                if (part.entityId != entityId) continue;
                part.velocity = part.velocity.subtract(commonNoise);
                if (settledContact && maximumAngular < 0.20) part.angularVelocity = part.angularVelocity.scale(
                        isAttachmentRegion(part.region) ? 0.88 : 0.72);
                if (settledContact && part.velocity.lengthSqr() < 0.005 * 0.005)
                    part.velocity = Vec3.ZERO;
                if (settledContact && part.angularVelocity.lengthSqr() < 0.006 * 0.006)
                    part.angularVelocity = Vec3.ZERO;
                if (Math.abs(part.velocity.y) < 0.025 && part.supportTicks > 0)
                    part.velocity = new Vec3(part.velocity.x, 0, part.velocity.z);
            }
        }
    }

    private static double supportedTraction(ClientLevel level, RigidBodyPiece part) {
        double traction = 0.0;
        for (RigidBodyPiece.PersistentContact contact : part.contacts.values()) {
            if (contact.normal.y < 0.55 || part.age - contact.lastAge > 1) continue;
            BlockPos position = contactBlock(part, contact.point, contact.normal);
            var block = level.getBlockState(position).getBlock();
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            traction = Math.max(traction, surfacePhysics(path, block.getFriction()).friction);
        }
        return traction;
    }

    private void resolveIslandPenetration(ClientLevel level,
                                                          List<RigidBodyPiece> active) {
        Set<Integer> anatomicalIslands = tickAnatomicalIslands;
        anatomicalIslands.clear();
        for (RigidBodyPiece part : active)
            if (isAnatomicalRegion(part.region)) anatomicalIslands.add(part.entityId);
        for (int entityId : anatomicalIslands) {
            for (int iteration = 0; iteration < 3; iteration++) {
                ObbContact deepest = null;
                for (RigidBodyPiece part : active) {
                    if (part.entityId != entityId || !isAnatomicalRegion(part.region)) continue;
                    AABB broad = boundsAt(part, part.position).inflate(0.0015);
                    for (var shape : level.getBlockCollisions(null, broad))
                        for (AABB block : shape.toAabbs()) {
                            ObbContact contact = obbContact(part, part.position, block);
                            if (contact != null && contact.depth > 0.0025
                                    && (deepest == null || contact.depth > deepest.depth))
                                deepest = contact;
                        }
                }
                if (deepest == null) break;
                Vec3 outward = deepest.normal.scale(-1);
                Vec3 correction = outward.scale(Math.min(0.045,
                        Math.max(0.0, deepest.depth - 0.0010)));
                for (RigidBodyPiece part : active) {
                    if (part.entityId != entityId) continue;
                    part.position = part.position.add(correction);
                    part.previous = part.previous.add(correction);
                    double inward = part.velocity.dot(outward);
                    if (inward < 0.0)
                        part.velocity = part.velocity.subtract(outward.scale(inward));
                }
            }
        }
    }

    private void resolveAttachmentWorldCollisions(ClientLevel level,
                                                          List<RigidBodyPiece> active) {
        for (RigidBodyPiece attachment : active) {
            if (!attachment.playerBody || !isClothRegion(attachment.region)
                    || !attachment.anchoredJoint || attachment.parentRegion < 0) continue;
            RigidBodyPiece parent = find(attachment.entityId, attachment.parentRegion);
            if (parent == null) continue;
            Vec3 socket = worldAnchor(parent, attachment.parentJointAnchor);
            for (int iteration = 0; iteration < 2; iteration++) {
                ObbContact deepest = deepestWorldContact(level, attachment);
                if (deepest == null || deepest.depth <= 0.0007) break;
                Vec3 outward = deepest.normal.scale(-1);
                Vec3 lever = attachment.position.subtract(socket);
                Vec3 rotationAxis = lever.cross(outward);
                if (rotationAxis.lengthSqr() < 1.0e-9) {
                    attachment.angularVelocity = attachment.angularVelocity.scale(0.45);
                    break;
                }
                Vector3f axis = new Vector3f((float) rotationAxis.x,
                        (float) rotationAxis.y, (float) rotationAxis.z).normalize();
                double angle = Mth.clamp(deepest.depth / Math.max(0.06, lever.length()) * 0.48,
                        0.006, 0.055);
                Quaternionf oldOrientation = new Quaternionf(attachment.orientation);
                Vec3 oldPosition = attachment.position;
                attachment.orientation.set(new Quaternionf().fromAxisAngleRad(axis, (float) angle)
                        .mul(attachment.orientation)).normalize();
                Vector3f rotatedAnchor = attachment.orientation.transform(new Vector3f(
                        (float) attachment.childJointAnchor.x,
                        (float) attachment.childJointAnchor.y,
                        (float) attachment.childJointAnchor.z));
                attachment.position = socket.subtract(new Vec3(
                        rotatedAnchor.x, rotatedAnchor.y, rotatedAnchor.z));
                ObbContact after = deepestWorldContact(level, attachment);
                if (after != null && after.depth >= deepest.depth - 0.0002) {
                    attachment.orientation.set(oldOrientation);
                    attachment.position = oldPosition;
                    attachment.angularVelocity = attachment.angularVelocity.scale(0.38);
                    break;
                }
                attachment.angularVelocity = attachment.angularVelocity.scale(0.78);
            }
            Vec3 lever = attachment.position.subtract(socket);
            attachment.velocity = parent.velocityAt(socket)
                    .add(attachment.angularVelocity.cross(lever));
        }
    }

    private void resolveAttachmentBodyCollisions(ClientLevel level,
                                                          List<RigidBodyPiece> active) {
        for (RigidBodyPiece attachment : active) {
            if (!attachment.playerBody || !isClothRegion(attachment.region)
                    || !attachment.anchoredJoint || attachment.parentRegion < 0) continue;
            if (attachment.region == 7 || attachment.region == 8
                    || attachment.region == 14 || attachment.region == 15) continue;
            RigidBodyPiece parent = find(attachment.entityId, attachment.parentRegion);
            if (parent == null) continue;
            Vec3 socket = worldAnchor(parent, attachment.parentJointAnchor);
            Vec3 lastOutward = null;
            for (int iteration = 0; iteration < 2; iteration++) {
                AttachmentContact contact = deepestAttachmentOwnerContact(attachment);
                if (contact == null || contact.depth <= 0.0008) break;
                Vec3 lever = attachment.position.subtract(socket);
                Vec3 rotationAxis = lever.cross(contact.outward);
                if (rotationAxis.lengthSqr() < 1.0e-9) break;
                Quaternionf oldOrientation = new Quaternionf(attachment.orientation);
                Vec3 oldPosition = attachment.position;
                ObbContact worldBefore = deepestWorldContact(level, attachment);
                Vector3f axis = new Vector3f((float) rotationAxis.x,
                        (float) rotationAxis.y, (float) rotationAxis.z).normalize();
                double angle = Mth.clamp(contact.depth / Math.max(0.08, lever.length()) * 0.42,
                        0.005, 0.050);
                attachment.orientation.set(new Quaternionf().fromAxisAngleRad(axis, (float) angle)
                        .mul(attachment.orientation)).normalize();
                Vector3f rotatedAnchor = attachment.orientation.transform(new Vector3f(
                        (float) attachment.childJointAnchor.x,
                        (float) attachment.childJointAnchor.y,
                        (float) attachment.childJointAnchor.z));
                attachment.position = socket.subtract(new Vec3(
                        rotatedAnchor.x, rotatedAnchor.y, rotatedAnchor.z));
                AttachmentContact after = deepestAttachmentOwnerContact(attachment);
                ObbContact worldAfter = deepestWorldContact(level, attachment);
                double afterDepth = after == null ? 0.0 : after.depth;
                double oldWorldDepth = worldBefore == null ? 0.0 : worldBefore.depth;
                double newWorldDepth = worldAfter == null ? 0.0 : worldAfter.depth;
                if (afterDepth >= contact.depth - 0.0002 || newWorldDepth > oldWorldDepth + 0.0010) {
                    attachment.orientation.set(oldOrientation);
                    attachment.position = oldPosition;
                    break;
                }
                lastOutward = contact.outward;
            }
            Vec3 lever = attachment.position.subtract(socket);
            if (lastOutward != null && lever.lengthSqr() > 1.0e-8) {
                Vec3 rotationalVelocity = attachment.angularVelocity.cross(lever);
                double inward = rotationalVelocity.dot(lastOutward);
                if (inward < 0.0) {
                    Vec3 desiredChange = lastOutward.scale(-inward);
                    attachment.angularVelocity = attachment.angularVelocity.add(
                            lever.cross(desiredChange).scale(1.0 / lever.lengthSqr()));
                }
            }
            if (attachment.angularVelocity.length() > 0.16)
                attachment.angularVelocity = attachment.angularVelocity.normalize().scale(0.16);
            attachment.velocity = parent.velocityAt(socket)
                    .add(attachment.angularVelocity.cross(lever));
        }
    }

    private AttachmentContact deepestAttachmentOwnerContact(RigidBodyPiece attachment) {
        AttachmentContact deepest = null;
        for (RigidBodyPiece body : pieces) {
            if (body.entityId != attachment.entityId || !isAnatomicalRegion(body.region)) continue;
            ObbContact contact = obbContact(attachment, body);
            if (contact == null) continue;
            double seamSlop = body.region == attachment.parentRegion
                    ? Math.max(0.018, attachment.halfExtents.z * 1.45) : 0.004;
            double depth = contact.depth - seamSlop;
            if (depth <= 0.0) continue;
            AttachmentContact candidate = new AttachmentContact(contact.normal.scale(-1), depth);
            if (deepest == null || candidate.depth > deepest.depth) deepest = candidate;
        }
        return deepest;
    }

    private record AttachmentContact(Vec3 outward, double depth) { }

    private static ObbContact deepestWorldContact(ClientLevel level, RigidBodyPiece part) {
        AABB broad = boundsAt(part, part.position).inflate(0.0015);
        ObbContact deepest = null;
        for (var shape : level.getBlockCollisions(null, broad))
            for (AABB block : shape.toAabbs()) {
                ObbContact contact = obbContact(part, part.position, block);
                if (contact != null && (deepest == null || contact.depth > deepest.depth))
                    deepest = contact;
            }
        return deepest;
    }

    private static boolean hasGroundSupport(ClientLevel level, RigidBodyPiece part) {
        return !isWorldClear(level, part, part.position.add(0, -0.006, 0));
    }

    private boolean hasNonHeadSupport(int entityId) {
        for (RigidBodyPiece piece : pieces)
            if (piece.entityId == entityId && piece.region != 0
                    && isAnatomicalRegion(piece.region) && piece.supportTicks > 0) return true;
        return false;
    }

    private boolean resolveEntityCollisions(ClientLevel level, RigidBodyPiece part) {
        if (isGripRegion(part.region)) return false;
        boolean collided = false;
        AABB broadPhase = boundsAt(part, part.position).inflate(0.12);
        for (Entity entity : level.getEntities((Entity) null, broadPhase,
                candidate -> candidate.isAlive() && candidate.getId() != part.entityId)) {
            AABB box = entity.getBoundingBox();
            ObbContact contact = obbContact(part, part.position, box);
            if (contact == null) continue;
            Vec3 normal = contact.normal.scale(-1); // solid entity -> rigid body
            double correctionDistance = Math.min(0.085, contact.depth + 0.002);
            Vec3 correction = normal.scale(correctionDistance);
            boolean articulated = ragdolled.contains(part.entityId);
            if (articulated) {
                Vec3 sharedCorrection = correction.scale(0.38);
                for (RigidBodyPiece islandPart : pieces) if (islandPart.entityId == part.entityId) {
                    Vec3 candidate = islandPart.position.add(sharedCorrection);
                    if (isWorldClear(level, islandPart, candidate)) islandPart.position = candidate;
                }
                correction = correction.scale(0.62);
            }
            Vec3 correctedPart = part.position.add(correction);
            if (isWorldClear(level, part, correctedPart)) part.position = correctedPart;
            Vec3 relative = part.velocity.subtract(entity.getDeltaMovement());
            if (relative.dot(normal) < 0) {
                applyCollisionTorque(part, normal, relative);
                Vec3 desiredVelocity = RagdollMath.reflect(relative, normal, 0.18, 0.44)
                        .add(entity.getDeltaMovement().scale(0.82));
                distributeIslandVelocity(part, desiredVelocity);
                double closingSpeed = -relative.dot(normal);
                Vec3 entityImpulse = normal.scale(-Math.min(0.40,
                        closingSpeed * part.mass() * 0.92));
                if (entityImpulse.lengthSqr() > 0.0004
                        && traumaDecayTicker - entityImpactAges.getOrDefault(entity.getId(), -1000) >= 4
                        && ClientPlayNetworking.canSend(RagdollEntityImpactPayload.TYPE)) {
                    entityImpactAges.put(entity.getId(), traumaDecayTicker);
                    ClientPlayNetworking.send(new RagdollEntityImpactPayload(entity.getId(),
                            (float) entityImpulse.x, (float) entityImpulse.y, (float) entityImpulse.z));
                    entity.push(entityImpulse.x, entityImpulse.y, entityImpulse.z);
                }
            }
            if (entity.getDeltaMovement().horizontalDistanceSqr() > 0.0005)
                distributeIslandVelocity(part, part.velocity.add(entity.getDeltaMovement().scale(0.18)));
            part.angularVelocity = part.angularVelocity.add(normal.cross(relative).scale(0.045));
            collided = true;
        }
        return collided;
    }

    private void distributeIslandVelocity(RigidBodyPiece contacted, Vec3 desiredVelocity) {
        Vec3 velocityChange = desiredVelocity.subtract(contacted.velocity);
        if (!ragdolled.contains(contacted.entityId)) {
            contacted.velocity = desiredVelocity;
            return;
        }
        double islandMass = 0.0;
        for (RigidBodyPiece piece : pieces)
            if (piece.entityId == contacted.entityId) islandMass += piece.mass();
        if (islandMass < 1.0e-6) {
            contacted.velocity = desiredVelocity;
            return;
        }
        Vec3 sharedVelocity = velocityChange.scale(contacted.mass() / islandMass * 0.72);
        for (RigidBodyPiece piece : pieces) if (piece.entityId == contacted.entityId) {
            piece.velocity = piece.velocity.add(sharedVelocity);
            piece.sleeping = false;
        }
        contacted.velocity = contacted.velocity.add(velocityChange.scale(0.28));
        wakeGroup(contacted.entityId);
    }

    private static AABB boundsAt(RigidBodyPiece part, Vec3 center) {
        Vec3 collisionHalf = collisionHalfExtents(part);
        Vector3f x = part.orientation.transform(new Vector3f((float) collisionHalf.x, 0, 0));
        Vector3f y = part.orientation.transform(new Vector3f(0, (float) collisionHalf.y, 0));
        Vector3f z = part.orientation.transform(new Vector3f(0, 0, (float) collisionHalf.z));
        double hx = Math.abs(x.x) + Math.abs(y.x) + Math.abs(z.x);
        double hy = Math.abs(x.y) + Math.abs(y.y) + Math.abs(z.y);
        double hz = Math.abs(x.z) + Math.abs(y.z) + Math.abs(z.z);
        return new AABB(center.x - hx, center.y - hy, center.z - hz,
                center.x + hx, center.y + hy, center.z + hz);
    }

    private static boolean isWorldClear(ClientLevel level, RigidBodyPiece part, Vec3 center) {
        if (isGripRegion(part.region)) return true;
        AABB broad = boundsAt(part, center).deflate(0.00035);
        for (var shape : level.getBlockCollisions(null, broad))
            for (AABB box : shape.toAabbs())
                if (obbContact(part, center, box) != null) return false;
        return true;
    }

    private static Vec3[] axes(RigidBodyPiece part) {
        Vector3f x = part.orientation.transform(new Vector3f(1, 0, 0));
        Vector3f y = part.orientation.transform(new Vector3f(0, 1, 0));
        Vector3f z = part.orientation.transform(new Vector3f(0, 0, 1));
        return new Vec3[] {new Vec3(x.x, x.y, x.z), new Vec3(y.x, y.y, y.z), new Vec3(z.x, z.y, z.z)};
    }

    private static ObbContact obbContact(RigidBodyPiece part, Vec3 center, AABB box) {
        Vec3 boxCenter = box.getCenter();
        Vec3 boxHalf = new Vec3(box.getXsize() * 0.5, box.getYsize() * 0.5, box.getZsize() * 0.5);
        return satContact(center, collisionHalfExtents(part), axes(part), boxCenter, boxHalf,
                new Vec3[] {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)});
    }

    private static ObbContact obbContact(RigidBodyPiece a, RigidBodyPiece b) {
        return satContact(a.position, collisionHalfExtents(a), axes(a),
                b.position, collisionHalfExtents(b), axes(b));
    }

    private static Vec3 collisionHalfExtents(RigidBodyPiece part) {
        if (part.playerBody && part.region == 0)
            return new Vec3(part.halfExtents.x * 0.82, part.halfExtents.y * 0.80,
                    part.halfExtents.z * 0.82);
        return part.halfExtents;
    }

    private static ObbContact satContact(Vec3 centerA, Vec3 halfA, Vec3[] axesA,
                                         Vec3 centerB, Vec3 halfB, Vec3[] axesB) {
        Vec3 delta = centerB.subtract(centerA);
        Vec3 bestAxis = null;
        double bestDepth = Double.POSITIVE_INFINITY;
        Vec3[] candidates = new Vec3[15];
        System.arraycopy(axesA, 0, candidates, 0, 3);
        System.arraycopy(axesB, 0, candidates, 3, 3);
        int index = 6;
        for (Vec3 axisA : axesA) for (Vec3 axisB : axesB) candidates[index++] = axisA.cross(axisB);
        for (Vec3 rawAxis : candidates) {
            double lengthSqr = rawAxis.lengthSqr();
            if (lengthSqr < 1.0e-10) continue;
            Vec3 axis = rawAxis.scale(1.0 / Math.sqrt(lengthSqr));
            double radiusA = projectionRadius(halfA, axesA, axis);
            double radiusB = projectionRadius(halfB, axesB, axis);
            double overlap = radiusA + radiusB - Math.abs(delta.dot(axis));
            if (overlap <= 0.00045) return null;
            if (overlap < bestDepth) {
                bestDepth = overlap;
                bestAxis = delta.dot(axis) < 0 ? axis.scale(-1) : axis;
            }
        }
        return bestAxis == null ? null : new ObbContact(bestAxis, bestDepth);
    }

    private static double projectionRadius(Vec3 half, Vec3[] axes, Vec3 direction) {
        return half.x * Math.abs(axes[0].dot(direction))
                + half.y * Math.abs(axes[1].dot(direction))
                + half.z * Math.abs(axes[2].dot(direction));
    }

    private record ObbContact(Vec3 normal, double depth) { }

    private void solveJoints(ClientLevel level, List<RigidBodyPiece> active) {
        for (RigidBodyPiece child : active) {
            if (child.parentRegion < 0) continue;
            RigidBodyPiece parent = find(child.entityId, child.parentRegion);
            if (parent == null) continue;
            if (child.sleeping && parent.sleeping) continue;
            Vec3 delta = child.anchoredJoint
                    ? worldAnchor(child, child.childJointAnchor).subtract(worldAnchor(parent, child.parentJointAnchor))
                    : child.position.subtract(parent.position);
            double length = Math.max(1.0e-5, delta.length());
            double distanceError = child.anchoredJoint ? length : length - child.jointLength;
            double maxCorrection = Math.max(0.018, Math.min(child.radius(), parent.radius()) * 0.20);
            double correctionLength = Mth.clamp(distanceError * (child.anchoredJoint ? 0.82 : 0.42),
                    -maxCorrection, maxCorrection);
            Vec3 correction = delta.scale(correctionLength / length);
            boolean attachment = isAttachmentRegion(child.region);
            double childInverseMass = child == grabbed ? 0.0 : child.inverseMass();
            double parentInverseMass = parent == grabbed || attachment ? 0.0 : parent.inverseMass();
            double inverseMassSum = childInverseMass + parentInverseMass;
            double childWeight = inverseMassSum < 1.0e-8 ? 0.5 : childInverseMass / inverseMassSum;
            Vec3 childCandidate = child.position.subtract(correction.scale(childWeight));
            Vec3 parentCandidate = parent.position.add(correction.scale(1.0 - childWeight));
            if (isWorldClear(level, child, childCandidate)) child.position = childCandidate;
            else child.velocity = child.velocity.scale(0.72);
            if (!attachment) {
                if (isWorldClear(level, parent, parentCandidate)) parent.position = parentCandidate;
                else parent.velocity = parent.velocity.scale(0.72);
            }

            Vec3 centerDelta = child.position.subtract(parent.position);
            double centerDistance = centerDelta.length();
            double restCenterDistance = Math.max(child.jointLength, child.jointRestOffset.length());
            double minimumCenterDistance = restCenterDistance * (child.playerBody ? 0.58 : 0.44);
            if (centerDistance < minimumCenterDistance) {
                Vec3 separationAxis;
                if (centerDistance > 1.0e-5) separationAxis = centerDelta.scale(1.0 / centerDistance);
                else {
                    Vector3f expected = parent.orientation.transform(new Vector3f(
                            (float) child.jointRestOffset.x, (float) child.jointRestOffset.y,
                            (float) child.jointRestOffset.z));
                    separationAxis = RagdollMath.safeNormalize(new Vec3(expected.x, expected.y, expected.z),
                            new Vec3(0, 1, 0));
                }
                double unfoldDistance = Math.min(0.045,
                        (minimumCenterDistance - centerDistance) * 0.46);
                Vec3 unfold = separationAxis.scale(unfoldDistance);
                Vec3 unfoldedChild = child.position.add(unfold.scale(childWeight));
                Vec3 unfoldedParent = parent.position.subtract(unfold.scale(1.0 - childWeight));
                if (isWorldClear(level, child, unfoldedChild)) child.position = unfoldedChild;
                if (!attachment && isWorldClear(level, parent, unfoldedParent))
                    parent.position = unfoldedParent;
            }
            if (child.anchoredJoint)
                solveSocketImpulse(child, parent, distanceError,
                        child.playerBody ? 0.17 : globalConstraintBias());
            Vec3 axis = delta.scale(1.0 / length);
            Vec3 relativeVelocity = child.velocity.subtract(parent.velocity);
            double separatingSpeed = relativeVelocity.dot(axis);
            Vec3 relativeAngular = child.angularVelocity.subtract(parent.angularVelocity);
            double angularCoupling = child.playerBody
                    ? isLimbRegion(child.region) ? 0.002 : 0.012
                    : 0.012;
            child.angularVelocity = child.angularVelocity.subtract(relativeAngular.scale(angularCoupling)).scale(0.9985);
            if (!attachment)
                parent.angularVelocity = parent.angularVelocity.add(
                        relativeAngular.scale(angularCoupling * 0.5)).scale(0.999);
            solveAngularMotor(level, child, parent, childWeight);
            double maximumJointSpin = isAttachmentRegion(child.region) ? 0.16
                    : child.playerBody && isLimbRegion(child.region) ? 0.72
                    : child.playerBody ? 0.52 : isLimbRegion(child.region) ? 0.90 : 0.74;
            if (child.angularVelocity.length() > maximumJointSpin)
                child.angularVelocity = child.angularVelocity.normalize().scale(maximumJointSpin);
            if (Math.abs(distanceError) > 0.008 || Math.abs(separatingSpeed) > 0.018) {
                child.sleeping = false;
                if (!isClothRegion(child.region)) parent.sleeping = false;
            }
        }
    }

    private void warmStartJoints(List<RigidBodyPiece> active) {
        for (RigidBodyPiece child : active) {
            if (!child.anchoredJoint || child.parentRegion < 0 || child.jointImpulse.lengthSqr() < 1.0e-10)
                continue;
            RigidBodyPiece parent = find(child.entityId, child.parentRegion);
            if (parent == null) continue;
            if (child.sleeping && parent.sleeping) continue;
            Vec3 childAnchor = worldAnchor(child, child.childJointAnchor);
            Vec3 parentAnchor = worldAnchor(parent, child.parentJointAnchor);
            if (child != grabbed) child.applyImpulse(childAnchor, child.jointImpulse);
            if (parent != grabbed && isAnatomicalRegion(child.region))
                parent.applyImpulse(parentAnchor, child.jointImpulse.scale(-1));
        }
    }

    private void solveSocketImpulse(RigidBodyPiece child, RigidBodyPiece parent,
                                    double distanceError, double biasFactor) {
        Vec3 childAnchor = worldAnchor(child, child.childJointAnchor);
        Vec3 parentAnchor = worldAnchor(parent, child.parentJointAnchor);
        Vec3 error = childAnchor.subtract(parentAnchor);
        Vec3 relativeVelocity = child.velocityAt(childAnchor).subtract(parent.velocityAt(parentAnchor));
        Vec3 impulseTotal = Vec3.ZERO;
        Vec3[] basis = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};
        for (Vec3 axis : basis) {
            boolean attachment = isAttachmentRegion(child.region);
            double effectiveMass = socketEffectiveMass(child, childAnchor, axis, child == grabbed)
                    + socketEffectiveMass(parent, parentAnchor, axis, parent == grabbed || attachment);
            if (effectiveMass < 1.0e-8) continue;
            double velocityError = relativeVelocity.dot(axis);
            double positionalBias = Mth.clamp(error.dot(axis) * biasFactor, -0.12, 0.12);
            double lambda = -(velocityError + positionalBias) / effectiveMass;
            double impulseLimit = child.playerBody ? 0.060 : 0.078;
            lambda = Mth.clamp(lambda, -impulseLimit, impulseLimit);
            Vec3 impulse = axis.scale(lambda);
            if (child != grabbed) child.applyImpulse(childAnchor, impulse);
            if (parent != grabbed && !attachment) parent.applyImpulse(parentAnchor, impulse.scale(-1));
            impulseTotal = impulseTotal.add(impulse);
            relativeVelocity = child.velocityAt(childAnchor).subtract(parent.velocityAt(parentAnchor));
        }
        Vec3 accumulated = child.jointImpulse.add(impulseTotal);
        double accumulatedLimit = child.playerBody ? 0.14 : 0.24;
        child.jointImpulse = accumulated.lengthSqr() > accumulatedLimit * accumulatedLimit
                ? accumulated.normalize().scale(accumulatedLimit) : accumulated;
        if (Math.abs(distanceError) > 0.008 || impulseTotal.lengthSqr() > 2.5e-5) {
            child.sleeping = false;
            parent.sleeping = false;
        }
    }

    private static double socketEffectiveMass(RigidBodyPiece body, Vec3 anchor,
                                              Vec3 axis, boolean fixed) {
        if (fixed) return 0.0;
        Vec3 lever = anchor.subtract(body.position);
        Vec3 angular = body.inverseInertia(lever.cross(axis)).cross(lever);
        return body.inverseMass() + axis.dot(angular);
    }

    private static double globalConstraintBias() {
        return 0.13;
    }

    private void enforceJointInvariant(ClientLevel level, List<RigidBodyPiece> active) {
        for (RigidBodyPiece child : active) {
            if (!child.anchoredJoint || child.parentRegion < 0) continue;
            RigidBodyPiece parent = find(child.entityId, child.parentRegion);
            if (parent == null) continue;
            if (child.sleeping && parent.sleeping) continue;
            Vec3 error = worldAnchor(child, child.childJointAnchor)
                    .subtract(worldAnchor(parent, child.parentJointAnchor));
            double distance = error.length();
            if (distance <= 0.003) continue;
            boolean attachment = isAttachmentRegion(child.region);
            double childInverseMass = child == grabbed ? 0.0 : child.inverseMass();
            double parentInverseMass = parent == grabbed || attachment ? 0.0 : parent.inverseMass();
            double inverseSum = childInverseMass + parentInverseMass;
            if (inverseSum < 1.0e-8) continue;
            double childWeight = childInverseMass / inverseSum;
            Vec3 childCandidate = child.position.subtract(error.scale(childWeight));
            Vec3 parentCandidate = parent.position.add(error.scale(1.0 - childWeight));
            boolean childClear = child == grabbed || isWorldClear(level, child, childCandidate);
            boolean parentClear = parent == grabbed || isWorldClear(level, parent, parentCandidate);
            if (childClear && parentClear) {
                if (child != grabbed) child.position = childCandidate;
                if (parent != grabbed && !attachment) parent.position = parentCandidate;
            } else if (child != grabbed && isWorldClear(level, child, child.position.subtract(error))) {
                child.position = child.position.subtract(error);
            } else if (!attachment && parent != grabbed
                    && isWorldClear(level, parent, parent.position.add(error))) {
                parent.position = parent.position.add(error);
            } else {
                child.velocity = child.velocity.scale(0.72);
                if (!attachment) parent.velocity = parent.velocity.scale(0.72);
                continue;
            }

            Vec3 axis = error.scale(1.0 / distance);
            Vec3 relative = child.velocity.subtract(parent.velocity);
            double separating = relative.dot(axis);
            if (Math.abs(separating) > 1.0e-5) {
                Vec3 cancel = axis.scale(separating);
                if (child != grabbed) child.velocity = child.velocity.subtract(cancel.scale(childWeight));
                if (parent != grabbed && !attachment)
                    parent.velocity = parent.velocity.add(cancel.scale(1.0 - childWeight));
            }
        }
    }

    private void enforceExactAnatomicalSockets(List<RigidBodyPiece> active) {
        for (RigidBodyPiece child : active) {
            if (!isAnatomicalRegion(child.region) || !child.anchoredJoint
                    || child.parentRegion < 0 || child == grabbed) continue;
            RigidBodyPiece parent = find(child.entityId, child.parentRegion);
            if (parent == null) continue;
            Vec3 parentAnchor = worldAnchor(parent, child.parentJointAnchor);
            Vector3f rotatedChildAnchor = child.orientation.transform(new Vector3f(
                    (float) child.childJointAnchor.x, (float) child.childJointAnchor.y,
                    (float) child.childJointAnchor.z));
            Vec3 lever = new Vec3(rotatedChildAnchor.x, rotatedChildAnchor.y, rotatedChildAnchor.z);
            child.position = parentAnchor.subtract(lever);
            if (child.playerBody && isLimbRegion(child.region)) {
                keepPlayerLimbOutsideCore(child, parentAnchor);
                rotatedChildAnchor = child.orientation.transform(new Vector3f(
                        (float) child.childJointAnchor.x, (float) child.childJointAnchor.y,
                        (float) child.childJointAnchor.z));
                lever = new Vec3(rotatedChildAnchor.x, rotatedChildAnchor.y, rotatedChildAnchor.z);
                child.position = parentAnchor.subtract(lever);
            }
            Vec3 parentAnchorVelocity = parent.velocityAt(parentAnchor);
            child.velocity = parentAnchorVelocity.subtract(child.angularVelocity.cross(lever));
            if (!parent.sleeping) child.sleeping = false;
        }
    }

    private void keepPlayerLimbOutsideCore(RigidBodyPiece limb, Vec3 socket) {
        for (int pass = 0; pass < 2; pass++) {
            Vec3 outward = null;
            double penetration = 0.0;
            if (limb.region == 2 || limb.region == 3) {
                Vec3 awayFromSocket = RagdollMath.safeNormalize(limb.position.subtract(socket),
                        transformedAxis(limb, new Vec3(0, 1, 0)));
                Vec3 probe = limb.position.add(awayFromSocket.scale(limb.halfExtents.y * 0.58));
                for (int coreRegion : new int[] {1, 0}) {
                    RigidBodyPiece core = find(limb.entityId, coreRegion);
                    if (core == null) continue;
                    PointEscape escape = pointEscape(core, probe, 0.012);
                    if (escape != null && escape.depth > penetration) {
                        penetration = escape.depth;
                        outward = escape.outward;
                    }
                }
            } else {
                ObbContact deepest = null;
                for (int coreRegion : new int[] {1, 0}) {
                    RigidBodyPiece core = find(limb.entityId, coreRegion);
                    if (core == null) continue;
                    ObbContact contact = obbContact(limb, core);
                    if (contact != null && contact.depth > 0.014
                            && (deepest == null || contact.depth > deepest.depth)) deepest = contact;
                }
                if (deepest != null) {
                    penetration = deepest.depth;
                    outward = deepest.normal.scale(-1);
                }
            }
            if (outward == null) return;
            Vec3 centerLever = limb.position.subtract(socket);
            Vec3 rotationAxis = centerLever.cross(outward);
            if (rotationAxis.lengthSqr() < 1.0e-8)
                rotationAxis = axes(limb)[2].cross(outward);
            if (rotationAxis.lengthSqr() < 1.0e-8) return;
            double angle = Mth.clamp(penetration / Math.max(0.08, limb.radius()) * 0.20,
                    0.012, 0.085);
            Vector3f axis = new Vector3f((float) rotationAxis.x, (float) rotationAxis.y,
                    (float) rotationAxis.z).normalize();
            Quaternionf correction = new Quaternionf().fromAxisAngleRad(axis, (float) angle);
            limb.orientation.set(correction.mul(limb.orientation)).normalize();
            Vector3f anchor = limb.orientation.transform(new Vector3f((float) limb.childJointAnchor.x,
                    (float) limb.childJointAnchor.y, (float) limb.childJointAnchor.z));
            limb.position = socket.subtract(new Vec3(anchor.x, anchor.y, anchor.z));
            limb.angularVelocity = limb.angularVelocity.scale(0.92);
        }
    }

    private static Vec3 transformedAxis(RigidBodyPiece body, Vec3 localAxis) {
        Vector3f axis = body.orientation.transform(new Vector3f(
                (float) localAxis.x, (float) localAxis.y, (float) localAxis.z));
        return new Vec3(axis.x, axis.y, axis.z);
    }

    private static PointEscape pointEscape(RigidBodyPiece box, Vec3 point, double inset) {
        Vector3f local = new Vector3f((float) (point.x - box.position.x),
                (float) (point.y - box.position.y), (float) (point.z - box.position.z));
        new Quaternionf(box.orientation).conjugate().transform(local);
        double px = box.halfExtents.x - inset - Math.abs(local.x);
        double py = box.halfExtents.y - inset - Math.abs(local.y);
        double pz = box.halfExtents.z - inset - Math.abs(local.z);
        if (px <= 0 || py <= 0 || pz <= 0) return null;
        Vec3 localOutward;
        double depth;
        if (px <= py && px <= pz) {
            depth = px;
            localOutward = new Vec3(Math.copySign(1.0, local.x == 0 ? 1.0 : local.x), 0, 0);
        } else if (py <= pz) {
            depth = py;
            localOutward = new Vec3(0, Math.copySign(1.0, local.y == 0 ? 1.0 : local.y), 0);
        } else {
            depth = pz;
            localOutward = new Vec3(0, 0, Math.copySign(1.0, local.z == 0 ? 1.0 : local.z));
        }
        return new PointEscape(RagdollMath.safeNormalize(transformedAxis(box, localOutward),
                new Vec3(0, 1, 0)), depth);
    }

    private record PointEscape(Vec3 outward, double depth) { }

    private void solveAngularMotor(ClientLevel level, RigidBodyPiece child,
                                   RigidBodyPiece parent, double childWeight) {
        if (!child.anchoredJoint) return;
        boolean applyVelocityMotor = child.lastMotorAge != child.age;
        Quaternionf relative = new Quaternionf(parent.orientation).conjugate()
                .mul(child.orientation).normalize();
        Quaternionf deviation = new Quaternionf(child.jointRestOrientation).conjugate()
                .mul(relative).normalize();
        Vector3f angles = deviation.getEulerAnglesXYZ(new Vector3f());
        double errorX = motorError(angles.x, child.angularLimit.x);
        double errorY = motorError(angles.y, child.angularLimit.y);
        double errorZ = motorError(angles.z, child.angularLimit.z);
        double limitViolation = Math.sqrt(errorX * errorX + errorY * errorY + errorZ * errorZ);

        if (limitViolation > 1.0e-4 && child != grabbed) {
            float legalX = (float) Mth.clamp(angles.x,
                    -child.angularLimit.x, child.angularLimit.x);
            float legalY = (float) Mth.clamp(angles.y,
                    -child.angularLimit.y, child.angularLimit.y);
            float legalZ = (float) Mth.clamp(angles.z,
                    -child.angularLimit.z, child.angularLimit.z);
            Quaternionf legalRelative = new Quaternionf(child.jointRestOrientation)
                    .mul(new Quaternionf().rotationXYZ(legalX, legalY, legalZ)).normalize();
            Quaternionf legalWorld = new Quaternionf(parent.orientation).mul(legalRelative).normalize();
            Quaternionf original = new Quaternionf(child.orientation);
            float projection = child.playerBody
                    ? isLimbRegion(child.region)
                    ? Mth.clamp(RagdollRuntime.INSTANCE.config.playerJointTightness * 0.24f, 0.10f, 0.25f)
                    : isAnatomicalRegion(child.region)
                    ? Mth.clamp(RagdollRuntime.INSTANCE.config.playerJointTightness * 0.30f, 0.14f, 0.32f)
                    : Mth.clamp(RagdollRuntime.INSTANCE.config.playerJointTightness * 0.42f, 0.18f, 0.44f)
                    : isLimbRegion(child.region)
                    ? (float) Mth.clamp(0.045 + limitViolation * 0.10, 0.045, 0.14)
                    : (float) Mth.clamp(0.075 + limitViolation * 0.14, 0.075, 0.24);
            child.orientation.slerp(legalWorld, projection).normalize();
            if (!isWorldClear(level, child, child.position)) child.orientation.set(original);
            else {
                double arrest = child.playerBody
                        ? isAnatomicalRegion(child.region) ? 0.34 : 0.56
                        : isLimbRegion(child.region)
                        ? Mth.clamp(0.08 + limitViolation * 0.16, 0.08, 0.25)
                        : Mth.clamp(0.14 + limitViolation * 0.22, 0.14, 0.38);
                child.angularVelocity = child.angularVelocity.lerp(parent.angularVelocity, arrest);
            }
        }
        if (!applyVelocityMotor || child.angularStiffness <= 0.0f) return;
        child.lastMotorAge = child.age;
        boolean fracturedLeg = child.playerBody
                && playerFracturedLegs.getOrDefault(child.entityId, -1) == child.region;
        boolean jointGrounded = fracturedLeg
                && (child.supportTicks > 0 || parent.supportTicks > 0);
        double bindRecovery = fracturedLeg ? jointGrounded ? 0.0 : 0.018
                : isLimbRegion(child.region) ? child.playerBody ? 0.0 : 0.006
                : child.playerBody ? 0.014 : 0.012;
        Vec3 bindError = new Vec3(-angles.x, -angles.y, -angles.z)
                .scale(child.angularStiffness * bindRecovery);
        Vec3 limitError = new Vec3(errorX, errorY, errorZ)
                .scale(child.angularStiffness * 1.55);
        Vec3 relativeVelocityWorld = child.angularVelocity.subtract(parent.angularVelocity);
        Vector3f relativeVelocityLocalVector = new Vector3f((float) relativeVelocityWorld.x,
                (float) relativeVelocityWorld.y, (float) relativeVelocityWorld.z);
        new Quaternionf(parent.orientation).conjugate().transform(relativeVelocityLocalVector);
        Vec3 relativeVelocityLocal = new Vec3(relativeVelocityLocalVector.x,
                relativeVelocityLocalVector.y, relativeVelocityLocalVector.z);
        Vec3 impulseLocal = bindError.add(limitError)
                .subtract(relativeVelocityLocal.scale(child.angularDamping));
        double maxMotorImpulse = fracturedLeg ? jointGrounded ? 0.0 : 0.012 : child.playerBody
                ? isLimbRegion(child.region) ? 0.018 : 0.044
                : isLimbRegion(child.region) ? 0.026 : 0.050;
        if (impulseLocal.length() > maxMotorImpulse)
            impulseLocal = impulseLocal.normalize().scale(maxMotorImpulse);
        Vector3f impulseWorldVector = parent.orientation.transform(new Vector3f(
                (float) impulseLocal.x, (float) impulseLocal.y, (float) impulseLocal.z));
        Vec3 impulseWorld = new Vec3(impulseWorldVector.x, impulseWorldVector.y, impulseWorldVector.z);
        if (child != grabbed)
            child.angularVelocity = child.angularVelocity.add(impulseWorld.scale(childWeight));
        if (parent != grabbed && isAnatomicalRegion(child.region))
            parent.angularVelocity = parent.angularVelocity.subtract(impulseWorld.scale(1.0 - childWeight));
    }

    private static double motorError(double angle, double limit) {
        double wrapped = Mth.wrapDegrees(Math.toDegrees(angle));
        double radians = Math.toRadians(wrapped);
        return radians > limit ? limit - radians : radians < -limit ? -limit - radians : 0.0;
    }

    private void solveSelfCollisions(ClientLevel level, List<RigidBodyPiece> active) {
        List<RigidBodyPiece> candidates = selfCollisionCandidates;
        Map<RigidBodyPiece, AABB> bounds = selfCollisionBounds;
        candidates.clear();
        candidates.addAll(active);
        bounds.clear();
        for (RigidBodyPiece part : candidates) bounds.put(part, boundsAt(part, part.position));
        candidates.sort(java.util.Comparator.comparingDouble(part -> bounds.get(part).minX));
        for (int i = 0; i < candidates.size(); i++) for (int j = i + 1; j < candidates.size(); j++) {
            RigidBodyPiece a = candidates.get(i), b = candidates.get(j);
            if (isGripRegion(a.region) || isGripRegion(b.region)) continue;
            AABB aBounds = bounds.get(a), bBounds = bounds.get(b);
            if (bBounds.minX > aBounds.maxX) break;
            boolean sameRagdoll = a.entityId == b.entityId;
            boolean directlyConnected = sameRagdoll
                    && (a.parentRegion == b.region || b.parentRegion == a.region);
            boolean clothContact = sameRagdoll && (isClothRegion(a.region) || isClothRegion(b.region));
            boolean playerAnatomy = sameRagdoll && a.playerBody && b.playerBody
                    && isAnatomicalRegion(a.region) && isAnatomicalRegion(b.region);
            if (a.sleeping && b.sleeping) continue;
            if (clothContact) continue;
            if (!RagdollRuntime.INSTANCE.config.ragdollSelfCollision) continue;
            if (sameRagdoll && directlyConnected) continue;
            if (!aBounds.intersects(bBounds)) continue;
            ObbContact contact = obbContact(a, b);
            if (contact == null) continue;
            double socketSlop = playerAnatomy ? 0.018 : 0.0035;
            if (contact.depth <= socketSlop) continue;
            Vec3 normal = contact.normal;
            double inverseA = a == grabbed ? 0.0 : a.inverseMass();
            double inverseB = b == grabbed ? 0.0 : b.inverseMass();
            double inverseSum = inverseA + inverseB;
            if (inverseSum < 1.0e-8) continue;
            double correctionMagnitude = sameRagdoll
                    ? playerAnatomy
                    ? Math.min(0.006, Math.max(0, contact.depth - socketSlop) * 0.05)
                    : Math.min(0.026, Math.max(0, contact.depth - socketSlop) * 0.22)
                    : Math.min(0.18, Math.max(0, contact.depth - 0.0015) * 0.62);
            Vec3 correction = normal.scale(correctionMagnitude / inverseSum);
            Vec3 aCandidate = a.position.subtract(correction.scale(inverseA));
            Vec3 bCandidate = b.position.add(correction.scale(inverseB));
            if (inverseA > 0 && isWorldClear(level, a, aCandidate)) a.position = aCandidate;
            if (inverseB > 0 && isWorldClear(level, b, bCandidate)) b.position = bCandidate;
            Vec3 relativeVelocity = b.velocity.subtract(a.velocity);
            double closing = relativeVelocity.dot(normal);
            if (closing < 0) {
                double impulseMagnitude = sameRagdoll
                        ? -closing / inverseSum * (playerAnatomy ? 0.012 : 0.10)
                        : -(1.0 + 0.08) * closing / inverseSum;
                Vec3 normalImpulse = normal.scale(impulseMagnitude);
                if (!sameRagdoll) {
                    applyCollisionTorque(a, normal.scale(-1), a.velocity.subtract(b.velocity));
                    applyCollisionTorque(b, normal, b.velocity.subtract(a.velocity));
                }
                if (inverseA > 0) a.velocity = a.velocity.subtract(normalImpulse.scale(inverseA));
                if (inverseB > 0) b.velocity = b.velocity.add(normalImpulse.scale(inverseB));
                Vec3 tangent = relativeVelocity.subtract(normal.scale(closing));
                if (!sameRagdoll && tangent.lengthSqr() > 1.0e-8) {
                    tangent = tangent.normalize();
                    double tangentSpeed = relativeVelocity.dot(tangent);
                    double frictionMagnitude = Mth.clamp(-tangentSpeed / inverseSum,
                            -impulseMagnitude * 0.42, impulseMagnitude * 0.42);
                    Vec3 frictionImpulse = tangent.scale(frictionMagnitude);
                    if (inverseA > 0) a.velocity = a.velocity.subtract(frictionImpulse.scale(inverseA));
                    if (inverseB > 0) b.velocity = b.velocity.add(frictionImpulse.scale(inverseB));
                }
            }
            boolean meaningfulContact = sameRagdoll
                    ? contact.depth > socketSlop + (playerAnatomy ? 0.024 : 0.016)
                    && Math.abs(closing) > (playerAnatomy ? 0.065 : 0.035)
                    : contact.depth > 0.003 || Math.abs(closing) > 0.018;
            if (meaningfulContact) {
                a.sleeping = false;
                b.sleeping = false;
            }
        }
    }

    private void wakeGroup(int entityId) {
        islandSleepTicks.remove(entityId);
        for (RigidBodyPiece piece : pieces) if (piece.entityId == entityId) piece.sleeping = false;
    }

    private void updateSleepingIslands() {
        Map<Integer, List<RigidBodyPiece>> islands = new HashMap<>();
        for (RigidBodyPiece piece : pieces)
            islands.computeIfAbsent(piece.entityId, ignored -> new ArrayList<>()).add(piece);
        islandSleepTicks.keySet().removeIf(entityId -> !islands.containsKey(entityId));
        for (Map.Entry<Integer, List<RigidBodyPiece>> entry : islands.entrySet()) {
            List<RigidBodyPiece> island = entry.getValue();
            boolean playerIsland = island.stream().anyMatch(part -> part.playerBody);
            boolean supportedIsland = island.stream()
                    .anyMatch(part -> part.region != 0 && isAnatomicalRegion(part.region)
                            && part.supportTicks >= 2);
            boolean groundedPlayer = playerIsland && supportedIsland;
            boolean stableManifolds = !groundedPlayer || island.stream()
                    .filter(part -> part.region != 0 && isAnatomicalRegion(part.region)
                            && part.supportTicks >= 2)
                    .allMatch(part -> part.contacts.values().stream()
                            .anyMatch(contact -> contact.stableSteps >= 6));
            boolean settledPose = supportedIsland;
            for (RigidBodyPiece part : island) {
                if (!settledPose || isAttachmentRegion(part.region) || part.parentRegion < 0
                        || part.supportTicks >= 2 || !part.anchoredJoint) continue;
                if (part.region == 0) continue;
                RigidBodyPiece parent = find(part.entityId, part.parentRegion);
                if (parent == null) continue;
                Vec3 socket = worldAnchor(parent, part.parentJointAnchor);
                Vec3 lever = part.position.subtract(socket);
                double horizontalLever = Math.sqrt(lever.x * lever.x + lever.z * lever.z);
                if (horizontalLever > Math.max(0.035, lever.length() * 0.32)) settledPose = false;
            }
            boolean quiet = settledPose && (grabbed == null || grabbed.entityId != entry.getKey());
            double linearSleep = groundedPlayer ? 0.0022 : 0.00055;
            double angularSleep = groundedPlayer ? 0.0016 : 0.00025;
            for (RigidBodyPiece part : island) {
                if (isAttachmentRegion(part.region)) continue;
                quiet &= part.velocity.lengthSqr() < linearSleep
                        && part.angularVelocity.lengthSqr() < angularSleep;
            }
            int quietTicks = quiet ? islandSleepTicks.getOrDefault(entry.getKey(), 0) + 1 : 0;
            islandSleepTicks.put(entry.getKey(), quietTicks);
            int sleepDelay = groundedPlayer ? stableManifolds ? 8 : 14 : playerIsland ? 36 : 14;
            if (quietTicks >= sleepDelay) {
                for (RigidBodyPiece part : island) {
                    part.velocity = Vec3.ZERO;
                    part.angularVelocity = Vec3.ZERO;
                    part.jointImpulse = Vec3.ZERO;
                    part.lastEnergyDelta = 0.0;
                    part.sleeping = true;
                }
            } else if (!quiet) {
                for (RigidBodyPiece part : island) part.sleeping = false;
            }
        }
    }

    private RigidBodyPiece find(int entityId, int region) {
        if (solverIndex != null) return solverIndex.get(pieceKey(entityId, region));
        for (RigidBodyPiece piece : pieces) if (piece.entityId == entityId && piece.region == region) return piece;
        return null;
    }

    private static long pieceKey(int entityId, int region) {
        return ((long) entityId << 32) ^ (region & 0xffffffffL);
    }

    private void trim(int maximum) {
        while (pieces.size() > Math.max(8, maximum)) {
            RigidBodyPiece removed = pieces.removeFirst();
            if (grabbed == removed) grabbed = null;
        }
    }

    public boolean isRagdolled(int entityId) { return ragdolled.contains(entityId); }

    public RagdollDebugFrame debugFrame(Player player) {
        if (player == null) return new RagdollDebugFrame(0.0, List.of());
        List<RagdollDebugPart> result = new ArrayList<>();
        for (RigidBodyPiece part : pieces) if (part.entityId == player.getId()) {
            RigidBodyPiece parent = part.parentRegion < 0 ? null : find(part.entityId, part.parentRegion);
            double constraintError = parent == null ? 0.0
                    : worldAnchor(part, part.childJointAnchor)
                    .distanceTo(worldAnchor(parent, part.parentJointAnchor));
            result.add(new RagdollDebugPart(part.region, debugRegionName(part.region),
                    part.position, part.position.subtract(part.previous), part.velocity,
                    part.angularVelocity, part.mass(), part.supportTicks > 0,
                    part.supportTicks, part.sleeping, part.jointType.name(),
                    part.contacts.size(), part.contacts.values().stream().findFirst()
                    .map(contact -> contact.normal).orElse(Vec3.ZERO),
                    0.5 * part.mass() * part.velocity.lengthSqr(),
                    part.lastEnergyDelta, constraintError, part.physicsBlend));
        }
        result.sort(java.util.Comparator.comparingInt(RagdollDebugPart::region));
        return new RagdollDebugFrame(playerArmorWeight(player), List.copyOf(result));
    }

    private static String debugRegionName(int region) {
        return switch (region) {
            case 0 -> "HEAD";
            case 1 -> "TORSO";
            case 2 -> "R ARM";
            case 3 -> "L ARM";
            case 4 -> "R LEG";
            case 5 -> "L LEG";
            case 6 -> "CAPE";
            case 7 -> "L WING";
            case 8 -> "R WING";
            case 9 -> "R FOREARM";
            case 10 -> "L FOREARM";
            case 11 -> "R SHIN";
            case 12 -> "L SHIN";
            case 13 -> "CAPE TIP";
            case 14 -> "L WING TIP";
            case 15 -> "R WING TIP";
            case 30 -> "R ITEM GRIP";
            case 31 -> "L ITEM GRIP";
            default -> "PART " + region;
        };
    }

    public record RagdollDebugFrame(double armorWeight, List<RagdollDebugPart> parts) { }
    public record RagdollDebugPart(int region, String name, Vec3 position, Vec3 movement,
                                   Vec3 velocity, Vec3 angularVelocity, double mass,
                                   boolean supported, int supportTicks, boolean sleeping,
                                   String jointType, int contactCount, Vec3 contactNormal,
                                   double kineticEnergy, double energyDelta,
                                   double constraintError, float physicsBlend) { }
    public float deformation(int entityId, int region) {
        if (!RagdollRuntime.INSTANCE.config.modelDeformation) return 0.0f;
        long key = ((long) entityId << 32) ^ (region & 0xffffffffL);
        float trauma = regionalTrauma.getOrDefault(key, 0.0f);
        float threshold = Math.max(0.5f, RagdollRuntime.INSTANCE.config.dismemberForce);
        return Mth.clamp((trauma / threshold - 0.22f) * 0.085f, 0.0f, 0.12f);
    }
    public Set<Integer> hiddenRegions(int entityId) { return detached.getOrDefault(entityId, Set.of()); }
    public String hiddenModelPath(int entityId, int region) {
        return detachedModelPaths.getOrDefault(entityId, Map.of()).get(region);
    }
    List<RigidBodyPiece> pieces() { return pieces; }

    public void prune(int entityId) {
        detached.remove(entityId);
        detachedModelPaths.remove(entityId);
        renderedPoseCache.remove(entityId);
        ragdolled.remove(entityId);
        rigidSoundAges.remove(entityId);
        regionalTrauma.keySet().removeIf(key -> (int) (key >> 32) == entityId);
    }

    public void clear() { pieces.clear(); detached.clear(); detachedModelPaths.clear(); renderedPoseCache.clear(); ragdolled.clear(); remoteDriven.clear(); remotePoseSequences.clear(); remotePoseTicks.clear(); playerTumbles.clear(); playerFracturedLegs.clear(); appliedFracturePoses.clear(); tumbleStartedAt.clear(); ragdollStartedAt.clear(); electrifiedUntil.clear(); wailing.clear(); recentExplosions.clear(); regionalTrauma.clear(); islandSleepTicks.clear(); blockImpactAges.clear(); entityImpactAges.clear(); rigidSoundAges.clear(); grabbed = null; smoothedGrabTarget = null; solverIndex = null; lastAuthorityTick = Long.MIN_VALUE; }

    private static boolean inAsterion(Entity entity) {
        return entity != null && entity.level().dimension().equals(Asterion.ASTERION_LEVEL);
    }

    private void tickWailing() {
        wailing.entrySet().removeIf(entry -> {
            WailingState state = entry.getValue();
            if (traumaDecayTicker >= state.endTick) return true;
            if (traumaDecayTicker < state.nextTick) return false;
            state.nextTick = traumaDecayTicker + state.intervalTicks;
            for (RigidBodyPiece part : pieces) if (part.entityId == entry.getKey()) part.sleeping = false;
            return false;
        });
    }

    private static final class WailingState {
        final float stiffness;
        final int endTick;
        final int intervalTicks;
        int nextTick;
        WailingState(float stiffness, int endTick, int intervalTicks, int nextTick) {
            this.stiffness = stiffness;
            this.endTick = endTick;
            this.intervalTicks = intervalTicks;
            this.nextTick = nextTick;
        }
    }

    private record RecentExplosion(
            Vec3 center, float radius, int createdTick) { }
}
