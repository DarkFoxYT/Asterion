package net.krodark.asterion.port.client;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlock;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Smooth world camera used by the 1.21.1 forge interface. */
public final class PortCrucibleCamera {
    private static BlockPos crucible;
    private static float blend;
    private static float previousBlend;
    private static CameraType previousCamera;
    private static boolean closing;

    private PortCrucibleCamera() {}

    public static void begin(BlockPos position) {
        Minecraft client = Minecraft.getInstance();
        crucible = position.immutable();
        closing = false;
        if (previousCamera == null) previousCamera = client.options.getCameraType();
        client.options.setCameraType(CameraType.FIRST_PERSON);
    }

    public static void end() {
        closing = true;
    }

    public static void tick(Minecraft client) {
        previousBlend = blend;
        boolean open = !closing && client.screen instanceof PortCrucibleScreen && crucible != null;
        blend += ((open ? 1.0F : 0.0F) - blend) * (open ? .22F : .18F);
        if (crucible != null && blend > .01F && client.options.getCameraType() != CameraType.FIRST_PERSON)
            client.options.setCameraType(CameraType.FIRST_PERSON);
        if (!open && blend < .01F) clear(client);
    }

    public static CameraPose cameraPose(Vec3 vanilla, float vanillaYaw, float vanillaPitch, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        if (crucible == null || client.level == null) return null;
        float amount = smoother(Mth.lerp(partialTick, previousBlend, blend));
        if (amount <= .001F) return null;
        var state = client.level.getBlockState(crucible);
        Direction facing = state.is(Asterion.CRUCIBLE) ? state.getValue(CrucibleBlock.FACING) : Direction.NORTH;
        Vec3 focus = crucible.getCenter().add(0.0D, 1.35D, 0.0D);
        Vec3 desired = focus.add(facing.getStepX() * 6.35D, 5.45D, facing.getStepZ() * 6.35D);
        Vec3 position = vanilla.lerp(desired, amount);
        Vec3 look = focus.subtract(position);
        float yaw = (float)(Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float)(-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        return new CameraPose(position, Mth.rotLerp(amount, vanillaYaw, yaw),
                Mth.lerp(amount, vanillaPitch, pitch));
    }

    private static float smoother(float value) {
        float x = Mth.clamp(value, 0.0F, 1.0F);
        return x * x * x * (x * (x * 6.0F - 15.0F) + 10.0F);
    }

    private static void clear(Minecraft client) {
        crucible = null;
        closing = false;
        blend = previousBlend = 0.0F;
        if (previousCamera != null) client.options.setCameraType(previousCamera);
        previousCamera = null;
    }

    public record CameraPose(Vec3 position, float yaw, float pitch) {}
}
