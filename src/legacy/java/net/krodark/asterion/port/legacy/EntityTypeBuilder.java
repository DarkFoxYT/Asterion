package net.krodark.asterion.port.legacy;
import net.minecraft.world.entity.*;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
/** Uses Fabric's constructor path, which correctly omits vanilla-only data fixers. */
public final class EntityTypeBuilder<T extends Entity> {
 private final FabricEntityTypeBuilder<T> delegate;
 private EntityTypeBuilder(MobCategory category,EntityType.EntityFactory<T> factory) { delegate=FabricEntityTypeBuilder.create(category,factory); }
 public static <T extends Entity> EntityTypeBuilder<T> of(EntityType.EntityFactory<T> factory,MobCategory category) { return new EntityTypeBuilder<>(category,factory); }
 public EntityTypeBuilder<T> sized(float width,float height){delegate.dimensions(EntityDimensions.scalable(width,height));return this;}
 public EntityTypeBuilder<T> clientTrackingRange(int chunks){delegate.trackRangeChunks(chunks);return this;}
 public EntityTypeBuilder<T> updateInterval(int ticks){delegate.trackedUpdateRate(ticks);return this;}
 public EntityTypeBuilder<T> fireImmune(){delegate.fireImmune();return this;}
 public EntityTypeBuilder<T> noSave(){delegate.disableSaving();return this;}
 public EntityTypeBuilder<T> noSummon(){delegate.disableSummon();return this;}
 public EntityTypeBuilder<T> canSpawnFarFromPlayer(){delegate.spawnableFarFromPlayer();return this;}
 public EntityType<T> build(String id){return delegate.build();}
}
