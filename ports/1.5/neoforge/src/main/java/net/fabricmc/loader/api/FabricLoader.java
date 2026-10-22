package net.fabricmc.loader.api;

import java.nio.file.Path;
import java.util.Optional;

/** Read-only loader queries used by the bundled renderer; backed by NeoForge. */
public interface FabricLoader {
    static FabricLoader getInstance() {
        return Instance.VALUE;
    }

    boolean isModLoaded(String id);

    boolean isDevelopmentEnvironment();

    Path getGameDir();

    Path getConfigDir();

    Optional<ModContainer> getModContainer(String id);

    java.util.Collection<ModContainer> getAllMods();

    final class Instance implements FabricLoader {
        private static final FabricLoader VALUE = new Instance();

        public boolean isModLoaded(String id) {
            return net.neoforged.fml.ModList.get().isLoaded(id);
        }

        // Runtime shader source watching is intentionally disabled in packaged builds.
        public boolean isDevelopmentEnvironment() {
            return false;
        }

        public Path getGameDir() {
            return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        }

        public Path getConfigDir() {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        }

        public Optional<ModContainer> getModContainer(String id) {
            return net.neoforged.fml.ModList.get()
                    .getModContainerById(id)
                    .map(
                            mod ->
                                    new ModContainer() {
                                        public net.fabricmc.loader.api.metadata.ModMetadata
                                                getMetadata() {
                                            return new net.fabricmc.loader.api.metadata
                                                    .ModMetadata() {
                                                public String getId() {
                                                    return mod.getModId();
                                                }

                                                public String getName() {
                                                    return mod.getModInfo().getDisplayName();
                                                }
                                            };
                                        }
                                    });
        }

        public java.util.Collection<ModContainer> getAllMods() {
            return net.neoforged.fml.ModList.get().getMods().stream()
                    .map(info -> getModContainer(info.getModId()).orElseThrow())
                    .toList();
        }
    }
}
