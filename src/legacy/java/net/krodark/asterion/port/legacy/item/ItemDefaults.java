package net.krodark.asterion.port.legacy.item;
import java.util.*;
import net.minecraft.world.item.*;
public final class ItemDefaults {
 public static final Map<Item,Map<DataComponentType<?>,Object>> VALUES=new IdentityHashMap<>();
 @SuppressWarnings({"unchecked","rawtypes"}) public static void apply(ItemStack stack){var values=VALUES.get(stack.getItem());if(values!=null)values.forEach((key,value)->((DataComponentType)key).write().accept(stack,value));}
}
