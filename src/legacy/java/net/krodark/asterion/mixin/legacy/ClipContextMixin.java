package net.krodark.asterion.mixin.legacy;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Backport the nullable ray context used by modern traps and physics queries. */
@Mixin(ClipContext.class)
public abstract class ClipContextMixin {
    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/phys/shapes/CollisionContext;of(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/shapes/CollisionContext;"))
    private CollisionContext asterion$nullableEntity(Entity entity) {
        return entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
    }
}
