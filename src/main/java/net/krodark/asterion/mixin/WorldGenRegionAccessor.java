package net.krodark.asterion.mixin;

import net.minecraft.server.level.WorldGenRegion;
//? if >=1.20.5 {
import net.minecraft.world.level.chunk.status.ChunkStep;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldGenRegion.class)
public interface WorldGenRegionAccessor {
    //? if >=1.20.5 {
    @Accessor("generatingStep") ChunkStep asterion$generatingStep();
    //?} else {
    /*@Accessor("writeRadiusCutoff") int asterion$writeRadius();*/
    //?}
}
