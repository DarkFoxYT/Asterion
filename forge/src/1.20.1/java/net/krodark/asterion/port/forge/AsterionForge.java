package net.krodark.asterion.port.forge;

import net.krodark.asterion.Asterion;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(Asterion.MOD_ID)
public final class AsterionForge {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    public AsterionForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(AsterionForge::initializeSharedContent);
        modBus.addListener(AsterionForge::registerSpawnPlacements);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void initializeSharedContent(EntityAttributeCreationEvent event) {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        List<MappedRegistry> registries = BuiltInRegistries.REGISTRY.stream()
                .filter(MappedRegistry.class::isInstance)
                .map(MappedRegistry.class::cast)
                .toList();
        registries.forEach(MappedRegistry::unfreeze);
        Asterion.initialize();
    }

    private static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(net.krodark.asterion.game.AncientContent.SKELETON,
                SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.krodark.asterion.entity.AncientSkeletonEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(Asterion.BOMBARDIER_BEETLE, SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(Asterion.CONSTRUCT, SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(Asterion.SCARLET_CENTIPEDE, SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
