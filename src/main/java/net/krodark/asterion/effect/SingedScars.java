package net.krodark.asterion.effect;

import com.mojang.serialization.Codec;
import net.krodark.asterion.Asterion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.util.datafix.DataFixTypes;
import java.util.HashMap;
import java.util.Map;

/** World-owned scars survive death and dimension changes without depending on copied player NBT. */
public final class SingedScars extends SavedData {
    private static final Codec<SingedScars> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
            .xmap(SingedScars::new, scars -> scars.hearts);
    private static final SavedDataType<SingedScars> TYPE = new SavedDataType<>(Asterion.id("singed_scars"),
            () -> new SingedScars(Map.of()), CODEC, DataFixTypes.LEVEL);
    private final Map<String, Integer> hearts;
    private SingedScars(Map<String, Integer> hearts) { this.hearts = new HashMap<>(hearts); }
    public static SingedScars get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(TYPE); }
    public int lostHearts(ServerPlayer player) { return hearts.getOrDefault(player.getUUID().toString(), 0); }

    public void scar(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || player.getMaxHealth() <= 2) return;
        hearts.put(player.getUUID().toString(), lostHearts(player) + 1);
        setDirty();
        apply(player);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.asterion.singed_heart"));
    }

    public void apply(ServerPlayer player) {
        var health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        var id = Asterion.id("singed_scars");
        int lost = lostHearts(player);
        var previous = health.getModifier(id);
        double unscarred = health.getBaseValue();
        // Never reduce the base maximum below one heart, even after another mod changes it.
        double amount = -Math.min(lost * 2.0, Math.max(0, unscarred - 2));
        if (previous != null && previous.amount() == amount || previous == null && amount == 0) return;
        health.removeModifier(id);
        if (amount < 0) health.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        var scars = get(server);
        for (var player : server.getPlayerList().getPlayers()) scars.apply(player);
    }
}
