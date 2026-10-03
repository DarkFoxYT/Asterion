package net.krodark.asterion.port.forge;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.port.compat.AsterionRegistry;
import net.krodark.asterion.entity.*;
import net.krodark.asterion.game.AncientContent;
import net.krodark.asterion.game.GameplayContent;
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
import net.minecraftforge.registries.RegisterEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(Asterion.MOD_ID)
public final class AsterionForge {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    public AsterionForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(AsterionForge::initializeSharedContent);
        modBus.addListener(AsterionForge::registerSpawnPlacements);
        modBus.addListener(AsterionForge::registerPending);
        modBus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(AsterionRegistry::runAfterRegistration));
        AsterionRegistry.defer();
        bootstrapSharedContent();
        net.minecraftforge.fml.DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> AsterionLegacyForgeClient::initialize);

    }

    private static void registerPending(RegisterEvent event) {
        for (AsterionRegistry.Pending<?> entry : AsterionRegistry.pending()) {
            registerPendingEntry(event, entry);
        }
    }

    private static <T> void registerPendingEntry(RegisterEvent event, AsterionRegistry.Pending<T> entry) {
        event.register(entry.registryKey(), entry.id(), entry::value);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void initializeSharedContent(EntityAttributeCreationEvent event) {
        bootstrapSharedContent();
        event.put(Asterion.MINOTAUR, MinotaurEntity.createAttributes().build());
        event.put(Asterion.BOMBARDIER_BEETLE, BombadierBeetleEntity.createAttributes().build());
        event.put(Asterion.RUNE_BEETLE, RuneBeetleEntity.createAttributes().build());
        event.put(Asterion.SCARLET_CENTIPEDE, ScarletCentipedeEntity.createAttributes().build());
        event.put(Asterion.CONSTRUCT, ConstructEntity.createAttributes().build());
        event.put(Asterion.QUEEN_BEETLE, QueenBeetleEntity.createAttributes().build());
        event.put(AncientContent.SKELETON, AncientSkeletonEntity.attributes().build());
        event.put(GameplayContent.CURSED_BRAZIER, CursedBrazierEntity.createAttributes().build());
    }

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void bootstrapSharedContent() {
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
