package net.krodark.labyrinth.client.ragdoll;

import net.minecraft.world.phys.Vec3;

/** A contact-space contusion permanently attached to one rotating rigid piece. */
record RigidBruise(Vec3 localPosition, Vec3 localNormal, float severity, int seed, int createdAge) { }


