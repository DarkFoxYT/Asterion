package net.krodark.asterion.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.krodark.asterion.Asterion;

 
public final class CinematicDebugCommands {
    private static int previewTicks;
    private CinematicDebugCommands() {}
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            var root = ClientCommands.literal("cinematic");
            for (String name : new String[]{"entry", "brazier", "minotaur", "ending", "stop"})
                root.then(ClientCommands.literal(name).executes(c -> preview(name)));
            dispatcher.register(root);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (previewTicks > 0 && --previewTicks == 0) stop(client);
        });
    }
    private static void stop(Minecraft client) {
        DeadSunEntryCinematic.finish(client);
        if (CursedBrazierCinematic.isActive()) CursedBrazierCinematic.finish(client);
        if (BossEntranceCinematic.isActive()) BossEntranceCinematic.finish(client);
        if (BossFinaleOverlay.isActive()) BossFinaleOverlay.finish(client);
        previewTicks = 0;
    }
    private static int preview(String name) {
        var client = Minecraft.getInstance();
        stop(client);
        if (name.equals("stop")) return 1;
        if (client.player == null || client.level == null) return 0;
        if (!client.level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            client.gui.setOverlayMessage(Component.literal("Run cinematic previews inside Asterion."), false);
            return 0;
        }
        switch (name) {
            case "entry" -> DeadSunEntryCinematic.begin();
            case "minotaur" -> BossEntranceCinematic.receive(new net.krodark.asterion.network.BossEntrancePayload(
                    net.krodark.asterion.worldgen.MinotaurArenaEntrances.BOSS_ENTRANCE, 0,
                    net.krodark.asterion.worldgen.BossArenaEncounter.INTRO_TICKS));
            case "ending" -> BossFinaleOverlay.begin();
            case "brazier" -> {
                var boss = client.level.getEntitiesOfClass(net.krodark.asterion.entity.CursedBrazierEntity.class,
                        client.player.getBoundingBox().inflate(64)).stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(client.player))).orElse(null);
                if (boss == null) {
                    client.gui.setOverlayMessage(Component.literal("Stand within 64 blocks of a Cursed Brazier."), false);
                    return 0;
                }
                CursedBrazierCinematic.receive(new net.krodark.asterion.network.CursedBrazierAwakeningPayload(boss.getId(),112));
            }
        }
        previewTicks = name.equals("ending") ? 280 : 300;
        return 1;
    }
}
