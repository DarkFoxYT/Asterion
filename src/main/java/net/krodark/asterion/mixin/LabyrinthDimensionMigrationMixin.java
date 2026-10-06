package net.krodark.asterion.mixin;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelResource;
import net.krodark.asterion.worldgen.LabyrinthDimensionMigration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class LabyrinthDimensionMigrationMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void asterion$migrate(CallbackInfo callback) {
        LabyrinthDimensionMigration.migrate(((LevelStorageSource.LevelStorageAccess)(Object)this).getLevelPath(LevelResource.ROOT));
    }
}
