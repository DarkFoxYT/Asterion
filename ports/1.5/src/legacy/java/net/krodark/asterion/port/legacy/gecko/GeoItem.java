package net.krodark.asterion.port.legacy.gecko;
import java.util.function.*;
import java.util.*;
public interface GeoItem extends software.bernie.geckolib.animatable.GeoItem {
 Map<GeoItem,Supplier<Object>> RENDERERS=Collections.synchronizedMap(new WeakHashMap<>());
 void createGeoRenderer(Consumer<GeoRenderProvider> consumer);
 @Override default void createRenderer(Consumer<Object> consumer){createGeoRenderer(consumer::accept);}
 @Override default Supplier<Object> getRenderProvider(){return RENDERERS.computeIfAbsent(this,software.bernie.geckolib.animatable.GeoItem::makeRenderer);}
 static void registerSyncedAnimatable(software.bernie.geckolib.core.animatable.GeoAnimatable value){software.bernie.geckolib.animatable.GeoItem.registerSyncedAnimatable(value);}
 static long getOrAssignId(net.minecraft.world.item.ItemStack stack,net.minecraft.server.level.ServerLevel level){return software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack,level);}
}
