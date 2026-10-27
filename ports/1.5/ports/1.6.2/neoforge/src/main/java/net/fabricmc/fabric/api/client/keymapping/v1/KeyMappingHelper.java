package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;

public final class KeyMappingHelper {
    private static final java.util.List<KeyMapping> KEYS = new java.util.ArrayList<>();

    private KeyMappingHelper() {}

    public static KeyMapping registerKeyMapping(KeyMapping key) {
        KEYS.add(key);
        return key;
    }

    public static void register(
            net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        KEYS.forEach(event::register);
    }
}
