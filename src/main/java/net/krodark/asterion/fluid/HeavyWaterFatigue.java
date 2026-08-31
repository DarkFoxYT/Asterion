package net.krodark.asterion.fluid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Exposure follows the swimmer, not individual liquid blocks. Ordinary water never contributes. */
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
            // Short refreshes let recovery work without removing unrelated potion effects.
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 45, tier - 1, false, false, true));
            if (swimming) {
                player.causeFoodExhaustion(.10F * tier);
                if (tier >= 2) player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, tier - 2, false, false, true));
            }
        }
    }
    public static boolean swimmingInHeavyWater(ServerPlayer player) {
        if (!player.isInWater() || player.isPassenger()
                || player.onGround() && player.getFluidHeight(FluidTags.WATER) < .8 && !player.isSwimming()) return false;
        var level = player.level();
        for (double height : new double[]{.1, Math.min(.9, player.getBbHeight() * .5), player.getEyeHeight()}) {
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
