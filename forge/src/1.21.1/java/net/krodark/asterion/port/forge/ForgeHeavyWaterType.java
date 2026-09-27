package net.krodark.asterion.port.forge;

import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Consumer;

/** Shared type for both heavy water forms, using vanilla water sprites and Asterion tint. */
public final class ForgeHeavyWaterType extends FluidType {
    public static final ForgeHeavyWaterType INSTANCE = new ForgeHeavyWaterType();

    private ForgeHeavyWaterType() {
        super(FluidType.Properties.create().descriptionId("block.asterion.heavy_water"));
    }

    @Override public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
        consumer.accept(new IClientFluidTypeExtensions() {
            @Override public ResourceLocation getStillTexture() {
                return ResourceLocation.tryParse("minecraft:block/water_still");
            }
            @Override public ResourceLocation getFlowingTexture() {
                return ResourceLocation.tryParse("minecraft:block/water_flow");
            }
            @Override public int getTintColor() { return 0xFF579FAD; }
        });
    }
}
