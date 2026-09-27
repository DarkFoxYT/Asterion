package net.krodark.asterion.worldgen;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

public final class UnderwaterRuinFeature extends Feature<NoneFeatureConfiguration> {
    public static final BlockPos TEMPLATE_CENTER = new BlockPos(5, 0, 5);

    public UnderwaterRuinFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (context.random().nextInt(AsterionConfig.INSTANCE.underwaterRuinChance) != 0) return false;
        BlockPos center = context.origin();
        if (!context.level().getBlockState(center.above(3)).is(Blocks.WATER)) return false;

        var template = context.level().getLevel().getStructureManager().get(Asterion.id("underwater_ruin"));
        if (template.isEmpty()) {
            Asterion.LOGGER.error("Missing required structure asterion:underwater_ruin");
            return false;
        }

        BlockPos corner = center.subtract(TEMPLATE_CENTER);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.getRandom(context.random()))
                .setRotationPivot(TEMPLATE_CENTER)
                .setLiquidSettings(LiquidSettings.APPLY_WATERLOGGING)
                .setIgnoreEntities(true);
        if (!template.get().placeInWorld(context.level(), corner, corner, settings, context.random(), 2)) return false;

        BlockPos barrelPos = StructurePlaceSettingsTransform.transformBarrel(corner, settings);
        if (context.level().getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel) {
            barrel.setLootTable(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.LOOT_TABLE,
                    Asterion.id("chests/underwater_ruin")));
            barrel.setChanged();
        }
        return true;
    }

    private static final class StructurePlaceSettingsTransform {
        private static BlockPos transformBarrel(BlockPos corner, StructurePlaceSettings settings) {
            return corner.offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
                    .transform(TEMPLATE_CENTER.above(), settings.getMirror(), settings.getRotation(), TEMPLATE_CENTER));
        }
    }
}
