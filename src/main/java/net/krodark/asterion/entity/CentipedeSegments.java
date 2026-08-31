package net.krodark.asterion.entity;

import net.minecraft.util.RandomSource;

/** Shared limits for spawning, saved entities, commands, and the procedural model. */
public final class CentipedeSegments {
    public static final int MIN = 3, MAX = 32;
    public static final int SPAWN_MIN = 5, SPAWN_MAX = 12;
    private CentipedeSegments() {}
    public static int randomCount(RandomSource random) {
        return SPAWN_MIN + random.nextInt(SPAWN_MAX - SPAWN_MIN + 1);
    }
}
