package net.krodark.asterion.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.MinotaurEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Operator-only, per-player sandbox sessions; telemetry describes actual state, never inferred thoughts. */
public final class MinotaurDebugCommands {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private MinotaurDebugCommands() { }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
            Commands.literal("asterion").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("minotaur")
                    .then(Commands.literal("debug").executes(c -> start(c.getSource())))
                    .then(Commands.literal("attack").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, builder) -> { MinotaurEntity.debugAttackNames().stream()
                            .filter(n -> n.startsWith(builder.getRemaining())).forEach(builder::suggest); return builder.buildFuture(); })
                        .executes(c -> control(c.getSource(), StringArgumentType.getString(c, "name")))))
                    .then(Commands.literal("pause").executes(c -> control(c.getSource(), "pause")))
                    .then(Commands.literal("auto").executes(c -> control(c.getSource(), "auto")))
                    .then(Commands.literal("status").executes(c -> control(c.getSource(), "status")))
                    .then(Commands.literal("pillars")
                        .then(Commands.literal("destroy_all").executes(c -> destroyPillars(c.getSource()))))
                    .then(Commands.literal("stop").executes(c -> control(c.getSource(), "stop"))))));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 10 != 0) return;
            for (var it = SESSIONS.entrySet().iterator(); it.hasNext();) {
                var entry = it.next(); Session session = entry.getValue();
                ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
                if (owner == null || owner.level() != session.boss.level() || session.boss.isRemoved()) {
                    if (session.owned) session.boss.stopDebug(); it.remove();
                    if (owner != null) owner.sendSystemMessage(Component.literal("[Minotaur debug] Session ended."));
                    continue;
                }
                String key = session.boss.debugStateKey();
                if (!key.equals(session.lastKey) || server.getTickCount() - session.lastReport >= 40) {
                    owner.sendSystemMessage(Component.literal("[Minotaur debug] " + session.boss.debugStatus()));
                    session.lastKey = key; session.lastReport = server.getTickCount();
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Session session : SESSIONS.values()) if (session.owned) session.boss.stopDebug();
            SESSIONS.clear();
        });
    }
    private static int start(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer owner = source.getPlayerOrException();
        Session old = SESSIONS.get(owner.getUUID());
        if (old != null && !old.boss.isRemoved()) {
            if (old.owned) old.boss.setDebugRunning(true);
            return 1;
        }
        if (owner.level().dimension().equals(Asterion.ASTERION_LEVEL)) {
            var boss = owner.level().getEntitiesOfClass(MinotaurEntity.class, owner.getBoundingBox().inflate(96),
                    entity -> entity.isAlive() && entity.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS)
                    .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(owner))).orElse(null);
            if (boss == null) {
                source.sendFailure(Component.literal("No active arena Minotaur nearby."));
                return 0;
            }
            SESSIONS.put(owner.getUUID(), new Session(boss, false));
            source.sendSuccess(() -> Component.literal(
                    "[Minotaur debug] Watching the arena boss. Use status to inspect it or stop to leave."), false);
            return 1;
        }
        if (!owner.level().dimension().equals(Level.OVERWORLD)) {
            source.sendFailure(Component.literal(
                    "Use debug in the Overworld or during the maze arena fight."));
            return 0;
        }
        var level = owner.level();
        var boss = Asterion.MINOTAUR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        if (boss == null) return 0;
        Vec3 forward = Vec3.directionFromRotation(0, owner.getYRot());
        Vec3 across = new Vec3(forward.z, 0, -forward.x);
        Vec3 spawn = null;
        for (int distance : new int[]{12, 18, 24}) for (int side : new int[]{0, -6, 6}) {
            if (spawn != null) break;
            Vec3 point = owner.position().add(forward.scale(distance)).add(across.scale(side));
            BlockPos pos = BlockPos.containing(point);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            boss.setPos(point.x, surface, point.z);
            if (level.noCollision(boss) && !level.getBlockState(BlockPos.containing(boss.position()).below())
                    .getCollisionShape(level, BlockPos.containing(boss.position()).below()).isEmpty()) spawn = boss.position();
        }
        if (spawn == null) {
            source.sendFailure(Component.literal("No clear space ahead for the Minotaur. Try an open area."));
            return 0;
        }
        boss.setPos(spawn);
        boss.beginDebug(owner);
        if (!level.addFreshEntity(boss)) return 0;
        SESSIONS.put(owner.getUUID(), new Session(boss, true));
        source.sendSuccess(() -> Component.literal("[Minotaur debug] Active. Attacks can hurt: use Creative for safe observation. "
                + "Commands: /asterion minotaur attack <name>, pause, auto, status, stop. No arena construction or finale; the test boss is not saved."), false);
        return 1;
    }
    private static int control(CommandSourceStack source, String action) throws CommandSyntaxException {
        var owner = source.getPlayerOrException(); Session session = SESSIONS.get(owner.getUUID());
        if (session == null || session.boss.isRemoved()) { source.sendFailure(Component.literal("Start with /asterion minotaur debug.")); return 0; }
        if (!session.owned && !action.equals("status") && !action.equals("stop")) {
            source.sendFailure(Component.literal("Arena debug observes the live fight. Use Overworld debug to force or pause attacks.")); return 0;
        }
        switch (action) {
            case "stop" -> { if (session.owned) session.boss.stopDebug(); SESSIONS.remove(owner.getUUID()); }
            case "pause" -> session.boss.setDebugRunning(false);
            case "auto" -> session.boss.setDebugRunning(true);
            case "status" -> { }
            default -> {
                if (!session.boss.forceDebugAttack(owner, action)) {
                    source.sendFailure(Component.literal("Unknown attack or an attack is still running. Use autocomplete, or resume and let it finish.")); return 0;
                }
            }
        }
        source.sendSuccess(() -> Component.literal("[Minotaur debug] " + (action.equals("stop") ? "Stopped." : session.boss.debugStatus())), false);
        return 1;
    }
    private static int destroyPillars(CommandSourceStack source) {
        if (!source.getLevel().dimension().equals(Asterion.ASTERION_LEVEL)) {
            source.sendFailure(Component.literal("Arena pillars only exist in the Asterion dimension."));
            return 0;
        }
        int destroyed = net.krodark.asterion.WorldGenerator.destroyAllBossPillars(source.getLevel());
        if (destroyed == 0) {
            source.sendFailure(Component.literal("No active arena pillars were found."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Minotaur debug] Destroyed " + destroyed
                + " active arena pillar" + (destroyed == 1 ? "." : "s.")), true);
        return destroyed;
    }
    private static final class Session {
        final MinotaurEntity boss;
        final boolean owned;
        String lastKey = "";
        int lastReport;
        Session(MinotaurEntity boss, boolean owned) { this.boss = boss; this.owned = owned; }
    }
}
