package net.krodark.labyrinth.client.ragdoll;

public final class RagdollConfig {
    public boolean enabled = false;
    public boolean youtubeMode = true;
    public float bloodAmount = 1.35f;
    public float bloodVisualScale = 1.25f;
    public boolean affectPlayers = true;
    public boolean blockDecals = true;
    public boolean entityMist = true;
    public int dropletsPerHit = 72;
    public int maxDroplets = 5000;
    public int maxDecals = 12000;
    public float rayDistance = 64.0f;
    public float launchSpeed = 1.15f;
    public float spread = 0.42f;
    public float gravity = 0.055f;
    public float drag = 0.985f;
    public float bounce = 0.18f;
    public float decalSize = 0.16f;
    public float viscosity = 0.28f;
    public float turbulence = 0.18f;
    public int solverSubsteps = 4;
    public int renderBudget = 2600;
    public float projectileSpeed = 7.5f;
    public float penetrationPower = 2.8f;
    public float exitCone = 0.16f;
    public boolean wallDrips = true;
    public boolean ceilingDrips = true;
    public boolean floorPools = false;
    public boolean crownSplashes = true;
    public boolean bruises = false;
    public float bruiseLifetime = 6000.0f;
    public float terminalVelocity = 3.2f;
    public boolean modelBleeding = false;
    public float punchBleedThreshold = 0.78f;
    public float poolMassThreshold = 0.34f;
    public boolean dismemberment = true;
    public boolean modelDeformation = false;
    public boolean rigidImpactBleeding = false;
    public boolean rigidBodySounds = true;
    public float rigidBodySoundVolume = 0.42f;
    public float dismemberForce = 3.1f;
    public int maxRigidBodies = 48;
    public int bleedDuration = 420;
    public boolean bloodyFootprints = true;
    public int footprintLifetime = 700;
    public int maxFootprints = 640;
    public float footprintScale = 1.32f;
    public float grabStrength = 0.34f;
    public float grabSmoothing = 0.38f;
    public int ragdollConstraintIterations = 9;
    public boolean ragdollSelfCollision = true;
    public boolean ragdollDebug = false;
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
    public float dripRate = 0.006f;
    public float maxDripLength = 1.15f;
    public float poolSpreadRate = 0.0045f;
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

