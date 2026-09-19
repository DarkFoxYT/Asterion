package net.krodark.asterion.mixin;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ModelPart.Cube.class)
public interface PortModelCubeAccessor {
    @Accessor("polygons") ModelPart.Polygon[] asterion$polygons();
}
