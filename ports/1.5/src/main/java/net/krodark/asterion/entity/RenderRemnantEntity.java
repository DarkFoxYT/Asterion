package net.krodark.asterion.entity;

import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** A personal, non-interactive apparition. Its encounters are owned by the local client. */
public final class RenderRemnantEntity extends Marker {
    public boolean persistentWitness;
    public int focusTicks, visibleTicks;
    public float appearance;

    public RenderRemnantEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        refreshDimensions();
    }

    @Override public EntityDimensions getDimensions(final Pose pose) { return EntityDimensions.scalable(1.1F, 2.85F); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public void tick() {
        super.tick();
        if (!level().isClientSide()) discard();
    }
}
