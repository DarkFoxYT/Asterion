package net.krodark.asterion.block;

import net.krodark.asterion.Asterion;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class RespawnObelisks {
    public static final SanctuaryBlock ALTAR = register("respawn_altar", true);
    public static final SanctuaryBlock OBELISK = register("respawn_obelisk", false);
    public static final Item CHARGED_RUNE = Registry.register(BuiltInRegistries.ITEM,
            Asterion.id("charged_respawn_rune"), new Item(new Item.Properties().stacksTo(1)
                    .setId(ResourceKey.create(Registries.ITEM, Asterion.id("charged_respawn_rune")))));
    public static final BlockEntityType<SanctuaryBlockEntity> BLOCK_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Asterion.id("sanctuary"),
            FabricBlockEntityTypeBuilder.create(SanctuaryBlockEntity::new, ALTAR, OBELISK).build());

    private RespawnObelisks() { }
    public static void initialize() { }

    private static SanctuaryBlock register(String name, boolean altar) {
        var id = Asterion.id(name);
        SanctuaryBlock block = Registry.register(BuiltInRegistries.BLOCK, id,
                new SanctuaryBlock(altar, BlockBehaviour.Properties.of()
                        .setId(ResourceKey.create(Registries.BLOCK, id)).strength(4.0F, 12.0F)
                        .noOcclusion().sound(SoundType.DEEPSLATE)
                        .lightLevel(state -> state.getValue(SanctuaryBlock.CHARGE) == 1 ? (altar ? 5 : 11) : 0)));
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block,
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        return block;
    }

    /** Bounded search of loaded terrain only; each altar holds one non-duplicating reward. */
    public static boolean chargeNearest(ServerLevel level, BlockPos origin) {
        BlockPos nearest = nearestUnchargedAltar(level, origin);
        if (nearest == null) return false;
        level.setBlock(nearest, ALTAR.defaultBlockState().setValue(SanctuaryBlock.CHARGE, 1), 3);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                nearest.getX() + .5, nearest.getY() + 1.2, nearest.getZ() + .5, 32, .35, .3, .35, .08);
        return true;
    }

    public static BlockPos nearestUnchargedAltar(ServerLevel level, BlockPos origin) {
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-24, -6, -24), origin.offset(24, 8, 24))) {
            if (!isLoaded(level, pos)) continue;
            var state = level.getBlockState(pos);
            if (!state.is(ALTAR) || state.getValue(SanctuaryBlock.CHARGE) != 0) continue;
            double distance = pos.distSqr(origin);
            if (distance < best) {
                best = distance;
                nearest = pos.immutable();
            }
        }
        return nearest;
    }

    /** Add fixtures alongside existing safe-room markers without replacing floors or decorations. */
    public static void ensureRoomFixtures(ServerLevel level, BlockPos rune) {
        BlockPos marker = null;
        boolean hasAltar = false;
        boolean hasObelisk = false;
        double nearest = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(rune.offset(-10, -4, -10), rune.offset(10, 7, 10))) {
            if (!isLoaded(level, pos)) continue;
            var state = level.getBlockState(pos);
            hasAltar |= state.is(ALTAR);
            hasObelisk |= state.is(OBELISK);
            if (state.is(Blocks.LODESTONE) && pos.distSqr(rune) < nearest) {
                marker = pos.immutable();
                nearest = pos.distSqr(rune);
            }
        }
        if (marker == null || (hasAltar && hasObelisk)) return;
        for (int radius = 2; radius <= 4; radius++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos pos = marker.relative(direction, radius).above();
                if (!isLoaded(level, pos)) continue;
                if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()
                        || !level.getBlockState(pos.below()).isCollisionShapeFullBlock(level, pos.below())) continue;
                if (!hasAltar) {
                    level.setBlock(pos, ALTAR.defaultBlockState(), 3);
                    hasAltar = true;
                } else if (!hasObelisk) {
                    level.setBlock(pos, OBELISK.defaultBlockState(), 3);
                    hasObelisk = true;
                }
                if (hasAltar && hasObelisk) return;
            }
        }
    }

    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
