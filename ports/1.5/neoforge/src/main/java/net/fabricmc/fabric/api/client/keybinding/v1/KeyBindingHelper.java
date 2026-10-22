package net.fabricmc.fabric.api.client.keybinding.v1;

import net.minecraft.client.KeyMapping;

public final class KeyBindingHelper {
    private static final java.util.List<KeyMapping> KEYS = new java.util.ArrayList<>();

    private KeyBindingHelper() {}

    public static KeyMapping registerKeyBinding(KeyMapping key) {
        KEYS.add(key);
        return key;
    }

    public static void register(
            net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        KEYS.forEach(event::register);
    }
}
