package net.krodark.asterion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.krodark.asterion.network.ForgeInsertPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

 
public final class ForgeItemFlights {
    private record Flight(Vec3 from, Vec3 to, long tick, ItemStackRenderState item) {}
    private static final ArrayList<Flight> flights = new ArrayList<>();
    private static ClientLevel level;
    private ForgeItemFlights() {}
    public static void tick(Minecraft client) {
        if (level != client.level) { flights.clear(); level = client.level; }
        if (level != null && !flights.isEmpty()) flights.removeIf(f -> level.getGameTime() - f.tick >= 40);
    }
    public static int activeCount() { return flights.size(); }
    public static void receive(ForgeInsertPayload payload) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        if (level != client.level) { flights.clear(); level = client.level; }
        var state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(state, payload.item(), ItemDisplayContext.FIXED, level, null, 0);
        if (flights.size() >= 32) flights.removeFirst();
        flights.add(new Flight(payload.from(), payload.pos().getCenter().add(0, 3.05, 0), level.getGameTime(), state));
    }
    public static void submit(PoseStack poses, LevelRenderState state, SubmitNodeCollector out) {
        var client = Minecraft.getInstance();
        if (level != client.level) { flights.clear(); level = client.level; }
        if (level == null || flights.isEmpty()) return;
        double now = level.getGameTime() + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = state.cameraRenderState.pos;
        for (var flight : flights) {
            double age = now - flight.tick;
            float melt = (float)Math.clamp((age - 22) / 18, 0, 1);
            double t = Math.clamp((now - flight.tick) / 22, 0, 1);
            Vec3 point = flight.from.lerp(flight.to, t).add(0, 4.5 * Math.sin(t * Math.PI), 0).add(0, -melt * .3, 0).subtract(camera);
            poses.pushPose();
            poses.translate(point.x, point.y, point.z);
            float size = .9F * (1 - melt);
            poses.scale(size * (1 + melt * .5F), size * (1 - melt * .85F), size * (1 + melt * .5F));
            poses.mulPose(Axis.YP.rotationDegrees((float)(t * 300)));
            poses.mulPose(Axis.XP.rotationDegrees((float)(t * 220)));
            flight.item.submit(poses, out, 0x00F000F0, OverlayTexture.NO_OVERLAY, 0);
            poses.popPose();
        }
    }
}
