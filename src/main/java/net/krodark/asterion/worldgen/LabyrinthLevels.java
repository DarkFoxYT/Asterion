package net.krodark.asterion.worldgen;

/** Canonical vertical layout. Authored NBT files remain local-space and need no resaving. */
public final class LabyrinthLevels {
    public static final int LEGACY_TO_LAYERED_OFFSET = 48;
    public static final int MAZE_FLOOR_Y = 48 + LEGACY_TO_LAYERED_OFFSET;
    public static final int CATACOMB_BASE_Y = 19 + LEGACY_TO_LAYERED_OFFSET;
    public static final int ARENA_BASE_Y = 1 + LEGACY_TO_LAYERED_OFFSET;
    public static final int FORGE_FLOOR_Y = 16;
    public static final int CAVE_BOTTOM_Y = -64;
    public static final int CAVE_ROOF_Y = 13;
    public static final int FORGE_ROOF_Y = ARENA_BASE_Y - 2;

    private LabyrinthLevels() { }
}
