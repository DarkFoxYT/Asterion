package net.krodark.asterion.fluid;

import net.krodark.asterion.port.compat.AsterionRegistry;

import net.krodark.asterion.Asterion;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;


public final class HeavyWater {
    public static final int COLOR = 0xFF579FAD;
    public static final HeavyWaterFluid STILL = AsterionRegistry.register(BuiltInRegistries.FLUID,
            Asterion.id("heavy_water"), new HeavyWaterFluid.Source());
    public static final HeavyWaterFluid FLOWING = AsterionRegistry.register(BuiltInRegistries.FLUID,
            Asterion.id("flowing_heavy_water"), new HeavyWaterFluid.Flowing());
    public static final net.minecraft.world.level.block.LiquidBlock WATER_BLOCK = AsterionRegistry.register(BuiltInRegistries.BLOCK,
            Asterion.id("heavy_water"), waterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER).replaceable().noCollission().noLootTable().liquid().strength(100)));
    public static final TidalWaterFluid FLUID = AsterionRegistry.register(BuiltInRegistries.FLUID,
            Asterion.id("heavy_water_layer"), new TidalWaterFluid());
    public static final TidalWaterBlock BLOCK = AsterionRegistry.register(BuiltInRegistries.BLOCK,
            Asterion.id("heavy_water_layer"), new TidalWaterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER).replaceable().noCollission().noOcclusion()
                    .strength(100).noLootTable().liquid()));
    public static final Item BUCKET = AsterionRegistry.register(BuiltInRegistries.ITEM,
            Asterion.id("heavy_water_bucket"), waterBucket(new net.krodark.asterion.port.compat.ItemProperties()
                    .craftRemainder(Items.BUCKET).stacksTo(1)));
    // Forge queues registry writes until RegisterEvent. Its direct constructors
    // look up delegates immediately, before those queued fluids are registered.
    private static net.minecraft.world.level.block.LiquidBlock waterBlock(BlockBehaviour.Properties properties) {
        //? if forge {
        /*return new net.minecraft.world.level.block.LiquidBlock(() -> STILL, properties);
        *///?} else {
        return new net.minecraft.world.level.block.LiquidBlock(STILL, properties);
        //?}
    }
    private static BucketItem waterBucket(Item.Properties properties) {
        //? if forge {
        /*return new BucketItem(() -> STILL, properties);
        *///?} else {
        return new BucketItem(STILL, properties);
        //?}
    }
    private HeavyWater() { }
    public static void initialize() { HeavyWaterlogging.ready = true; }
}
