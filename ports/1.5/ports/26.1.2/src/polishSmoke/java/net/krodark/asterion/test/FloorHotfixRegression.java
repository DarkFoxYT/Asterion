package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.entity.AncientSkeletonEntity;
import net.krodark.asterion.event.DeadSunEventSystem;
import net.krodark.asterion.worldgen.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.*;
import java.util.List;

/** Full-world regressions; never included in release artifacts. */
final class FloorHotfixRegression {
    static volatile boolean serverPassed;
    static volatile Throwable failure;
    private static int corpseId, corpseTicks;
    private static Object engine;
    private static Class<?> engineType;
    private static org.lwjgl.opengl.GLDebugMessageCallback graphicsCallback;
    private static int graphicsErrors;

    static void prepare(ServerPlayer player) {
        try {
            var level = player.level().getServer().getLevel(Asterion.ASTERION_LEVEL);
            var source = player.level().getServer().createCommandSourceStack();
            player.level().getServer().getCommands().performPrefixedCommand(source,
                    "execute in asterion:labyrinth run tp @a 85 100 85");
            player.setNoGravity(true);
            net.krodark.asterion.AsterionConfig.INSTANCE.wallZapDelayTicks = Integer.MAX_VALUE;
            Method exposed = DeadSunEventSystem.class.getDeclaredMethod("eclipseHunterExposed", ServerPlayer.class);
            exposed.setAccessible(true);
            for (int y : new int[]{20, 70, 95, 100}) {
                player.setPos(85.5, y, 85.5);
                require((boolean) exposed.invoke(null, player) == (y == 100), "Wrong eclipse exposure at Y=" + y);
                if (y != 100) require(!DeadSunEventSystem.trigger(level, DeadSunEventSystem.ECLIPSE),
                        "Eclipse started with only an underground player");
            }
            player.setPos(85.5, 100, 85.5);
            require(DeadSunEventSystem.trigger(level, DeadSunEventSystem.ECLIPSE), "Surface eclipse refused to start");
            Field statesField = DeadSunEventSystem.class.getDeclaredField("STATES"); statesField.setAccessible(true);
            Object state = ((java.util.Map<?, ?>)statesField.get(null)).get(level.getServer());
            Object event = field(state, "active");
            Method sync = DeadSunEventSystem.class.getDeclaredMethod("syncNewPlayers", level.getClass(), event.getClass());
            sync.setAccessible(true);
            java.util.Set<?> notified = (java.util.Set<?>)field(event, "notifiedPlayers");
            require(notified.contains(player.getUUID()), "Surface player missed eclipse start");
            player.setPos(85.5, 70, 85.5); sync.invoke(null, level, event);
            require(!notified.contains(player.getUUID()), "Descending player kept eclipse effects");
            player.setPos(85.5, 100, 85.5); sync.invoke(null, level, event);
            require(notified.contains(player.getUUID()), "Returning player failed to rejoin eclipse");
            DeadSunEventSystem.finishEclipse(level);
            for (long seed = 0; seed < 128; seed++) for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                int x = 3 + dx * 12, z = 4 + dz * 12;
                if (CatacombLayout.reserved(x, z)) continue;
                require(ForgeDepths.isStairModule(x, z), "Stair fixture drifted");
                require(CatacombLayout.connected(seed, x, z, Direction.EAST), "Disconnected Forge stair " + x + "," + z);
                require(CatacombLayout.connected(seed, x + 1, z, Direction.WEST), "One-way Forge connection");
                require((AuthoredCatacombs.module(seed, x + 1, z).exits() & 8) != 0, "Crypt seals the Forge entrance");
            }
            // Both halves must be placed independently and must not write into their neighbour.
            for (Direction facing : List.of(MinotaurArenaEntrances.PLAYER_ENTRANCE, MinotaurArenaEntrances.BOSS_ENTRANCE)) {
                BlockPos gate = facing == MinotaurArenaEntrances.BOSS_ENTRANCE
                        ? MinotaurArenaEntrances.AUTHORED_BOSS_GATE : MinotaurArenaEntrances.gate(facing);
                for (int x = -3; x <= 3; x++) level.setBlock(gate.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 2);
                MinotaurArenaEntrances.buildForChunk(level, new ChunkPos(-1, gate.getZ() >> 4));
                require(level.getBlockState(gate.west()).is(Asterion.MAZESTEEL_GATE), "Negative gate half missing");
                require(level.getBlockState(gate.east()).is(Blocks.STONE), "Gate writes outside its chunk");
                MinotaurArenaEntrances.buildForChunk(level, new ChunkPos(0, gate.getZ() >> 4));
                require(level.getBlockState(gate.east()).is(Asterion.MAZESTEEL_GATE), "Positive gate half missing");
            }
            for (BlockPos pos : BlockPos.betweenClosed(80, 177, 80, 90, 177, 90)) level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
            player.level().getServer().getCommands().performPrefixedCommand(source,
                    "execute in asterion:labyrinth run tp @a 85 178 82");
            serverPassed = true;
            Asterion.LOGGER.info("ASTERION_FLOOR_HOTFIX server PASS: eclipse floor gates, 128 seeds/81 districts, chunk-clipped Minotaur gates");
        } catch (Throwable error) { failure = error; }
    }

    static void client(Minecraft client) {
        if (failure != null) throw new AssertionError("Floor regression failed", failure);
        if (!serverPassed || !client.level.dimension().equals(Asterion.ASTERION_LEVEL)) return;
        if (!client.level.getBlockState(new BlockPos(85, 177, 85)).is(Blocks.STONE)) return;
        try {
            if (engine == null) {
                verifyPresentation(client);
                org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
                graphicsCallback = org.lwjgl.opengl.GLDebugMessageCallback.create((source, type, id, severity, length, message, user) -> {
                    if (type == org.lwjgl.opengl.GL43.GL_DEBUG_TYPE_ERROR && graphicsErrors++ == 0)
                        Asterion.LOGGER.error("ASTERION_FLOOR_GL " + org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length, message), new Throwable("Graphics call trace"));
                });
                org.lwjgl.opengl.GL43.glDebugMessageCallback(graphicsCallback, 0L);
                engineType = Class.forName("net.krodark.asterion.client.ragdoll.DismembermentEngine");
                engine = engineType.getField("INSTANCE").get(null);
                AncientSkeletonEntity skeleton = new AncientSkeletonEntity(net.krodark.asterion.game.AncientContent.SKELETON, client.level);
                skeleton.setId(-20001);
                skeleton.setPos(85.5, 178, 85.5);
                skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                skeleton.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                skeleton.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                skeleton.setHealth(0);
                corpseId = skeleton.getId();
                boolean created = (boolean)engineType.getMethod("ragdoll", net.minecraft.world.entity.Entity.class,
                        int.class, Vec3.class, Vec3.class, double.class, boolean.class).invoke(engine,
                        skeleton, 1, skeleton.getBoundingBox().getCenter(), Vec3.ZERO, .35, false);
                require(created, "Ancient skeleton refused ragdoll creation");
                skeleton.discard(); // Entity lifetime must not determine corpse/equipment lifetime.
                checkPieces(true);
            }
            if (++corpseTicks == 80) {
                checkPieces(false);
                ageCorpse(599);
            }
            if (corpseTicks == 81) {
                checkPieces(false);
                ageCorpse(2399);
            }
            if (corpseTicks == 82) {
                require(graphicsErrors == 0, "Equipped corpse rendering produced OpenGL errors: " + graphicsErrors);
                require(!(boolean)engineType.getMethod("isRagdolled", int.class).invoke(engine, corpseId),
                        "Expired corpse retained its ragdoll state");
                Asterion.LOGGER.info("ASTERION_FLOOR_HOTFIX client PASS: skeleton fell, equipped corpse survives owner removal and old 30-second limit; two-minute expiry cleans up");
                client.stop();
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void verifyPresentation(Minecraft client) throws ReflectiveOperationException {
        Class<?> presentation = Class.forName("net.krodark.asterion.client.event.DeadSunClientEvents");
        Class<?> payloadType = net.krodark.asterion.network.DeadSunEventPayload.class;
        Method receive = presentation.getMethod("receive", payloadType);
        Method tick = presentation.getMethod("tick", Minecraft.class);
        Field active = presentation.getDeclaredField("active"); active.setAccessible(true);
        Field sound = presentation.getDeclaredField("eclipseSound"); sound.setAccessible(true);
        Vec3 previous = client.player.position();
        var payload = new net.krodark.asterion.network.DeadSunEventPayload(DeadSunEventSystem.ECLIPSE, 72L, 1200, 0, 1F);
        try {
            receive.invoke(null, payload);
            require(active.get(null) != null && sound.get(null) != null, "Surface eclipse presentation missing");
            client.player.setPos(previous.x, 70, previous.z);
            tick.invoke(null, client);
            require(active.get(null) == null && sound.get(null) == null, "Eclipse visuals/audio survived descent");
            receive.invoke(null, payload);
            require(active.get(null) == null && sound.get(null) == null, "Late packet restarted eclipse underground");
        } finally { client.player.setPos(previous.x, previous.y, previous.z); }
    }

    private static void ageCorpse(int age) throws ReflectiveOperationException {
        Method method = engineType.getDeclaredMethod("pieces"); method.setAccessible(true);
        for (Object piece : (List<?>)method.invoke(engine)) if ((int)field(piece, "entityId") == corpseId) {
            Field f = piece.getClass().getDeclaredField("age"); f.setAccessible(true); f.setInt(piece, age);
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    private static void checkPieces(boolean initial) throws ReflectiveOperationException {
        Method method = engineType.getDeclaredMethod("pieces"); method.setAccessible(true);
        List<?> pieces = (List<?>)method.invoke(engine);
        int count = 0, grips = 0;
        boolean fallen = false;
        for (Object piece : pieces) if ((int)field(piece, "entityId") == corpseId) {
            count++;
            require((int)field(piece, "lifetimeTicks") == 2400, "Ancient corpse lost its two-minute lifetime");
            int region = (int)field(piece, "region");
            if (region == 30 || region == 31) { grips++; require(!((ItemStack)field(piece, "heldItem")).isEmpty(), "Lost held item"); }
            if (region == 1) {
                require(((ItemStack)field(piece, "chestEquipment")).is(Items.IRON_CHESTPLATE), "Lost armor snapshot");
                Vec3 position = (Vec3)field(piece, "position");
                fallen = position.y < 178.8;
            }
        }
        require(count == 8 && grips == 2, "Incomplete equipped corpse: " + count + " pieces, " + grips + " grips");
        if (!initial) require(fallen, "Stationary skeleton stayed upright");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
