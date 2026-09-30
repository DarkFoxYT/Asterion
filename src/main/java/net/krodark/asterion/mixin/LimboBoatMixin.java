package net.krodark.asterion.mixin;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.update.underworld.world.*;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Changes buoyancy only: vanilla rowing, acceleration, turning and drag remain authoritative. */
@Mixin(AbstractBoat.class)
public abstract class LimboBoatMixin implements LimboBoatPose {
    @Unique private double asterion$surface = Double.NaN;
    @Unique private double asterion$heave, asterion$target;
    @Unique private float asterion$pitch, asterion$roll, asterion$oldPitch, asterion$oldRoll;
    @Override public float limboPitch(float partial) { return Mth.lerp(partial, asterion$oldPitch, asterion$pitch); }
    @Override public float limboRoll(float partial) { return Mth.lerp(partial, asterion$oldRoll, asterion$roll); }

    @Inject(method = "getStatus", at = @At("HEAD"), cancellable = true)
    private void asterion$waveContact(CallbackInfoReturnable<AbstractBoat.Status> cir) {
        var boat = (AbstractBoat)(Object)this;
        asterion$surface = Double.NaN;
        asterion$oldPitch = asterion$pitch; asterion$oldRoll = asterion$roll;
        if (!boat.level().dimension().equals(Asterion.LIMBO_LEVEL)) {
            asterion$pitch = 0; asterion$roll = 0; return;
        }
        double surface = UnderworldWaterPhysics.surfaceAt(boat, boat.level().getGameTime());
        if (!Double.isFinite(surface) || Math.abs(boat.getY() - surface) > 5
                || boat.level().getBlockState(boat.blockPosition().below()).isSolidRender()) return;
        asterion$surface = surface;
        double yaw = Math.toRadians(boat.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
        double time = boat.level().getGameTime();
        double shore = WaterShoreline.sample(boat.level(), (int)Math.floor(boat.getX()), (int)Math.floor(boat.getZ()));
        double bow = UnderworldTerrain.waveHeight(boat.getX()+fx*1.1, boat.getZ()+fz*1.1, time);
        double stern = UnderworldTerrain.waveHeight(boat.getX()-fx*1.1, boat.getZ()-fz*1.1, time);
        double port = UnderworldTerrain.waveHeight(boat.getX()-sx*.55, boat.getZ()-sz*.55, time);
        double starboard = UnderworldTerrain.waveHeight(boat.getX()+sx*.55, boat.getZ()+sz*.55, time);
        double center = UnderworldTerrain.waveHeight(boat.getX(), boat.getZ(), time);
        asterion$target = asterion$surface - center*shore + (center*2+bow+stern+port+starboard)*shore/6 - .12;
        asterion$pitch = Mth.lerp(.36F, asterion$pitch, (float)Math.clamp(Math.toDegrees(Math.atan2((bow-stern)*shore,2.2))+boat.getDeltaMovement().y*20, -30,25));
        asterion$roll = Mth.lerp(.28F, asterion$roll, (float)Math.clamp(Math.toDegrees(Math.atan2((port-starboard)*shore,1.1)), -22,22));

        // Avoid vanilla's flat-water status changes/ejection on a rising swell.
        cir.setReturnValue(AbstractBoat.Status.IN_WATER);
    }
    @Inject(method = "getWaterLevelAbove", at = @At("HEAD"), cancellable = true)
    private void asterion$waveLevel(CallbackInfoReturnable<Float> cir) {
        if (Double.isFinite(asterion$surface)) cir.setReturnValue((float)asterion$surface);
    }
    @Inject(method = "floatBoat", at = @At("RETURN"))
    private void asterion$buoyancy(CallbackInfo ci) {
        var boat = (AbstractBoat)(Object)this;
        if (!Double.isFinite(asterion$surface)) {
            asterion$heave = boat.getDeltaMovement().y;
            asterion$pitch *= .8F; asterion$roll *= .8F; return;
        }
        double error = asterion$target - boat.getY();
        if (error < -.65) asterion$heave = Math.max(-.72, asterion$heave - .042);
        else asterion$heave = Math.clamp(asterion$heave + error*(error<0?.17:.11)
                - asterion$heave*(asterion$heave<0?.22:.36), -.38, .27);
        Vec3 motion = boat.getDeltaMovement();
        boat.setDeltaMovement(motion.x, asterion$heave, motion.z);
        boat.resetFallDistance();

    }
}
