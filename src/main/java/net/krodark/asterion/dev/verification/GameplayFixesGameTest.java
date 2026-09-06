package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.WorldGenerator;
import net.krodark.asterion.block.*;
import net.krodark.asterion.entity.*;
import net.krodark.asterion.game.*;
import net.krodark.asterion.network.CrucibleControlPayload;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Real registry, level, inventory and entity-load checks for the survival blockers. */
public final class GameplayFixesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(c -> org.lwjgl.glfw.GLFW.glfwHideWindow(c.getWindow().handle()));
        try (var world = context.worldBuilder().create()) {
            // Damage is ignored until the client has acknowledged loading the world.
            context.waitTicks(60);
            world.getServer().runOnServer(server -> {
                AncientBoneCheck.run(server);
                checkRecipes(server);
                checkSpawner(server);
                checkEntrance(server);
                checkShieldBreak(server);
                checkGasCleanup(server);
                for (var track : new MinotaurAnimationTiming.Track[]{MinotaurAnimationTiming.ROAR,
                        MinotaurAnimationTiming.ENTRY_ROAR, MinotaurAnimationTiming.FIRE_ROAR}) {
                    int sound = track.roarSoundTick();
                    require(track.seconds(sound - 1) < 2.75 && track.seconds(sound) >= 2.75, "Roar misses frame 66");
                }
                require(MinotaurAnimationTiming.ENTRY_ROAR.roarSoundTick()
                        + Math.round(6 * 20 / MinotaurAnimationTiming.ENTRY_ROAR_PITCH)
                        == MinotaurAnimationTiming.ENTRY_END_TICK, "Entrance audio and animation end separately");
                checkLift(server);
                checkPortal(server);
                CatacombLootCheck.run(server.overworld());
                Asterion.LOGGER.info("PASS: survival blockers: four-input Bone Steel, gold ore casting, sword components/tags, forge spawner persistence, solid boss entrance, bounded exponential lift and portal alignment");
            });
        }
    }

    private static void checkGasCleanup(MinecraftServer server) {
        var level = server.overworld();
        var owner = java.util.UUID.randomUUID();
        var replacement = java.util.UUID.randomUUID();
        Vec3 origin = new Vec3(100.5, 220, 100.5);
        level.getChunkAt(BlockPos.containing(origin));
        level.setBlock(BlockPos.containing(origin), Blocks.AIR.defaultBlockState(), 18);
        boolean[] damaged = {false};
        var victim = new net.minecraft.world.entity.animal.cow.Cow(net.minecraft.world.entity.EntityType.COW, level) {
            @Override public boolean hurtServer(net.minecraft.server.level.ServerLevel world,
                    net.minecraft.world.damagesource.DamageSource source, float amount) {
                damaged[0] = true;
                // Mirrors owner cleanup from entity death/removal, inside cloud damage processing.
                GasClouds.clearOwner(world, owner);
                GasClouds.emit(world, origin, Vec3.ZERO, replacement);
                return super.hurtServer(world, source, amount);
            }
        };
        victim.setPos(origin); victim.setNoAi(true); level.addFreshEntity(victim);
        GasClouds.clear();
        try {
            GasClouds.emit(level, origin, Vec3.ZERO, owner);
            GasClouds.emit(level, origin, Vec3.ZERO, owner);
            require(GasClouds.ignite(level, origin, owner), "Test clouds did not ignite");
            for (int i = 0; i < 10; i++) GasClouds.tick(server);
            require(damaged[0], "Cloud cleanup callback did not run");
            require(!GasClouds.ignite(level, origin, owner), "Removed owner clouds survived cleanup");
            require(GasClouds.ignite(level, origin, replacement), "Cloud emitted during damage was lost");
            Asterion.LOGGER.info("PASS: gas damage can remove owner clouds and emit replacements without concurrent modification");
        } finally {
            victim.discard(); GasClouds.clear();
        }
    }

    private static void checkShieldBreak(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        require(boss != null, "Missing Minotaur");
        var mode = player.gameMode.getGameModeForPlayer();
        var previous = player.getOffhandItem();
        var difficulty = server.getWorldData().getDifficulty();
        float health = player.getHealth();
        var shield = new ItemStack(net.minecraft.world.item.Items.SHIELD);
        var blocking = shield.get(DataComponents.BLOCKS_ATTACKS);
        // Skip the shield raise delay; exercise the actual server damage/blocking path.
        shield.set(DataComponents.BLOCKS_ATTACKS, new net.minecraft.world.item.component.BlocksAttacks(
                0, blocking.disableCooldownScale(), blocking.damageReductions(), blocking.itemDamage(),
                blocking.bypassedBy(), blocking.blockSound(), blocking.disableSound()));
        try {
            server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shield);
            player.setYRot(0);
            player.setYHeadRot(0);
            boss.setPos(player.getX(), player.getY(), player.getZ() + 3);
            player.invulnerableTime = 0;
            require(player.hurtServer(level, player.damageSources().mobAttack(boss), 1),
                    "Test player is immune to mob damage before shielding; loaded=" + player.connection.hasClientLoaded());
            require(!player.getCooldowns().isOnCooldown(shield), "Unblocked hit disabled shield");
            player.invulnerableTime = 0;
            player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
            require(player.isBlocking(), "Shield was not raised");
            player.hurtServer(level, player.damageSources().mobAttack(boss), 10);
            require(!player.isUsingItem() && player.getCooldowns().isOnCooldown(shield),
                    "Minotaur did not disable the blocking shield");
            require(!shield.isEmpty(), "Guard break destroyed the shield item");
            Asterion.LOGGER.info("PASS: Minotaur hits disable a blocking shield, but not an idle shield");
        } finally {
            player.stopUsingItem();
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, previous);
            player.setGameMode(mode);
            player.setHealth(health);
            player.invulnerableTime = 0;
            boss.discard();
            server.setDifficulty(difficulty, true);
        }
    }

    private static void checkRecipes(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        BlockPos pos = new BlockPos(0, 200, 0);
        try {
            var heat = CrucibleBlockEntity.class.getDeclaredField("temperature"); heat.setAccessible(true);
            for (Item ore : new Item[]{Asterion.CELESTIAL_GOLD_ORE.asItem(), Asterion.SHALE_CELESTIAL_GOLD_ORE.asItem(), Asterion.SHADED_SHALE_CELESTIAL_GOLD_ORE.asItem()}) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 18);
                level.setBlock(pos, Asterion.CRUCIBLE.defaultBlockState(), 18);
                level.setBlock(pos.below(), Blocks.CAMPFIRE.defaultBlockState(), 18);
                var forge = (CrucibleBlockEntity)level.getBlockEntity(pos);
                forge.insert(player, new ItemStack(Asterion.INGOT_CAST));
                require(!forge.insert(player, new ItemStack(ore)), "Cold forge accepted ore");
                heat.setInt(forge, 350);
                CrucibleBlockEntity.tick(level, pos, forge.getBlockState(), forge);
                require(forge.insert(player, new ItemStack(ore)), "Hot forge rejected gold ore");
                forge.control(player, CrucibleControlPayload.POUR);
                var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(5));
                require(drops.stream().filter(e -> e.getItem().is(Asterion.CELESTIAL_GOLD_INGOT))
                        .mapToInt(e -> e.getItem().getCount()).sum() == 1, "Gold recipe returned wrong item/count");
                drops.forEach(net.minecraft.world.entity.Entity::discard);
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        for (Item sword : new Item[]{Asterion.FORGED_SWORD, Asterion.CELESTIAL_BRONZE_SWORD, Asterion.AFTERBLOW, Asterion.SICKENED_TWINBLADES}) {
            ItemStack stack = new ItemStack(sword);
            require(stack.is(ItemTags.SWORDS), "Sword missing sword tag: " + sword);
            require(stack.has(DataComponents.WEAPON) && stack.has(DataComponents.TOOL)
                    && stack.has(DataComponents.ENCHANTABLE), "Sword missing vanilla components: " + sword);
        }
        require(new ItemStack(Asterion.MINOTAUR_KEY_CAST).get(DataComponents.LORE).lines().size() >= 3,
                "Key mold lacks instructions");
    }

    private static void checkSpawner(MinecraftServer server) {
        var level = server.overworld();
        var player = server.getPlayerList().getPlayers().getFirst();
        var previous = player.position();
        var mode = player.gameMode.getGameModeForPlayer();
        BlockPos pos = new BlockPos(20, 200, 20);
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-6, -2, -6), pos.offset(6, 5, 6)))
            level.setBlock(p, p.getY() == 198 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
        level.setBlock(pos, GameplayContent.REWARD_SPAWNER.defaultBlockState(), 18);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getCenter());
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        var spawner = (ChallengeSpawnerBlockEntity)level.getBlockEntity(pos);
        for (int i = 0; i < 10; i++) ChallengeSpawnerBlockEntity.tick(level, pos, spawner.getBlockState(), spawner);
        var mobs = level.getEntitiesOfClass(ConstructEntity.class, new AABB(pos).inflate(8));
        require(!mobs.isEmpty(), "Spawner cannot find a floor below its own elevation");
        var maze = server.getLevel(Asterion.ASTERION_LEVEL);
        require(maze != null, "Missing maze dimension");
        var construct = Asterion.CONSTRUCT.create(maze, EntitySpawnReason.SPAWNER);
        construct.setPos(200, 200, 200);
        construct.addTag(ChallengeDeaths.TAG);
        maze.addFreshEntity(construct);
        require(!construct.isRemoved(), "Challenge construct was deleted by location filter");
        construct.discard(); mobs.forEach(net.minecraft.world.entity.Entity::discard);
        BlockPos forgePos = new BlockPos(200, 20, 200);
        maze.setBlock(forgePos, GameplayContent.REWARD_SPAWNER.defaultBlockState(), 18);
        var oldTag = maze.getBlockEntity(forgePos).saveWithFullMetadata(maze.registryAccess());
        oldTag.putInt("SpawnVersion", 0); oldTag.putBoolean("Started", true);
        oldTag.putString("Mobs", java.util.UUID.randomUUID().toString());
        var legacy = (ChallengeSpawnerBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                forgePos, maze.getBlockState(forgePos), oldTag, maze.registryAccess());
        maze.setBlockEntity(legacy);
        ChallengeSpawnerBlockEntity.tick(maze, forgePos, legacy.getBlockState(), legacy);
        var repaired = legacy.saveWithFullMetadata(maze.registryAccess());
        require(!repaired.getBooleanOr("Started", true) && repaired.getStringOr("Mobs", "missing").isEmpty(),
                "Old broken Forge spawner still waits for deleted mobs");
        player.setPos(previous); player.setGameMode(mode);
    }

    private static void checkEntrance(MinecraftServer server) {
        var level = server.overworld();
        var boss = Asterion.MINOTAUR.create(level, EntitySpawnReason.COMMAND);
        try {
            var begin = MinotaurEntity.class.getDeclaredMethod("beginDoorEntry", net.minecraft.core.Direction.class);
            begin.setAccessible(true); begin.invoke(boss, MinotaurArenaEntrances.PLAYER_ENTRANCE);
            require(!boss.noPhysics && level.noCollision(boss), "Boss begins cutscene inside blocks or with noclip");
            var sample = MinotaurEntity.class.getDeclaredMethod("animationSeconds", MinotaurEntity.AnimationState.class, double.class);
            sample.setAccessible(true);
            require(Math.abs((double)sample.invoke(boss, MinotaurEntity.AnimationState.ROAR_START,
                    (double)MinotaurAnimationTiming.ENTRY_ROAR.roarSoundTick()) - 2.75) < .0001,
                    "Rendered entrance animation misses audio's frame 66");
            BlockPos platform = MinotaurArenaEntrances.door(MinotaurArenaEntrances.BOSS_ENTRANCE).south(3);
            level.setBlock(platform, Blocks.AIR.defaultBlockState(), 18);
            MinotaurArenaEntrances.clearBossEntryPath(level, boss.getBbWidth(), boss.getBbHeight(), true);
            require(level.getBlockState(platform).isAir(), "Entrance creates a platform in front of door");
            Vec3 start = boss.position();
            double distance = Math.max(5.5, boss.getBbWidth() / 2 + 3.5) + boss.getBbWidth() / 2 + 2.25;
            for (int i = 0; i <= Math.floor(distance * 2); i++) {
                AABB body = boss.getBoundingBox().move(0, 0, i * .5);
                require(level.noCollision(boss, body.deflate(.01)), "Boss entry route obstructed at " + i);
                if (start.z + i * .5 < MinotaurArenaEntrances.door(MinotaurArenaEntrances.BOSS_ENTRANCE).getZ())
                    require(!level.getBlockState(BlockPos.containing(start.add(0, -.1, i * .5))).isAir(), "Boss staging floor missing");
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void checkLift(MinecraftServer server) {
        var lift = ChainLiftContent.LIFT.create(server.overworld(), EntitySpawnReason.EVENT);
        lift.configure(new BlockPos(0, 100, 0), 204);
        try {
            var begin = ChainLiftEntity.class.getDeclaredMethod("beginJourney", double.class, boolean.class);
            begin.setAccessible(true); begin.invoke(lift, lift.bottomY(), true);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        long now = server.overworld().getGameTime() + 3;
        int duration = ChainLiftEntity.travelTicks(lift.topY() - lift.bottomY());
        double previous = lift.scheduledY(now), earlySpeed = 0;
        for (int i = 1; i <= duration; i++) {
            double next = lift.scheduledY(now + i), speed = previous - next;
            require(speed >= 0 && speed <= .321, "Lift reverses or exceeds reduced speed");
            if (i == 5) earlySpeed = speed;
            if (i == 25) require(speed > earlySpeed * 2, "Lift fails to accelerate exponentially");
            previous = next;
        }
        require(Math.abs(previous - lift.bottomY()) < .001, "Lift misses landing");
        checkLiftButton(server);
    }

    private static void checkLiftButton(MinecraftServer server) {
        var level = server.overworld();
        BlockPos anchor = new BlockPos(400, 160, 400);
        level.setBlock(anchor, ChainLiftContent.ANCHOR.defaultBlockState(), 18);
        var lift = ChainLiftContent.LIFT.create(level, EntitySpawnReason.EVENT);
        lift.configure(anchor, 180);
        lift.callTo(true); lift.tick();
        require(lift.moving() && lift.scheduledY(level.getGameTime() + 10) < lift.topY(),
                "Button at upper landing does not depart immediately");
        lift.callTo(false);
        try {
            var stop = ChainLiftEntity.class.getDeclaredMethod("stopAtCurrentPosition");
            stop.setAccessible(true);
            lift.setPos(lift.getX(), lift.bottomY(), lift.getZ()); stop.invoke(lift);
            lift.tick();
            require(!lift.moving(), "Queued call reverses the lift immediately on arrival");
            lift.callTo(false); lift.tick();
            require(lift.moving() && lift.scheduledY(level.getGameTime() + 10) > lift.bottomY(),
                    "Button at lower landing does not depart upward immediately");
            lift.setPos(lift.getX(), lift.topY(), lift.getZ()); stop.invoke(lift);
            lift.callTo(false); lift.tick();
            require(lift.moving() && lift.scheduledY(level.getGameTime() + 10) < lift.topY(),
                    "Remote lower button no longer calls the lift down");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static void checkPortal(MinecraftServer server) {
        var level = server.overworld();
        BlockPos center = new BlockPos(100, 80, 100);
        for (BlockPos pos : BlockPos.betweenClosed(99, 57, 99, 101, 83, 101))
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 18);
        WorldGenerator.summonPortal(level, center, 80);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            require(level.getBlockState(new BlockPos(100 + x, 54, 100 + z)).isAir(), "Rift and shaft misaligned");
        for (BlockPos pos : BlockPos.betweenClosed(99, 57, 99, 101, 83, 101))
            require(level.getBlockState(pos).isAir(), "Terrain blocks the portal entrance at " + pos);
        var saved = net.krodark.asterion.AsterionWorldState.get(level);
        require(WorldGenerator.isActivePortalProtected(level, new BlockPos(100, 54, 100)), "Portal protection misses rift");
        try {
            var version = saved.getClass().getDeclaredField("portalLayoutVersion"); version.setAccessible(true); version.setInt(saved, 0);
            var portal = WorldGenerator.class.getDeclaredField("summonedPortal"); portal.setAccessible(true); portal.set(null, null);
            var restore = WorldGenerator.class.getDeclaredMethod("restoreSavedPortal", MinecraftServer.class); restore.setAccessible(true);
            restore.invoke(null, server);
            require(saved.summonedPortal().center().equals(center.offset(8, 0, 8)), "Legacy portal corner was not migrated");
            portal.set(null, null); restore.invoke(null, server);
            require(saved.summonedPortal().center().equals(center.offset(8, 0, 8)), "Portal moved twice on reload");
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        BlockPos naturalCenter = new BlockPos(160, 0, 160);
        for (int x = 152; x <= 168; x++) for (int z = 152; z <= 168; z++)
            level.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 18);
        WorldGenerator.buildGateway(level, naturalCenter);
        int y = saved.gatewayRiftY(naturalCenter);
        require(y != Integer.MIN_VALUE, "Natural gateway height was not saved");
        BlockPos marker = new BlockPos(160, y, 160);
        level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
        WorldGenerator.buildGateway(level, naturalCenter);
        require(level.getBlockState(marker).is(Blocks.DIAMOND_BLOCK), "Loading gateway overwrote the existing structure");
        BlockPos obstruction = marker.above(29), rim = obstruction.east(2);
        level.setBlock(obstruction, Blocks.GRASS_BLOCK.defaultBlockState(), 18);
        level.setBlock(rim, Blocks.DIAMOND_BLOCK.defaultBlockState(), 18);
        try {
            var cleared = WorldGenerator.class.getDeclaredField("CLEARED_PORTAL_ENTRANCES"); cleared.setAccessible(true);
            ((java.util.Set<?>)cleared.get(null)).clear(); // Simulate reopening an existing world.
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        WorldGenerator.buildGateway(level, naturalCenter);
        require(level.getBlockState(obstruction).isAir(), "Saved portal entrance was not repaired at blueprint Y=92");
        require(level.getBlockState(rim).is(Blocks.DIAMOND_BLOCK), "Portal repair damaged the rim outside the 3x3 hole");
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
