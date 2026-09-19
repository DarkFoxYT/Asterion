package net.krodark.asterion.mixin.legacy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.item.Item;
@Mixin(Item.class)
public abstract class ItemDefaultsMixin {
 @Inject(method="<init>",at=@At("RETURN")) private void asterion$defaults(Item.Properties properties,CallbackInfo ci){if(properties instanceof net.krodark.asterion.port.compat.ItemProperties p)net.krodark.asterion.port.legacy.item.ItemDefaults.VALUES.put((Item)(Object)this,java.util.Map.copyOf(p.defaults));}
}
