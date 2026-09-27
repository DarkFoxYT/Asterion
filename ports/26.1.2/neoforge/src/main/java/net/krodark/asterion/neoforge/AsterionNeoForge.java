package net.krodark.asterion.neoforge;

import net.krodark.asterion.Asterion;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Native NeoForge bootstrap backed by Forgified Fabric API event adapters. */
@Mod(Asterion.MOD_ID)
public final class AsterionNeoForge {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    public AsterionNeoForge(IEventBus modBus) {
        modBus.addListener(AsterionNeoForge::initializeSharedContent);
        modBus.addListener(AsterionNeoForge::registerSpawnPlacements);
        if (FMLEnvironment.getDist() == Dist.CLIENT) AsterionNeoForgeClient.register(modBus);
    }

    /**
     * The shared sources intentionally use Fabric's immediate registration
     * model. NeoForge freezes built-in registries before mod construction, so
     * run that one compatibility pass from the registry lifecycle. NeoForge
     * performs the final registry bake after all listeners finish.
     */
    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void initializeSharedContent(EntityAttributeCreationEvent event) {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        List<MappedRegistry> registries = BuiltInRegistries.REGISTRY.stream()
                .filter(MappedRegistry.class::isInstance)
                .map(MappedRegistry.class::cast)
                .toList();
        registries.forEach(registry -> registry.unfreeze(false));
        try {
            new Asterion().onInitialize();
            if (FMLEnvironment.getDist() == Dist.CLIENT) AsterionNeoForgeClient.initializeSharedClient();
        } catch (Throwable error) {
            System.err.println("Asterion NeoForge bootstrap failed:");
            error.printStackTrace(System.err);
            throw error;
        }
    }

    private static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(net.krodark.asterion.game.AncientContent.SKELETON,
                SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.krodark.asterion.entity.AncientSkeletonEntity::canSpawn,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(Asterion.BOMBARDIER_BEETLE,
                SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(Asterion.CONSTRUCT,
                SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(Asterion.SCARLET_CENTIPEDE,
                SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mob::checkMobSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
