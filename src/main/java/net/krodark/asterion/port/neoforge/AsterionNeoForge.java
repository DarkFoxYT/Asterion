package net.krodark.asterion.port.neoforge;

import net.krodark.asterion.Asterion;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import java.util.List;

@Mod(Asterion.MOD_ID)
public final class AsterionNeoForge {
    public AsterionNeoForge(IEventBus modBus, net.neoforged.fml.ModContainer container) {
        modBus.addListener(AsterionNeoForge::initializeAfterNeoForgeRegistries);
        modBus.addListener(AsterionNeoForge::registerSpawnPlacements);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            ClientSetup.registerConfig(container);
        }
    }

    private static final class ClientSetup {
        static void registerConfig(net.neoforged.fml.ModContainer container) {
        net.neoforged.neoforge.client.gui.IConfigScreenFactory configScreenFactory =
                (mod, parent) -> new net.krodark.asterion.port.client.PortAsterionSettingsScreen(parent);
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                configScreenFactory);
        }
    }

    private static void initializeAfterNeoForgeRegistries(EntityAttributeCreationEvent event) {
        initializeFabricStyleRegistries();
    }

    /**
     * Asterion's shared 1.21.1 sources use Fabric's immediate registration model.
     * NeoForge freezes vanilla registries before constructing mods, so reopen the
     * built-in registries for this compatibility pass and bake them again after
     * all shared content has been installed.
     */
    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void initializeFabricStyleRegistries() {
        List<MappedRegistry> registries = BuiltInRegistries.REGISTRY.stream()
                .filter(MappedRegistry.class::isInstance)
                .map(MappedRegistry.class::cast)
                .toList();
        registries.forEach(MappedRegistry::unfreeze);
        try {
            Asterion.initialize();
        } finally {
            registries.forEach(MappedRegistry::freeze);
        }
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(net.krodark.asterion.game.AncientContent.SKELETON,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.krodark.asterion.entity.AncientSkeletonEntity::canSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(Asterion.BOMBARDIER_BEETLE,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(Asterion.CONSTRUCT,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(Asterion.SCARLET_CENTIPEDE,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
