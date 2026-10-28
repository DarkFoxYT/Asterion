package net.krodark.asterion.fluid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

 
public final class HeavyWaterFatigue {
    private static final Map<MinecraftServer, Map<UUID, Integer>> EXPOSURE = new WeakHashMap<>();
    public static final int MAX_EXPOSURE = 20 * 60;
    private HeavyWaterFatigue() { }
    public static int fatigueTier(int ticks) { return Math.min(3, ticks / (20 * 20)); }
    public static int nextExposure(int current, boolean swimming) {
        return swimming ? Math.min(MAX_EXPOSURE, current + 1) : Math.max(0, current - 3);
    }
    public static void tick(MinecraftServer server) {
        Map<UUID, Integer> swimmers = EXPOSURE.computeIfAbsent(server, ignored -> new HashMap<>());
        swimmers.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isCreative() || player.isSpectator()) { swimmers.remove(player.getUUID()); continue; }
            boolean swimming = swimmingInHeavyWater(player);
            int exposure = nextExposure(swimmers.getOrDefault(player.getUUID(), 0), swimming);
            if (exposure == 0) { swimmers.remove(player.getUUID()); continue; }
            swimmers.put(player.getUUID(), exposure);
            int tier = fatigueTier(exposure);
            if (tier == 0 || player.tickCount % 20 != 0) continue;
             
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 45, tier - 1, false, false, true));
            if (swimming) {
                player.causeFoodExhaustion(.10F * tier);
                if (tier >= 2) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 45, tier - 2, false, false, true));
            }
        }
    }
    public static boolean touchingHeavyWater(net.minecraft.world.entity.player.Player player) {
        return inHeavyWater(player, false);
    }
    public static boolean swimmingInHeavyWater(net.minecraft.world.entity.player.Player player) {
        return inHeavyWater(player, true);
    }
    private static boolean inHeavyWater(net.minecraft.world.entity.player.Player player, boolean swimming) {
        if (player.isPassenger()) return false;
        var level = player.level();
        // Forge's custom FluidType is separate from vanilla water even when tagged as water.
        if (!player.isInWater()) {
            var feet = level.getFluidState(player.blockPosition()).getType();
            if (feet != HeavyWater.STILL && feet != HeavyWater.FLOWING && feet != HeavyWater.FLUID) return false;
        }
        double deepEnough = Math.min(.8, player.getBbHeight() * .5);
        for (int sample = 0; sample < 3; sample++) {
            double height = sample == 0 ? .1 : sample == 1 ? Math.min(.9, player.getBbHeight() * .5) : player.getEyeHeight();
            if (swimming && player.onGround() && !player.isSwimming() && height < deepEnough) continue;
            BlockPos pos = BlockPos.containing(player.getX(), player.getY() + height, player.getZ());
            var fluid = level.getFluidState(pos);
            if ((fluid.getType() == HeavyWater.STILL || fluid.getType() == HeavyWater.FLOWING || fluid.getType() == HeavyWater.FLUID)
                    && player.getY() + height < pos.getY() + fluid.getHeight(level, pos)) return true;
        }
        return false;
    }
    public static void reset(ServerPlayer player) {
        var swimmers = EXPOSURE.get(player.level().getServer());
        if (swimmers != null) swimmers.remove(player.getUUID());
    }
    public static void clear(MinecraftServer server) { EXPOSURE.remove(server); }
}
