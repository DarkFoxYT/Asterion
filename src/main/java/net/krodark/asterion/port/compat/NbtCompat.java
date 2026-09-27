package net.krodark.asterion.port.compat;

import net.minecraft.nbt.CompoundTag;

/** Compatibility helpers for the typed NBT readers added after Minecraft 1.21.1. */
public final class NbtCompat {
    private NbtCompat() {}

    public static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key) ? tag.getInt(key) : fallback;
    }

    public static long getLong(CompoundTag tag, String key, long fallback) {
        return tag.contains(key) ? tag.getLong(key) : fallback;
    }

    public static float getFloat(CompoundTag tag, String key, float fallback) {
        return tag.contains(key) ? tag.getFloat(key) : fallback;
    }

    public static double getDouble(CompoundTag tag, String key, double fallback) {
        return tag.contains(key) ? tag.getDouble(key) : fallback;
    }

    public static boolean getBoolean(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    public static String getString(CompoundTag tag, String key, String fallback) {
        return tag.contains(key) ? tag.getString(key) : fallback;
    }
}
