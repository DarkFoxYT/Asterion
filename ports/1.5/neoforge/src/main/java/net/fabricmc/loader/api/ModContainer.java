package net.fabricmc.loader.api;

public interface ModContainer {
    net.fabricmc.loader.api.metadata.ModMetadata getMetadata();

    default java.util.Optional<ModContainer> getContainingMod() {
        return java.util.Optional.empty();
    }

    default java.util.List<java.nio.file.Path> getRootPaths() {
        return java.util.List.of();
    }
}
