package net.krodark.asterion.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.krodark.asterion.network.ForgeInsertPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

/** Restores the item-to-crucible flight effect on the 1.21.1 renderer. */
public final class PortForgeItemFlights {
    private record Flight(Vec3 from, Vec3 to, long started, ItemStack stack) {}
    private static final ArrayList<Flight> FLIGHTS = new ArrayList<>();
    private static ClientLevel world;

    private PortForgeItemFlights() {}

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(PortForgeItemFlights::render);
    }

    public static void receive(ForgeInsertPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (world != client.level) {
            FLIGHTS.clear();
            world = client.level;
        }
        if (FLIGHTS.size() >= 32) FLIGHTS.removeFirst();
        FLIGHTS.add(new Flight(payload.from(), payload.pos().getCenter().add(0.0D, 3.05D, 0.0D),
                world.getGameTime(), payload.item().copy()));
    }

    public static void tick(Minecraft client) {
        if (world != client.level) {
            FLIGHTS.clear();
            world = client.level;
        }
        if (world != null) FLIGHTS.removeIf(flight -> world.getGameTime() - flight.started() >= 40L);
    }

    private static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
        if (world == null || context.world() != world || FLIGHTS.isEmpty()
                || context.matrixStack() == null || context.consumers() == null) return;
        Minecraft client = Minecraft.getInstance();
        Vec3 camera = context.camera().getPosition();
        double now = world.getGameTime() + context.tickCounter().getGameTimeDeltaPartialTick(false);
        PoseStack poses = context.matrixStack();
        for (Flight flight : FLIGHTS) {
            double age = now - flight.started();
            double progress = MthCompat.clamp(age / 22.0D, 0.0D, 1.0D);
            float melt = (float)MthCompat.clamp((age - 22.0D) / 18.0D, 0.0D, 1.0D);
            Vec3 point = flight.from().lerp(flight.to(), progress)
                    .add(0.0D, 4.5D * Math.sin(progress * Math.PI) - melt * .3D, 0.0D)
                    .subtract(camera);
            poses.pushPose();
            poses.translate(point.x, point.y, point.z);
            float size = .9F * (1.0F - melt);
            poses.scale(size * (1.0F + melt * .5F), size * (1.0F - melt * .85F),
                    size * (1.0F + melt * .5F));
            poses.mulPose(Axis.YP.rotationDegrees((float)(progress * 300.0D)));
            poses.mulPose(Axis.XP.rotationDegrees((float)(progress * 220.0D)));
            client.getItemRenderer().renderStatic(flight.stack(), ItemDisplayContext.FIXED,
                    0x00F000F0, OverlayTexture.NO_OVERLAY, poses, context.consumers(), world,
                    flight.stack().hashCode());
            poses.popPose();
        }
    }

    private static final class MthCompat {
        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
