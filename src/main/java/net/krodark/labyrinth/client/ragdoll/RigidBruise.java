package net.krodark.labyrinth.client.ragdoll;

import net.minecraft.world.phys.Vec3;

record RigidBruise(Vec3 localPosition, Vec3 localNormal, float severity, int seed, int createdAge) { }

