package net.krodark.asterion.port.forge;

import net.krodark.asterion.Asterion;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.common.ForgeInternalHandler;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraft.server.packs.resources.SimpleReloadInstance;
import net.minecraft.util.Unit;
import java.util.concurrent.CompletableFuture;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(Asterion.MOD_ID)
public final class AsterionForge {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static net.minecraft.server.MinecraftServer lootReloadedFor;

    public AsterionForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(AsterionForge::initializeSharedContent);
        modBus.addListener(AsterionForge::registerSpawnPlacements);
        modBus.addListener(AsterionForge::buildCreativeTab);
        MinecraftForge.EVENT_BUS.addListener(AsterionForge::playerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(AsterionForge::playerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(AsterionForge::startTracking);
        MinecraftForge.EVENT_BUS.addListener(AsterionForge::stopTracking);
        MinecraftForge.EVENT_BUS.addListener(AsterionForge::playerClone);
        MinecraftForge.EVENT_BUS.addListener(AsterionForge::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(AsterionForge::onReloadListeners);
    }

    private static void onReloadListeners(AddReloadListenerEvent event) {
        // Forge's internal handler is not reached by this userdev event bus,
        // leaving ordinary block drops unable to resolve loot modifiers.
        new ForgeInternalHandler().onResourceReload(event);
    }

    private static synchronized void ensureForgeLootManager(net.minecraft.server.MinecraftServer server) {
        if (lootReloadedFor == server) return;
        var resources = server.getServerResources();
        var listeners = ForgeEventFactory.onResourceReload(resources.managers(),
                server.registryAccess(), server.registryAccess());
        SimpleReloadInstance.of(resources.resourceManager(), listeners, Runnable::run,
                Runnable::run, CompletableFuture.completedFuture(Unit.INSTANCE)).done().join();
        lootReloadedFor = server;
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.fire(event.getDispatcher(),
                event.getBuildContext(), event.getCommandSelection());
    }

    private static void playerClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof net.minecraft.server.level.ServerPlayer oldPlayer
                && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer newPlayer)
            net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.fireAfterRespawn(
                    oldPlayer, newPlayer, !event.isWasDeath());
    }

    private static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.fireStart(event.getTarget(), player);
    }

    private static void stopTracking(PlayerEvent.StopTracking event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents.fireStop(event.getTarget(), player);
    }

    private static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            ensureForgeLootManager(player.getServer());
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.fireJoin(
                    player.connection, player.getServer());
        }
    }

    private static void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.fireDisconnect(
                    player.connection, player.getServer());
    }

    private static void buildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.fire(event);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    private static void initializeSharedContent(EntityAttributeCreationEvent event) {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        List<MappedRegistry> registries = BuiltInRegistries.REGISTRY.stream()
                .filter(MappedRegistry.class::isInstance)
                .map(MappedRegistry.class::cast)
                .toList();
        registries.forEach(AsterionForge::unlockRegistry);
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.begin(event);
        try {
            Asterion.initialize();
            if (FMLEnvironment.dist == Dist.CLIENT)
                net.krodark.asterion.port.client.PortClientFeatures.initializeNetworking();
            ForgeNetworking.bootstrap();
        } finally {
            net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.end();
        }
    }

    private static void unlockRegistry(MappedRegistry<?> registry) {
        registry.unfreeze();
        Class<?> type = registry.getClass();
        while (type != null) {
            try {
                var locked = type.getDeclaredField("locked");
                locked.setAccessible(true);
                locked.setBoolean(registry, false);
                try {
                    var delegateField = registry.getClass().getDeclaredField("delegate");
                    delegateField.setAccessible(true);
                    Object delegate = delegateField.get(registry);
                    var unfreeze = delegate.getClass().getMethod("unfreeze");
                    unfreeze.setAccessible(true);
                    unfreeze.invoke(delegate);
                } catch (NoSuchFieldException ignored) {
                    // Vanilla registry: MappedRegistry.unfreeze() above is sufficient.
                }
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Could not unlock Forge registry " + registry.key(), error);
            }
        }
    }

    private static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(net.krodark.asterion.game.AncientContent.SKELETON,
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.krodark.asterion.entity.AncientSkeletonEntity::canSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(Asterion.BOMBARDIER_BEETLE, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(Asterion.CONSTRUCT, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(Asterion.SCARLET_CENTIPEDE, net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
