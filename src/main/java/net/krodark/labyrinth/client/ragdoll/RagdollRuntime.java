package net.krodark.labyrinth.client.ragdoll;

import net.minecraft.world.phys.Vec3;

final class RagdollRuntime {
    static final RagdollRuntime INSTANCE = new RagdollRuntime();
    final RagdollConfig config = new RagdollConfig();
    private RagdollRuntime() { }
    void emitRigidImpact(Vec3 position, Vec3 normal, Vec3 velocity, float severity, float amount, int color) { }
    void emitAshCloud(Vec3 position, Vec3 velocity, int amount) { }
}
