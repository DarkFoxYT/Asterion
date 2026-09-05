package net.krodark.asterion.mixin;

import net.krodark.asterion.block.WaterloggedDecoration;
import net.minecraft.world.level.block.*;
import org.spongepowered.asm.mixin.Mixin;

/** Preserve small decorations when water occupies the same block. */
@Mixin({TorchBlock.class, MushroomBlock.class, FlowerBlock.class, TallGrassBlock.class,
        DoublePlantBlock.class, SaplingBlock.class, VineBlock.class,
        CarpetBlock.class, FlowerPotBlock.class, SporeBlossomBlock.class, 
        NetherSproutsBlock.class, NetherRootsBlock.class, DryVegetationBlock.class,
        BushBlock.class, SweetBerryBushBlock.class, FireflyBushBlock.class, WebBlock.class})
public abstract class FloodDecorationMixin implements WaterloggedDecoration { }
