package net.krodark.asterion.port.compat;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Bridges the codec-backed SavedData API introduced after Minecraft 1.21.1. */
public final class SavedDataCompat {
    private static final String VALUE = "value";

    private SavedDataCompat() {
    }

    public static <T extends SavedData> SavedData.Factory<T> factory(Codec<T> codec, Supplier<T> fallback) {
        return new SavedData.Factory<>(fallback,
                (tag, registries) -> load(codec, tag, fallback), DataFixTypes.LEVEL);
    }

    private static <T> T load(Codec<T> codec, CompoundTag root, Supplier<T> fallback) {
        Tag encoded = root.get(VALUE);
        if (encoded == null) return fallback.get();
        return codec.parse(NbtOps.INSTANCE, encoded).resultOrPartial(
                error -> System.err.println("Could not read Asterion saved data: " + error))
                .orElseGet(fallback);
    }

    public static <T> CompoundTag save(Codec<T> codec, T value, CompoundTag root,
                                       HolderLookup.Provider registries) {
        codec.encodeStart(NbtOps.INSTANCE, value).resultOrPartial(
                error -> System.err.println("Could not write Asterion saved data: " + error))
                .ifPresent(tag -> root.put(VALUE, tag));
        return root;
    }
}
