package net.krodark.asterion.mixin.legacy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.world.item.ItemStack;
@Mixin(ItemStack.class)
public abstract class ItemStackDataMixin {
 @Inject(method="<init>(Lnet/minecraft/world/level/ItemLike;I)V",at=@At("RETURN")) private void asterion$defaults(net.minecraft.world.level.ItemLike item,int count,CallbackInfo ci){net.krodark.asterion.port.legacy.item.ItemDefaults.apply((ItemStack)(Object)this);}
 @Inject(method="getMaxDamage",at=@At("HEAD"),cancellable=true) private void asterion$maxDamage(CallbackInfoReturnable<Integer> ci){var tag=((ItemStack)(Object)this).getTag();if(tag!=null&&tag.contains("asterion:max_damage",3))ci.setReturnValue(Math.max(1,tag.getInt("asterion:max_damage")));}
}
