package net.krodark.asterion.client.ragdoll;

public final class RagdollConfig {
    public boolean enabled = false;
    public boolean dismemberment = true;
    public boolean modelDeformation = false;
    public boolean rigidImpactBleeding = false;
    public boolean rigidBodySounds = true;
    public float rigidBodySoundVolume = 0.42f;
    public float dismemberForce = 3.1f;
    public int maxRigidBodies = 48;
    public float grabStrength = 0.34f;
    public float grabSmoothing = 0.38f;
    public int ragdollConstraintIterations = 9;
    public boolean ragdollSelfCollision = true;
    public float ragdollRestitution = 1.18f;
    public float ragdollAirRetention = 0.994f;
    public float ragdollGroundFriction = 0.85f;
    public float playerBuoyancy = 1.0f;
    public float playerJointTightness = 0.68f;
    public boolean ragdollManualExit = true;
    public int ragdollMinExitTicks = 40;
    public boolean ragdollGrabBreak = true;
    public float ragdollGrabBreakDistance = 3.4f;
    public float ragdollImpactDamageThreshold = 0.72f;
    public float ragdollImpactDamageMultiplier = 5.2f;
    public float ragdollImpactDamageMax = 18.0f;
    public float red = 0.48f;
    public float green = 0.005f;
    public float blue = 0.008f;

    public void resetDefaults() {
        RagdollConfig defaults = new RagdollConfig();
        try {
            for (java.lang.reflect.Field field : RagdollConfig.class.getFields()) {
                field.set(this, field.get(defaults));
            }
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException("Unable to reset blood-engine defaults", impossible);
        }
    }
}

