package net.krodark.asterion.mixin.legacy;
@org.spongepowered.asm.mixin.Mixin(net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.class)
public interface LootTableAccessor { @org.spongepowered.asm.mixin.gen.Accessor("lootTable") net.minecraft.resources.ResourceLocation asterion$loot(); }
