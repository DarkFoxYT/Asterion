package net.fabricmc.fabric.api.resource;

import net.minecraft.server.packs.PackType;

public interface ResourceManagerHelper {
    java.util.Map<PackType, java.util.List<IdentifiableResourceReloadListener>> LISTENERS =
            new java.util.EnumMap<>(PackType.class);

    static ResourceManagerHelper get(PackType type) {
        return listener ->
                LISTENERS
                        .computeIfAbsent(type, ignored -> new java.util.ArrayList<>())
                        .add(listener);
    }

    void registerReloadListener(IdentifiableResourceReloadListener listener);

    static void registerClient(
            net.neoforged.neoforge.client.event.AddClientReloadListenersEvent event) {
        LISTENERS
                .getOrDefault(PackType.CLIENT_RESOURCES, java.util.List.of())
                .forEach(listener -> event.addListener(listener.getFabricId(), listener));
    }
}
