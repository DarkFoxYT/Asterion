package net.krodark.asterion.client.ragdoll;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RigidBodyPiece {
    final int entityId;
    final int region;
    final int parentRegion;
    final boolean playerBody;
    Vec3 halfExtents;
    double partMass;
    final Identifier texture;
    final int bloodRgb;
    ItemStack headEquipment = ItemStack.EMPTY;
    ItemStack chestEquipment = ItemStack.EMPTY;
    ItemStack legEquipment = ItemStack.EMPTY;
    ItemStack footEquipment = ItemStack.EMPTY;
    float[][] faceUvs;
    float[][] overlayFaceUvs;
    final Quaternionf orientation = new Quaternionf();
    final Quaternionf previousOrientation = new Quaternionf();
    Vec3 previous;
    Vec3 position;
    Vec3 velocity;
    Vec3 angularVelocity;
    float jointLength;
    Vec3 childJointAnchor = Vec3.ZERO;
    Vec3 parentJointAnchor = Vec3.ZERO;
    Vec3 jointRestOffset = Vec3.ZERO;
    Vec3 jointImpulse = Vec3.ZERO;
    boolean anchoredJoint;
    final Quaternionf jointRestOrientation = new Quaternionf();
    float angularStiffness;
    float angularDamping;
    JointType jointType = JointType.CONE_TWIST;
    Vec3 angularLimit = new Vec3(Math.PI, Math.PI, Math.PI);
    int lastMotorAge = -1;
    int age;
    int bounces;
    boolean sleeping;
    float physicsBlend;
    double lastEnergyDelta;
    float bloodReservoir = 0.42f;
    float contusion;
    int lastImpactBleed = -100;
    int lastBruiseAge = -100;
    int lastArmorImpactAge = -100;
    int airborneTicks;
    int burningTicks;
    int ignitionGrace;
    float charAmount;
    int supportTicks;
    int supportMissTicks;
    final List<RigidBruise> bruises = new ArrayList<>();
    final Map<Long, PersistentContact> contacts = new LinkedHashMap<>();

    enum JointType { ROOT, CONE_TWIST, HINGE, CLOTH, GRIP }

    static final class PersistentContact {
        Vec3 point;
        Vec3 normal;
        int stableSteps;
        int lastAge;

        PersistentContact(Vec3 point, Vec3 normal, int age) {
            this.point = point;
            this.normal = normal;
            this.lastAge = age;
            this.stableSteps = 1;
        }
    }

    RigidBodyPiece(int entityId, int region, int parentRegion, boolean playerBody,
                   Vec3 position, Vec3 velocity,
                   Vec3 angularVelocity, Vec3 halfExtents, double partMass, float jointLength,
                   Identifier texture, int bloodRgb, float[][] faceUvs, float[][] overlayFaceUvs,
                   Quaternionf initialOrientation) {
        this.entityId = entityId;
        this.region = region;
        this.parentRegion = parentRegion;
        this.playerBody = playerBody;
        this.previous = position;
        this.position = position;
        this.velocity = velocity;
        this.angularVelocity = angularVelocity;
        this.halfExtents = halfExtents;
        this.partMass = Math.max(0.012, partMass);
        this.jointLength = jointLength;
        this.texture = texture;
        this.bloodRgb = bloodRgb;
        this.faceUvs = faceUvs;
        this.overlayFaceUvs = overlayFaceUvs;
        this.orientation.set(initialOrientation);
        this.previousOrientation.set(initialOrientation);
        this.physicsBlend = playerBody ? 0.0f : 1.0f;
    }

    double radius() { return Math.max(halfExtents.x, Math.max(halfExtents.y, halfExtents.z)); }
    double mass() { return partMass; }
    double inverseMass() { return 1.0 / mass(); }

    Vec3 inverseInertia(Vec3 worldTorque) {
        Vector3f local = new Vector3f((float) worldTorque.x, (float) worldTorque.y,
                (float) worldTorque.z);
        new Quaternionf(orientation).conjugate().transform(local);
        double m = mass();
        double ix = Math.max(0.002, m / 3.0
                * (halfExtents.y * halfExtents.y + halfExtents.z * halfExtents.z));
        double iy = Math.max(0.002, m / 3.0
                * (halfExtents.x * halfExtents.x + halfExtents.z * halfExtents.z));
        double iz = Math.max(0.002, m / 3.0
                * (halfExtents.x * halfExtents.x + halfExtents.y * halfExtents.y));
        local.set((float) (local.x / ix), (float) (local.y / iy), (float) (local.z / iz));
        orientation.transform(local);
        return new Vec3(local.x, local.y, local.z);
    }

    Vec3 velocityAt(Vec3 worldPoint) {
        return velocity.add(angularVelocity.cross(worldPoint.subtract(position)));
    }

    void applyImpulse(Vec3 worldPoint, Vec3 impulse) {
        velocity = velocity.add(impulse.scale(inverseMass()));
        angularVelocity = angularVelocity.add(inverseInertia(
                worldPoint.subtract(position).cross(impulse)));
    }
}

