package net.krodark.asterion.client.light;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.block.RespawnObelisks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.krodark.asterion.client.ragdoll.DismembermentEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class HeldItemDynamicLights {
    private static final Set<Object> ACTIVE_LIGHTS = new HashSet<>();
    private static final Set<Object> CURRENT_LIGHTS = new HashSet<>();
    private static final Set<UUID> CURRENT_DROPPED_IDS = new HashSet<>();
    private static final Map<UUID, Vec3> DROPPED_POSITIONS = new HashMap<>();
    private static final Map<UUID, HeldLightKey[]> HELD_KEYS = new HashMap<>();
    private static final Map<UUID, DroppedLightKey> DROPPED_KEYS = new HashMap<>();
    private static final Map<Item, LightStyle> BLOCK_LIGHT_STYLES = new IdentityHashMap<>();
    private static final List<ItemEntity> NEARBY_ITEMS = new ArrayList<>();
    private static final LightStyle SOUL_LIGHT = new LightStyle(0.14F, 0.58F, 1.0F, 2.55F, 8.25F, true);
    private static final LightStyle WARM_LIGHT = new LightStyle(1.0F, 0.42F, 0.105F, 3.15F, 9.25F, true);
    private static final LightStyle REDSTONE_LIGHT = new LightStyle(1.0F, 0.055F, 0.025F, 1.45F, 5.0F, false);
    private static final LightStyle SEA_LIGHT = new LightStyle(0.36F, 0.82F, 1.0F, 2.55F, 8.0F, false);
    private static final LightStyle CHARGED_RUNE_LIGHT = new LightStyle(0.48F, 0.88F, 1.0F, 2.1F, 6.5F, false);
    private static final LightStyle NO_LIGHT = new LightStyle(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
    private static long lastRenderNs;
    private static long nextRenderUpdateNs;

    private HeldItemDynamicLights() {
    }

    public static void tick(Minecraft client) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        if (!config.dynamicLightsEnabled || client.level == null || client.player == null) {
            clear();
            return;
        }
        int quality = Math.min(config.dynamicLightQuality,
                net.krodark.asterion.client.PerformanceGovernor.quality());
        int scanInterval = quality == 0 ? 6 : quality == 1 ? 3 : 1;
        if (client.level.getGameTime() % scanInterval != 0L) return;
        double range = lightRange(config, quality);
        double rangeSquared = range * range;

        Set<Object> currentLights = CURRENT_LIGHTS;
        currentLights.clear();
        CURRENT_DROPPED_IDS.clear();
        for (AbstractClientPlayer player : client.level.players()) {
            if (!player.isAlive() || player.distanceToSqr(client.player) > rangeSquared) {
                continue;
            }
            trackHand(player, InteractionHand.MAIN_HAND, currentLights);
            trackHand(player, InteractionHand.OFF_HAND, currentLights);
        }
        NEARBY_ITEMS.clear();
        if (config.droppedItemLights) {
            NEARBY_ITEMS.addAll(client.level.getEntitiesOfClass(ItemEntity.class,
                    client.player.getBoundingBox().inflate(range), ItemEntity::isAlive));
            for (ItemEntity item : NEARBY_ITEMS) {
                if (styleFor(item.getItem()) != null) {
                    UUID id = item.getUUID();
                    CURRENT_DROPPED_IDS.add(id);
                    currentLights.add(droppedKey(id));
                }
            }
        }

        for (Object key : ACTIVE_LIGHTS) {
            if (!currentLights.contains(key)) {
                LedAmneticLight.removeItemGlowLight(key);
            }
        }
        ACTIVE_LIGHTS.clear();
        ACTIVE_LIGHTS.addAll(currentLights);
        DROPPED_POSITIONS.keySet().removeIf(id -> !CURRENT_DROPPED_IDS.contains(id));
        DROPPED_KEYS.keySet().removeIf(id -> !CURRENT_DROPPED_IDS.contains(id));
    }

    private static void trackHand(AbstractClientPlayer player, InteractionHand hand,
                                  Set<Object> currentLights) {
        ItemStack stack = player.getItemInHand(hand);
        LightStyle style = styleFor(stack);
        if (style == null) {
            return;
        }

        HeldLightKey key = heldKey(player.getUUID(), hand);
        currentLights.add(key);
        if (DismembermentEngine.INSTANCE.isRagdolled(player.getId())) {
            boolean physicalRight = hand == InteractionHand.MAIN_HAND
                    ? player.getMainArm() == HumanoidArm.RIGHT : player.getMainArm() != HumanoidArm.RIGHT;
            Vec3 handPosition = DismembermentEngine.INSTANCE.ragdollHandPosition(player.getId(), physicalRight);
            if (handPosition != null) updateAt(key, player, handPosition, style, false);
        } else {
            updateLight(player, hand, player.position(), player.getYRot(), style);
        }
    }

    public static void renderFrame(Minecraft client, float partialTick) {
        AsterionConfig config = AsterionConfig.INSTANCE;
        if (!config.dynamicLightsEnabled || client.level == null || client.player == null) return;
        long now = System.nanoTime();
        int quality = Math.min(config.dynamicLightQuality,
                net.krodark.asterion.client.PerformanceGovernor.quality());
        long interval = quality == 0 ? 50_000_000L : quality == 1 ? 25_000_000L : 12_000_000L;
        if (now < nextRenderUpdateNs) return;
        nextRenderUpdateNs = now + interval;
        float partial = Mth.clamp(partialTick, 0.0F, 1.0F);
        double range = lightRange(config, quality);
        double rangeSquared = range * range;
        float frameSeconds = lastRenderNs == 0L ? 1.0F / 60.0F
                : Mth.clamp((now - lastRenderNs) / 1_000_000_000.0F, 0.0F, 0.1F);
        lastRenderNs = now;
        for (AbstractClientPlayer player : client.level.players()) {
            if (!player.isAlive() || player.distanceToSqr(client.player) > rangeSquared
                    || DismembermentEngine.INSTANCE.isRagdolled(player.getId())) continue;
            Vec3 position = player.getPosition(partial);
            float yaw = Mth.rotLerp(partial, player.yRotO, player.getYRot());
            updateLight(player, InteractionHand.MAIN_HAND, position, yaw,
                    styleFor(player.getMainHandItem()));
            updateLight(player, InteractionHand.OFF_HAND, position, yaw,
                    styleFor(player.getOffhandItem()));
        }
        for (ItemEntity item : NEARBY_ITEMS) {
            if (!item.isAlive()) continue;
            LightStyle style = styleFor(item.getItem());
            if (style == null) continue;
            Vec3 target = item.getPosition(partial).add(0.0D, 0.62D, 0.0D);
            Vec3 previous = DROPPED_POSITIONS.get(item.getUUID());
            double blend = 1.0D - Math.exp(-frameSeconds * 14.0D);
            Vec3 position = previous == null || previous.distanceToSqr(target) > 9.0D
                    ? target : previous.lerp(target, blend);
            DROPPED_POSITIONS.put(item.getUUID(), position);
            updateAt(droppedKey(item.getUUID()), item, position, style, false);
        }
    }

    public static void updateRagdollHand(LivingEntity living, HumanoidArm physicalArm,
                                         ItemStack stack, Vec3 handPosition) {
        LightStyle style = styleFor(stack);
        if (style == null) return;
        InteractionHand hand = physicalArm == living.getMainArm()
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        updateAt(heldKey(living.getUUID(), hand), living,
                handPosition.add(0.0D, 0.08D, 0.0D), style, false);
    }

    private static void updateLight(AbstractClientPlayer player, InteractionHand hand,
                                    Vec3 playerPosition, float playerYaw, LightStyle style) {
        if (style == null) return;

        double side = hand == InteractionHand.MAIN_HAND ? 1.0D : -1.0D;
        if (player.getMainArm() == HumanoidArm.LEFT) {
            side = -side;
        }
        float yaw = (float) Math.toRadians(playerYaw);
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 position = playerPosition
                .add(0.0D, player.getBbHeight() * 0.68D, 0.0D)
                .add(right.scale(side * 0.28D));

        updateAt(heldKey(player.getUUID(), hand), player, position, style, false);
    }

    private static void updateAt(Object key, net.minecraft.world.entity.Entity owner, Vec3 position,
                                 LightStyle style, boolean castsShadow) {
        double time = owner.level().getGameTime()
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double phase = time * 0.22D + owner.getId() * 0.73D
                + (key instanceof HeldLightKey held && held.hand == InteractionHand.OFF_HAND
                ? 1.9D : 0.0D);
        float flicker = style.flickers
                ? (float) (0.972D + Math.sin(phase) * 0.018D
                        + Math.sin(phase * 2.37D) * 0.007D + Math.sin(phase * 0.41D) * 0.006D)
                : 1.0F;
        boolean inAsterion = owner.level().dimension().equals(Asterion.ASTERION_LEVEL);
        float visualIntensity = style.intensity * flicker * (inAsterion ? 0.46F : 1.0F);
        float visualRadius = style.radius * (0.992F + flicker * 0.008F)
                * (inAsterion ? 0.72F : 1.0F);
        int quality = Math.min(AsterionConfig.INSTANCE.dynamicLightQuality,
                net.krodark.asterion.client.PerformanceGovernor.quality());
        boolean softShadow = quality >= 2 && !inAsterion && (castsShadow || style.radius >= 6.5F);
        visualRadius *= quality == 0 ? 0.70F : quality == 1 ? 0.88F : 1.0F;
        LedAmneticLight.updateItemGlowLight(key, position, style.red, style.green, style.blue,
                visualIntensity, visualRadius, softShadow);
    }

    private static HeldLightKey heldKey(UUID playerId, InteractionHand hand) {
        HeldLightKey[] keys = HELD_KEYS.computeIfAbsent(playerId, id -> new HeldLightKey[] {
                new HeldLightKey(id, InteractionHand.MAIN_HAND),
                new HeldLightKey(id, InteractionHand.OFF_HAND)});
        return keys[hand == InteractionHand.MAIN_HAND ? 0 : 1];
    }

    private static DroppedLightKey droppedKey(UUID entityId) {
        return DROPPED_KEYS.computeIfAbsent(entityId, DroppedLightKey::new);
    }

    private static LightStyle styleFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.SOUL_TORCH) || stack.is(Items.SOUL_LANTERN)
                || stack.is(Items.SOUL_CAMPFIRE)) {
            return SOUL_LIGHT;
        }
        if (stack.is(Items.TORCH) || stack.is(Items.LANTERN) || stack.is(Items.CAMPFIRE)) {
            return WARM_LIGHT;
        }
        if (stack.is(Items.REDSTONE_TORCH)) {
            return REDSTONE_LIGHT;
        }
        if (stack.is(Blocks.SEA_LANTERN.asItem())) {
            return SEA_LIGHT;
        }
        if (stack.is(RespawnObelisks.CHARGED_RUNE)) {
            return CHARGED_RUNE_LIGHT;
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            LightStyle cached = BLOCK_LIGHT_STYLES.computeIfAbsent(stack.getItem(), ignored -> {
                int emission = blockItem.getBlock().defaultBlockState().getLightEmission();
                if (emission <= 0) return NO_LIGHT;
                float power = emission / 15.0F;
                return new LightStyle(1.0F, 0.72F, 0.42F,
                        0.85F + power * 1.45F, 3.0F + power * 4.25F, false);
            });
            return cached == NO_LIGHT ? null : cached;
        }
        return null;
    }

    private static double lightRange(AsterionConfig config, int quality) {
        double base = quality == 0 ? 24.0D : quality == 1 ? 40.0D : 56.0D;
        return base * config.dynamicLightRangePercent / 100.0D;
    }

    public static void clear() {
        ACTIVE_LIGHTS.forEach(LedAmneticLight::removeItemGlowLight);
        ACTIVE_LIGHTS.clear();
        CURRENT_LIGHTS.clear();
        CURRENT_DROPPED_IDS.clear();
        DROPPED_POSITIONS.clear();
        HELD_KEYS.clear();
        DROPPED_KEYS.clear();
        NEARBY_ITEMS.clear();
        lastRenderNs = 0L;
        nextRenderUpdateNs = 0L;
    }

    private record HeldLightKey(UUID playerId, InteractionHand hand) {
    }

    private record DroppedLightKey(UUID entityId) {
    }

    private record LightStyle(float red, float green, float blue, float intensity, float radius,
                              boolean flickers) {
    }
}
