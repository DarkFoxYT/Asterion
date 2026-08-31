package net.krodark.asterion.mixin;

import java.util.Map;
import java.util.function.Function;
import net.krodark.asterion.fluid.HeavyWaterlogging;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only waterloggable blocks gain a property; ordinary solid blocks retain their existing palettes. */
@Mixin(StateDefinition.Builder.class)
public abstract class HeavyWaterStateDefinitionMixin {
    @Shadow @Final private Object owner;
    @Shadow @Final private Map<String, Property<?>> properties;
    @Inject(method = "create", at = @At("HEAD"))
    private void asterion$waterLevels(Function<?, ?> defaultState, StateDefinition.Factory<?, ?> factory,
                                     CallbackInfoReturnable<?> result) {
        if (owner instanceof net.krodark.asterion.block.WaterloggedDecoration)
            properties.put(BlockStateProperties.WATERLOGGED.getName(), BlockStateProperties.WATERLOGGED);
        if (owner instanceof SimpleWaterloggedBlock && properties.containsValue(BlockStateProperties.WATERLOGGED))
            properties.put(HeavyWaterlogging.LEVEL.getName(), HeavyWaterlogging.LEVEL);
    }
}
