package net.krodark.asterion.mixin;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Loader-neutral access without putting a bridge inside Minecraft's module. */
@Mixin(RenderType.class)
public interface RenderTypeFactory {
    @Invoker("create")
    static RenderType create(String name, RenderSetup setup) { throw new AssertionError(); }
}
