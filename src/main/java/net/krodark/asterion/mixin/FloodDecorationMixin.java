package net.krodark.asterion.mixin;

import net.krodark.asterion.block.WaterloggedDecoration;
import net.minecraft.world.level.block.*;
import org.spongepowered.asm.mixin.Mixin;

 
@Mixin({TorchBlock.class, MushroomBlock.class, FlowerBlock.class, TallGrassBlock.class,
        DoublePlantBlock.class, SaplingBlock.class, VineBlock.class,
        CarpetBlock.class, FlowerPotBlock.class, SporeBlossomBlock.class, 
        NetherSproutsBlock.class, BushBlock.class, SweetBerryBushBlock.class, WebBlock.class})
public abstract class FloodDecorationMixin implements WaterloggedDecoration { }
