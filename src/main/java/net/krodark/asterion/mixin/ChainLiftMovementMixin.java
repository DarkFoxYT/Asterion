package net.krodark.asterion.mixin;

import net.krodark.asterion.entity.ChainLiftEntity;
import net.krodark.asterion.update.underworld.entity.CharonsFerryEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

 
@Mixin(Entity.class)
public abstract class ChainLiftMovementMixin {
    @Unique private ChainLiftEntity asterion$supportingLift;
    @Unique private CharonsFerryEntity asterion$supportingFerry;

    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 asterion$moveOnDeck(Vec3 movement) {
        asterion$supportingLift = null;
        asterion$supportingFerry = null;
        Entity entity = (Entity)(Object)this;
        if (!(entity instanceof Player) || entity.noPhysics
                || entity.getDeltaMovement().y > .08 || movement.y > .65) return movement;
        ChainLiftEntity lift = ChainLiftEntity.supporting(entity);
        if (lift == null) {
            CharonsFerryEntity ferry = CharonsFerryEntity.supporting(entity);
            if (ferry == null || !ferry.overlapsDeck(entity.getBoundingBox().move(movement.x, 0, movement.z)))
                return movement;
            asterion$supportingFerry = ferry;
            entity.setPos(entity.getX(), ferry.deckY(), entity.getZ());
            return new Vec3(movement.x, 0, movement.z);
        }
         
        if (!lift.overlapsDeck(entity.getBoundingBox().move(movement.x, 0, movement.z))) return movement;
        asterion$supportingLift = lift;
        entity.setPos(entity.getX(), lift.getY() + .5, entity.getZ());
        return new Vec3(movement.x, 0, movement.z);
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void asterion$standOnDeck(MoverType type, Vec3 movement, CallbackInfo ci) {
        ChainLiftEntity lift = asterion$supportingLift;
        asterion$supportingLift = null;
        CharonsFerryEntity ferry = asterion$supportingFerry;
        asterion$supportingFerry = null;
        if (lift == null && ferry == null) return;
        Entity entity = (Entity)(Object)this;
        if (lift != null && !lift.overlapsDeck(entity.getBoundingBox())
                || ferry != null && !ferry.overlapsDeck(entity.getBoundingBox())) return;
        entity.verticalCollision = true;
        entity.verticalCollisionBelow = true;
        entity.setOnGround(true);
        entity.resetFallDistance();
        if (entity.getDeltaMovement().y < 0) entity.setDeltaMovement(entity.getDeltaMovement().multiply(1, 0, 1));
    }
}
