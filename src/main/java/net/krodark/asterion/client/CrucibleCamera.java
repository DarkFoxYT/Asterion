package net.krodark.asterion.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlock;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Smooth elevated forge camera that keeps the physical crucible visible behind its HUD. */
public final class CrucibleCamera {
    private static BlockPos crucible;
    private static float blend, previousBlend;
    private static CameraType previousCamera;
    private CrucibleCamera() { }

    public static void begin(BlockPos position) {
        Minecraft client = Minecraft.getInstance();
        crucible = position.immutable();
        if (previousCamera == null) previousCamera = client.options.getCameraType();
        client.options.setCameraType(CameraType.FIRST_PERSON);
    }

    public static void end() { }

    public static void tick(Minecraft client) {
        previousBlend = blend;
        boolean open = client.screen instanceof CrucibleScreen && crucible != null;
        blend += ((open ? 1F : 0F) - blend) * (open ? .22F : .16F);
        if (!open && blend < .01F) {
            blend = previousBlend = 0F;
            crucible = null;
            if (previousCamera != null) client.options.setCameraType(previousCamera);
            previousCamera = null;
        } else if (crucible != null && client.options.getCameraType() != CameraType.FIRST_PERSON) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    public static CameraPose cameraPose(Vec3 vanilla, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (crucible == null || client.level == null) return null;
        float amount = smoother(Mth.lerp(partialTick, previousBlend, blend));
        if (amount <= .001F) return null;
        var state = client.level.getBlockState(crucible);
        Direction facing = state.is(Asterion.CRUCIBLE) ? state.getValue(CrucibleBlock.FACING) : Direction.NORTH;
        Vec3 focus = crucible.getCenter().add(0D, 1.35D, 0D);
        Vec3 desired = focus.add(facing.getStepX() * 5.4D, 4.7D, facing.getStepZ() * 5.4D);
        Vec3 camera = vanilla.lerp(desired, amount);
        Vec3 look = focus.subtract(camera);
        float yaw = (float)(Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float)(-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        return new CameraPose(camera, yaw, pitch);
    }

    private static float smoother(float value) {
        value = Mth.clamp(value, 0F, 1F);
        return value * value * value * (value * (value * 6F - 15F) + 10F);
    }
    public record CameraPose(Vec3 position, float yaw, float pitch) { }
}
