package net.krodark.asterion.game;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.*;
import net.krodark.asterion.entity.CursedBrazierEntity;
import net.krodark.asterion.network.IgniteGasPayload;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class GameplayContent {
    public static final Block EXPLOSIVE_SPAWNER = block("explosive_spawner", p -> new ChallengeSpawnerBlock(true, p));
    public static final Block REWARD_SPAWNER = block("reward_spawner", p -> new ChallengeSpawnerBlock(false, p));
    public static final BlockEntityType<ChallengeSpawnerBlockEntity> CHALLENGE_SPAWNER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Asterion.id("challenge_spawner"), FabricBlockEntityTypeBuilder.create(ChallengeSpawnerBlockEntity::new, EXPLOSIVE_SPAWNER, REWARD_SPAWNER).build());
    public static final Block SPEWER = block("spewer", p -> new TimedTrapBlock(true, p));
    public static final Block FIRE_BURST_TRAP = block("fire_burst_trap", p -> new TimedTrapBlock(false, p));
    public static final BlockEntityType<TimedTrapBlockEntity> TRAP_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Asterion.id("timed_trap"), FabricBlockEntityTypeBuilder.create(TimedTrapBlockEntity::new, SPEWER, FIRE_BURST_TRAP).build());
    public static final Item FLAMETHROWER = item("flamethrower", p -> new FlamethrowerItem(p.durability(512)));
    private static final ResourceKey<EntityType<?>> CURSED_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Asterion.id("cursed_brazier"));
    public static final EntityType<CursedBrazierEntity> CURSED_BRAZIER = Registry.register(BuiltInRegistries.ENTITY_TYPE, CURSED_KEY,
            EntityType.Builder.of(CursedBrazierEntity::new, MobCategory.MONSTER)
                    .sized(4.8F, 4.85F)
                    .fireImmune()
                    .clientTrackingRange(12)
                    .build(CURSED_KEY));
    public static final Item CURSED_BRAZIER_EGG = item("cursed_brazier_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(CURSED_BRAZIER)));
    public static final Item CURSED_BRAZIER_KEY = item("cursed_brazier_key",
            p -> new Item(p.stacksTo(1).rarity(Rarity.RARE).fireResistant()));
    public static final Item RUNE_BEETLE_EGG = item("rune_beetle_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(Asterion.RUNE_BEETLE)));
    private GameplayContent() { }
    private static Block block(String name, java.util.function.Function<BlockBehaviour.Properties, Block> factory) {
        var key = ResourceKey.create(Registries.BLOCK, Asterion.id(name));
        Block block = Registry.register(BuiltInRegistries.BLOCK, key,
                factory.apply(BlockBehaviour.Properties.of().setId(key).strength(4, 1200).sound(net.minecraft.world.level.block.SoundType.METAL)));
        item(name, p -> new BlockItem(block, p)); return block;
    }
    private static Item item(String name, java.util.function.Function<Item.Properties, Item> factory) {
        var key = ResourceKey.create(Registries.ITEM, Asterion.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
    }
    public static void initialize() {
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, damage) -> {
            if (entity.entityTags().contains(ChallengeDeaths.TAG) && entity.level() instanceof net.minecraft.server.level.ServerLevel level)
                ChallengeDeaths.get(level).record(entity.getUUID());
        });
        FabricDefaultAttributeRegistry.register(CURSED_BRAZIER, CursedBrazierEntity.createAttributes());
        CreativeModeTabEvents.modifyOutputEvent(ResourceKey.create(Registries.CREATIVE_MODE_TAB, Asterion.id("asterion"))).register(output -> {
            output.accept(SPEWER); output.accept(FIRE_BURST_TRAP);
            output.accept(EXPLOSIVE_SPAWNER); output.accept(REWARD_SPAWNER);
            output.accept(FLAMETHROWER); output.accept(CURSED_BRAZIER_EGG);
            output.accept(CURSED_BRAZIER_KEY); output.accept(RUNE_BEETLE_EGG);
        });
        PayloadTypeRegistry.serverboundPlay().register(IgniteGasPayload.TYPE, IgniteGasPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(IgniteGasPayload.TYPE, (payload, context) -> context.server().execute(() -> FlamethrowerItem.ignite(context.player())));
        ServerTickEvents.END_SERVER_TICK.register(GasClouds::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            GasClouds.clear(); net.krodark.asterion.worldgen.ZoneRunePlacement.clear();
        });
    }
}
