package net.fabricmc.fabric.api.object.builder.v1.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Constructor;
import java.util.Set;

/** Forge implementation for the shared block entity declarations. */
public final class FabricBlockEntityTypeBuilder<T extends BlockEntity> {
    @FunctionalInterface
    public interface Factory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    private final Factory<T> factory;
    private final Set<Block> blocks;

    private FabricBlockEntityTypeBuilder(Factory<T> factory, Block... blocks) {
        this.factory = factory;
        this.blocks = Set.of(blocks);
    }

    public static <T extends BlockEntity> FabricBlockEntityTypeBuilder<T> create(Factory<T> factory, Block... blocks) {
        return new FabricBlockEntityTypeBuilder<>(factory, blocks);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockEntityType<T> build() {
        try {
            Constructor<BlockEntityType> constructor = BlockEntityType.class.getDeclaredConstructor(
                    BlockEntityType.BlockEntitySupplier.class, Set.class);
            constructor.setAccessible(true);
            return constructor.newInstance((BlockEntityType.BlockEntitySupplier<T>) factory::create, blocks);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not create Forge block entity type", error);
        }
    }
}
