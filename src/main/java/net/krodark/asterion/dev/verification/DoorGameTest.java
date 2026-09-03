package net.krodark.asterion.dev.verification;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.WorldGenerator;
import net.krodark.asterion.entity.MinotaurEntity;
import net.krodark.asterion.worldgen.MinotaurArenaEntrances;
import net.krodark.asterion.worldgen.BossArenaEncounter;
import net.krodark.asterion.block.DirectionalGateBlock;
import net.krodark.asterion.client.BossEntranceCinematic;
import net.krodark.asterion.client.CinematicControls;
import net.krodark.asterion.block.MinotaurDoorBlock;
import net.krodark.asterion.block.MinotaurDoorBlockEntity;
import net.krodark.asterion.block.MinotaurDoorMotion;
import net.krodark.asterion.client.ragdoll.PhysicsDebrisSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Runs only with Fabric client gametests enabled; creates an isolated disposable world. */
public final class DoorGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().handle()));
        if (Boolean.getBoolean("asterion.pillarTest")) { PillarCheck.integration(context); return; }
        BlockPos root = new BlockPos(0, 121, 0);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(mc -> {
                WorldGenerator.ensureBossArenaReady(mc.getLevel(Asterion.ASTERION_LEVEL));
                check(WorldGenerator.isBossArenaReady(), "Arena was not completed before the player joined");
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                MinotaurArenaEntrances.build(maze);
                PillarCheck.arena(maze);
                check(WorldGenerator.activeBossBraziers(maze) == 4, "Arena fire-power braziers missing");
                var fireBoss = Asterion.MINOTAUR.create(maze, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                try {
                    var powered = MinotaurEntity.class.getDeclaredMethod("greekFirePowered"); powered.setAccessible(true);
                    check((boolean)powered.invoke(fireBoss), "Boss did not receive brazier power");
                    for (Direction direction : Direction.Plane.HORIZONTAL)
                        net.krodark.asterion.block.GreekBrazierBlock.extinguish(maze, net.krodark.asterion.worldgen.CatacombArena.brazier(direction));
                    check(!(boolean)powered.invoke(fireBoss), "Boss retained fire after all braziers extinguished");
                    for (Direction direction : Direction.Plane.HORIZONTAL)
                        net.krodark.asterion.block.GreekBrazierBlock.placeStructure((pos, state) -> maze.setBlock(pos, state, 3),
                                net.krodark.asterion.worldgen.CatacombArena.brazier(direction));
                } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
                var bossDoor = (MinotaurDoorBlockEntity)maze.getBlockEntity(MinotaurArenaEntrances.door(Direction.NORTH));
                bossDoor.interact(mc.getPlayerList().getPlayers().getFirst(), new ItemStack(Asterion.MINOTAUR_KEY));
                check(!bossDoor.allowsArenaEntry(), "The boss door accepted a player key");
                for (Direction facing : MinotaurArenaEntrances.DOORS) {
                    check(maze.getBlockEntity(MinotaurArenaEntrances.door(facing)) instanceof MinotaurDoorBlockEntity,
                            "Missing prebuilt door: " + facing);
                }
                for (Direction facing : MinotaurArenaEntrances.DOORS) {
                    Vec3 center = Vec3.atBottomCenterOf(MinotaurArenaEntrances.door(facing));
                    Vec3 outward = facing.getUnitVec3();
                    check(MinotaurArenaEntrances.crossedEntrance(center.add(outward.scale(2)), center.subtract(outward.scale(2))) == (facing == Direction.SOUTH ? facing : null),
                            "Only the south player doorway should authorize a crossing");
                    check(MinotaurArenaEntrances.crossedEntrance(center.subtract(outward.scale(2)), center.add(outward.scale(2))) == null,
                            "Leaving the arena was mistaken for entering it");
                }
            });
            server.runCommand("tp @a 0.5 121 -12 0 -4");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var player = mc.getPlayerList().getPlayers().getFirst();
                for (int x = -40; x <= 40; x++) for (int z = -80; z <= 80; z++)
                    level.setBlock(new BlockPos(x, 120, z), Blocks.STONE.defaultBlockState(), 3);
                player.setGameMode(GameType.CREATIVE);
                PillarCheck.run(level, player);
                MinotaurMotionCheck.run(level, player);
                player.setYRot(180);
                var stack = new ItemStack(Asterion.MINOTAUR_DOOR);
                var hit = new BlockHitResult(Vec3.atBottomCenterOf(root), Direction.UP, root.below(), false);
                ((BlockItem)Asterion.MINOTAUR_DOOR.asItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
                check(level.getBlockEntity(root) instanceof MinotaurDoorBlockEntity, "Door item did not place its anchor");
                var door = (MinotaurDoorBlockEntity)level.getBlockEntity(root);
                for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++) {
                    var pos = MinotaurDoorBlock.part(root, door.facing(), column, row);
                    var state = level.getBlockState(pos);
                    check(state.is(Asterion.MINOTAUR_DOOR), "Missing collision part");
                    check(MinotaurDoorBlock.root(pos, state).equals(root), "Part resolves wrong anchor");
                    check(!state.getCollisionShape(level, pos).isEmpty(), "Closed door has a collision hole");
                }
                player.setGameMode(GameType.SURVIVAL);
                door.interact(player, ItemStack.EMPTY);
                check(!door.getBlockState().getValue(MinotaurDoorBlock.OPEN), "Locked door opened without a key");
            });
            server.runCommand("tp @a 0.5 121 -12 0 -4");
            context.runOnClient(client -> client.options.hideGui = true);
            context.waitTicks(12);
            context.takeScreenshot("door-closed");
            server.runOnServer(mc -> {
                var key = new ItemStack(Asterion.MINOTAUR_KEY);
                ((MinotaurDoorBlockEntity)mc.overworld().getBlockEntity(root))
                        .interact(mc.getPlayerList().getPlayers().getFirst(), key);
                check(key.isEmpty(), "Minotaur key was not consumed on first unlock");
            });
            context.waitTicks(MinotaurDoorMotion.OPEN_TICKS + 8);
            context.takeScreenshot("door-open");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                var door = (MinotaurDoorBlockEntity)level.getBlockEntity(root);
                check(Math.abs(door.angle(0) - MinotaurDoorMotion.OPEN_ANGLE) < .001, "Opening did not finish");
                check(level.getBlockState(root).getCollisionShape(level, root).isEmpty(), "Open door blocks passage");
                var player = mc.getPlayerList().getPlayers().getFirst();
                Vec3 previous = player.position();
                player.setPos(Vec3.atBottomCenterOf(root));
                door.interact(player, ItemStack.EMPTY);
                check(Math.abs(door.angle(MinotaurDoorMotion.OPEN_TICKS) - MinotaurDoorMotion.OPEN_ANGLE) < .001,
                        "Door tried closing on a player in the passage");
                player.setPos(previous);
                door.interact(player, ItemStack.EMPTY);
            });
            context.waitTicks(MinotaurDoorMotion.OPEN_TICKS + 8);
            server.runOnServer(mc -> {
                var door = (MinotaurDoorBlockEntity)mc.overworld().getBlockEntity(root);
                check(!door.getBlockState().getValue(MinotaurDoorBlock.OPEN), "Unlocked door did not close");
                door.beginBreach();
            });
            server.runCommand("tp @a 0.5 121 -32 0 -6");
            context.waitTicks(47);
            context.takeScreenshot("door-third-jolt");
            context.waitTicks(30);
            server.runOnServer(mc -> check(!mc.overworld().getBlockState(root).is(Asterion.MINOTAUR_DOOR), "Breach left the door blocking the opening"));
            context.takeScreenshot("door-break");
            context.runOnClient(client -> {
                var leaves = doorSamples();
                check(leaves.size() == 2, "Breach did not spawn two physics leaves");
                for (DoorSample leaf : leaves) {
                    check(leaf.position.distanceTo(Vec3.atCenterOf(root)) > 5, "Door leaf did not fly away from its hinges");
                    check(leaf.position.y > root.getY() + 2.75, "Door kick did not lift the leaf into flight");
                }
            });
            context.waitTicks(140);
            context.takeScreenshot("door-settled");
            context.runOnClient(client -> {
                var leaves = doorSamples();
                check(leaves.size() == 2, "A physical door disappeared before settling");
                Asterion.LOGGER.info("Door physics after landing: {}", leaves);
                for (DoorSample leaf : leaves) {
                    check(leaf.position.y > 120.5 && leaf.position.y < 125, "Door fell through the test floor");
                    check(leaf.velocity.length() < .18, "Door never lost impact energy and settled");
                    check(leaf.sleeping, "Settled door kept running its physics solver");
                }
                Vec3 center = leaves.getFirst().position.add(leaves.getLast().position).scale(.5);
                PhysicsDebrisSystem.throwDoors(center, 8);
                check(doorSamples().stream().allMatch(leaf -> leaf.velocity.length() > .2 && !leaf.sleeping),
                        "Explosion did not throw settled doors again");
            });
            context.waitTicks(10);
            context.takeScreenshot("door-thrown-again");
            server.runOnServer(mc -> {
                MinotaurDoorBlock.place(mc.overworld(), root, Direction.SOUTH);
                mc.overworld().setBlock(MinotaurDoorBlock.part(root, Direction.SOUTH, 0, 4), Blocks.AIR.defaultBlockState(), 3);
            });
            context.waitTicks(3);
            server.runOnServer(mc -> {
                for (int column = 0; column < 7; column++) for (int row = 0; row < 5; row++)
                    check(!mc.overworld().getBlockState(MinotaurDoorBlock.part(root, Direction.SOUTH, column, row))
                            .is(Asterion.MINOTAUR_DOOR), "Breaking one part left orphaned collision blocks");
            });
            Asterion.LOGGER.info("PASS: door item placement, all 35 collision parts, key lock, opening/closing, breach and live debris rendering");

            // Arriving directly in the pit is no longer an encounter trigger.
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 0.5 180 0");
            context.waitTicks(20);
            server.runOnServer(mc -> check(!WorldGenerator.isBossEncounterActive(mc.getLevel(Asterion.ASTERION_LEVEL)),
                    "Being in the pit without a keyed entry started the fight"));
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 43.5 180 -4");
            context.waitTicks(3);
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                var player = mc.getPlayerList().getPlayers().getFirst();
                var door = (MinotaurDoorBlockEntity)maze.getBlockEntity(MinotaurArenaEntrances.door(Direction.SOUTH));
                door.interact(player, ItemStack.EMPTY);
                check(!door.allowsArenaEntry(), "Unkeyed gate authorized the encounter");
                check(door.getBlockState().getDestroyProgress(player, maze, door.getBlockPos()) == 0,
                        "Generated arena door can be mined to bypass the key");
                var hunter = Asterion.MINOTAUR.create(maze, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                hunter.setPos(.5, 37, 41.5);
                hunter.beginHunting(player);
                maze.addFreshEntity(hunter);
            });
            context.waitTicks(20);
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(!WorldGenerator.isBossEncounterActive(maze), "A nearby hunter bypassed the keyed boss entry");
                for (var entity : maze.getAllEntities()) if (entity instanceof MinotaurEntity) entity.discard();
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE);
                ((MinotaurDoorBlockEntity)maze.getBlockEntity(MinotaurArenaEntrances.door(Direction.SOUTH))).interact(player, ItemStack.EMPTY);
                player.setGameMode(GameType.SURVIVAL);
            });
            context.waitTicks(32);
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 39.5 180 -4");
            context.waitTicks(4);
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(!WorldGenerator.isBossEncounterActive(maze), "Crossing a gate opened without a key started the fight");
                ((MinotaurDoorBlockEntity)maze.getBlockEntity(MinotaurArenaEntrances.door(Direction.SOUTH)))
                        .interact(mc.getPlayerList().getPlayers().getFirst(), ItemStack.EMPTY);
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 39.5 180 -4");
            context.waitTicks(32);
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                for (Direction facing : MinotaurArenaEntrances.DOORS)
                    check(level.getBlockEntity(MinotaurArenaEntrances.door(facing)) instanceof MinotaurDoorBlockEntity,
                            "Missing arena entrance: " + facing);
                var door = (MinotaurDoorBlockEntity)level.getBlockEntity(MinotaurArenaEntrances.door(Direction.SOUTH));
                door.interact(mc.getPlayerList().getPlayers().getFirst(), new ItemStack(Asterion.MINOTAUR_KEY));
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 43.5 180 -4");
            context.waitTicks(3);
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 39.5 180 -4");
            context.waitTicks(36);
            context.takeScreenshot("arena-player-entrance");
            context.runOnClient(client -> {
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                client.options.hideGui = false;
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 31 180 -4");
            var started = new java.util.concurrent.atomic.AtomicBoolean();
            for (int tick = 0; tick < 80 && !started.get(); tick += 2) {
                context.waitTicks(2);
                server.runOnServer(mc -> {
                    for (var entity : mc.getLevel(Asterion.ASTERION_LEVEL).getAllEntities())
                        if (entity instanceof MinotaurEntity boss && boss.doorEntryTicks() > 0) {
                            check(boss.getZ() < -34, "Boss did not spawn behind the opposite door");
                            Vec3 before = boss.position();
                            int beforeTick = boss.doorEntryTicks();
                            MinotaurEntity.activateCenterBoss(mc.getLevel(Asterion.ASTERION_LEVEL),
                                    mc.getPlayerList().getPlayers().getFirst(), boss, Direction.SOUTH);
                            check(boss.position().equals(before) && boss.doorEntryTicks() == beforeTick,
                                    "A joining player restarted the boss entrance");
                            started.set(true);
                        }
                });
            }
            check(started.get(), "Entering through the player door did not trigger the boss entrance");
            var anchor = new java.util.concurrent.atomic.AtomicReference<Vec3>();
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                anchor.set(player.position());
                check(player.getZ() > 28 && player.getZ() < 35,
                        "Player was not staged just inside the entrance gate");
                check(Math.abs(net.minecraft.util.Mth.wrapDegrees(player.getYRot() - 180F)) < 8F,
                        "Player was not facing the Minotaur's north gate");
                check(BossArenaEncounter.isMovementLocked(player), "Server did not lock cinematic movement");
                check(player.isInvulnerable(), "Cinematic player is vulnerable to attacks");
                player.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
                        player.position().add(8, 1, 0), 90, 0, true, false));
                check(player.position().equals(anchor.get()), "A movement packet moved the player during the shot");
            });
            context.waitTicks(3);
            context.runOnClient(client -> {
                check(BossEntranceCinematic.isActive() && CinematicControls.locked(), "Client cinematic did not start");
                Vec3 position = client.player.position();
                client.player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(2, 1, 0));
                check(client.player.position().equals(position), "Local movement bypassed the cinematic lock");
                client.options.keyUp.setDown(true);
                client.options.keyJump.setDown(true);
            });
            context.waitTicks(34);
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(player.position().distanceToSqr(anchor.get()) < .001, "Held movement displaced the cinematic body");
                check(!maze.getBlockState(MinotaurArenaEntrances.door(Direction.SOUTH)).getValue(MinotaurDoorBlock.OPEN),
                        "The player's entry doors did not close behind them");
                check(gateIsOpen(maze, Direction.SOUTH),
                        "The player's gate lowered during the cutscene");
                check(gateIsOpen(maze, Direction.NORTH),
                        "The boss gate closed before the Minotaur could enter");
                check(maze.getBlockState(MinotaurArenaEntrances.OMEGA_LOCK_POSITION).isAir(),
                        "The Omega Lock remained visible during the gate cutscene");
                check(maze.noCollision(player), "The closing gate crushed the player");
            });
            context.takeScreenshot("arena-cinematic-rattle");
            context.waitTicks(55);
            context.takeScreenshot("arena-boss-kick");
            context.waitTicks(22);
            context.takeScreenshot("arena-boss-breach");
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(!level.getBlockState(MinotaurArenaEntrances.door(Direction.NORTH)).is(Asterion.MINOTAUR_DOOR),
                        "Boss entrance was not broken off");
            });
            context.waitTicks(95);
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(!BossArenaEncounter.isMovementLocked(player) && !player.isInvulnerable() && !player.isNoGravity(),
                        "Cinematic did not restore normal player state");
                for (Direction facing : MinotaurArenaEntrances.DOORS) {
                    check(level.getBlockState(MinotaurArenaEntrances.door(facing)).is(Asterion.MINOTAUR_DOOR), "A door failed to rebuild");
                    check(!gateIsOpen(level, facing), "A fight gate remained open");
                }
                check(level.getBlockState(MinotaurArenaEntrances.door(Direction.SOUTH)).is(Asterion.MINOTAUR_DOOR),
                        "Boss broke the player's entrance too");
                check(!level.getBlockState(MinotaurArenaEntrances.door(Direction.NORTH)).is(Asterion.MINOTAUR_DOOR),
                        "Boss entrance door was rebuilt after the cutscene");
                check(level.getBlockState(MinotaurArenaEntrances.OMEGA_LOCK_POSITION).is(Asterion.OMEGA_LOCK),
                        "The Omega Lock did not return when the gate cutscene ended");
                boolean entered = false;
                for (var entity : level.getAllEntities()) if (entity instanceof MinotaurEntity boss)
                    entered |= boss.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS
                            && boss.doorEntryTicks() == 0 && WorldGenerator.isInsideBossArena(boss.position());
                check(entered, "Boss did not finish entering the arena");
            });
            context.runOnClient(client -> {
                check(!BossEntranceCinematic.isActive() && !CinematicControls.locked(), "Client stayed frozen after the shot");
            });
            context.takeScreenshot("arena-gates-sealed");
            server.runCommand("execute as @a at @s run asterion minotaur debug");
            server.runCommand("execute as @a at @s run asterion minotaur status");
            server.runCommand("execute as @a at @s run asterion minotaur stop");
            server.runOnServer(mc -> check(WorldGenerator.isBossEncounterActive(mc.getLevel(Asterion.ASTERION_LEVEL)),
                    "Stopping arena telemetry removed the real boss"));
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 23.5 0 -8");
            context.runOnClient(client -> {
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                client.options.hideGui = true;
            });
            context.waitTicks(3);
            context.takeScreenshot("arena-entry-gate-closed");
            var wildCentipede = new java.util.concurrent.atomic.AtomicReference<net.krodark.asterion.entity.ScarletCentipedeEntity>();
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                mc.getPlayerList().getPlayers().getFirst().setInvulnerable(true);
                var centipede = Asterion.SCARLET_CENTIPEDE.create(maze, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                centipede.setPos(15, 37, 0);
                check(!centipede.checkSpawnRules(maze, net.minecraft.world.entity.EntitySpawnReason.NATURAL),
                        "Centipedes can naturally spawn during the boss fight");
                maze.addFreshEntity(centipede);
                wildCentipede.set(centipede);
            });
            context.waitTicks(65);
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(wildCentipede.get().isRemoved(), "Wild centipede remained inside the fight");
                var beetles = maze.getEntitiesOfClass(net.krodark.asterion.entity.BombadierBeetleEntity.class,
                        new net.minecraft.world.phys.AABB(-44, 5, -44, 44, 49, 44));
                check(beetles.size() <= 4, "Beetle reinforcement wave exceeded its cap");
                for (var beetle : beetles) check(maze.noCollision(beetle), "Reinforcement spawned in a block");
            });
            context.runOnClient(client -> PhysicsDebrisSystem.clear());
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                BlockPos pillar = PillarCheck.firstArenaRoot(maze);
                check(WorldGenerator.breakBossPillar(maze, new net.minecraft.world.phys.AABB(pillar).inflate(3, 12, 3)),
                        "Test pillar did not break");
                check(maze.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                        new net.minecraft.world.phys.AABB(-44, 5, -44, 44, 55, 44)).isEmpty(),
                        "Pillar collapse still creates old falling-block rubble");
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                try {
                    var field = PhysicsDebrisSystem.class.getDeclaredField("PIECES");
                    field.setAccessible(true);
                    var pieces = (java.util.List<?>)field.get(null);
                    check(!pieces.isEmpty() && pieces.size() <= 128, "Physical rubble batch was missing or exceeded its cap");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            Asterion.LOGGER.info("PASS: centipede exclusion, bounded beetle wave and physical pillar debris without falling blocks");
            Asterion.LOGGER.info("PASS: prebuilt roof and doors, clear entrance lanes for all pillar counts, keyed crossing required, opposite-door boss spawn, uninterrupted intro and physical launch");
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                for (int x : new int[]{-32, 32}) check(maze.getBlockState(new BlockPos(x, 45, 0)).isAir(), "Obsolete side corridor projects into the arena");
                for (Direction side : MinotaurArenaEntrances.DOORS)
                    check(gateExists(maze, side), "Authored gate hardware missing");
                // Use a known roof sample so this behavior check does not depend on
                // which center tiles the authored retractable ceiling leaves open.
                maze.setBlock(new BlockPos(0, 47, 0), Asterion.MAZESTEEL_BLOCK.defaultBlockState(), 2);
                MinotaurCombatSelectionCheck.run(maze, mc.getPlayerList().getPlayers().getFirst());
                MinotaurRageCheck.run(maze, mc.getPlayerList().getPlayers().getFirst());
                WorldGenerator.collapseBossRoofRing(maze, new Vec3(0, 37, 0), 0);
                check(maze.getBlockState(new BlockPos(0, 47, 0)).isAir(), "Scripted roof collapse only emitted cosmetic copies");
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 18.5 180 -32");
            context.waitTicks(2);
            context.takeScreenshot("arena-heavy-roof-rain");
            context.runOnClient(client -> check(bossBarCount(client) == 1, "Phase one must show only the health bar"));
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setInvulnerable(false);
                player.hurtServer(mc.getLevel(Asterion.ASTERION_LEVEL), player.damageSources().genericKill(), Float.MAX_VALUE);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                check(bossBarCount(client) == 0, "Boss bars survived death and encounter discard");
                client.player.respawn();
            });
            context.waitTicks(8);
            context.runOnClient(client -> check(bossBarCount(client) == 0, "Boss bars returned on respawn"));
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 7 23.5 0 0");
            context.waitTicks(3);
            Asterion.LOGGER.info("PASS: actual death, encounter discard, boss bar removal and respawn cleanup");
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(!WorldGenerator.isBossEncounterActive(maze), "Death did not reset the encounter");
                check(maze.getEntitiesOfClass(net.krodark.asterion.entity.BombadierBeetleEntity.class,
                        new net.minecraft.world.phys.AABB(-44, 5, -44, 44, 49, 44),
                        beetle -> beetle.entityTags().contains("asterion_arena_beetle")).isEmpty(), "Reset left summoned beetles behind");
                for (Direction facing : MinotaurArenaEntrances.DOORS) {
                    check(maze.getBlockEntity(MinotaurArenaEntrances.door(facing)) instanceof MinotaurDoorBlockEntity, "Wipe did not restore doors");
                    check(gateIsOpen(maze, facing), "Wipe left players locked in");
                }
                BlockPos sentinel = new BlockPos(0, 37, 0);
                maze.setBlock(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
                WorldGenerator.clearRuntimeState(mc);
                WorldGenerator.prepareBossArenaBeforePlayers(maze);
                check(maze.getBlockState(sentinel).is(Blocks.GOLD_BLOCK), "Reloading arena bookkeeping rebuilt the saved chamber");
                BossArenaEncounter.initialize(maze);
                check(gateIsOpen(maze, Direction.NORTH),
                        "Reloading left the arena sealed");
                maze.setBlock(sentinel, Blocks.AIR.defaultBlockState(), 2);
                var player = mc.getPlayerList().getPlayers().getFirst();
                var boss = Asterion.MINOTAUR.create(maze, net.minecraft.world.entity.EntitySpawnReason.EVENT);
                boss.setPos(.5, 37, -25.5);
                maze.addFreshEntity(boss);
                player.setInvulnerable(true);
                player.setNoGravity(true);
                BossArenaEncounter.begin(maze, player, boss, Direction.SOUTH);
                BossArenaEncounter.releasePlayer(player);
                check(!BossArenaEncounter.isMovementLocked(player) && player.isInvulnerable() && player.isNoGravity(),
                        "Disconnect cleanup failed to preserve pre-existing player flags");
                player.setInvulnerable(false);
                player.setNoGravity(false);
                BossArenaEncounter.begin(maze, player, boss, Direction.SOUTH);
                check(BossArenaEncounter.isMovementLocked(player), "Returning participant could not rejoin the intro");
                boss.discard();
                BossArenaEncounter.tick(maze);
                check(!BossArenaEncounter.isMovementLocked(player) && !player.isInvulnerable() && !player.isNoGravity(),
                        "Aborting the intro left player protection or movement lock active");
                check(!BossArenaEncounter.isSealed(maze), "Missing boss left the encounter sealed");
            });
            context.waitTicks(3);
            context.runOnClient(client -> check(!CinematicControls.locked(), "Aborting the intro left client controls locked"));
            Asterion.LOGGER.info("PASS: safe placement, local and server movement locks, entry-door closure, delayed boss gate, rebuilt doors, released controls and reopened gates on reset");
            server.runCommand("execute in minecraft:overworld run tp @a 0.5 121 -12 0 0");
            server.runCommand("gamemode creative @a");
            context.waitTicks(4);
            context.runOnClient(client -> PhysicsDebrisSystem.clear());
            server.runCommand("execute as @a at @s run asterion minotaur debug");
            server.runCommand("execute as @a at @s run asterion minotaur pause");
            var debugBoss = new java.util.concurrent.atomic.AtomicReference<MinotaurEntity>();
            server.runOnServer(mc -> {
                for (var entity : mc.overworld().getAllEntities()) if (entity instanceof MinotaurEntity boss && boss.isDebugMinotaur()) debugBoss.set(boss);
                check(debugBoss.get() != null, "Debug command did not spawn an overworld Minotaur");
                check(!debugBoss.get().shouldBeSaved(), "Debug boss would persist in a normal save");
                check(debugBoss.get().getY() > 119, "Debug boss was teleported to the arena floor");
                mc.overworld().setBlock(new BlockPos(0, 37, 0), Blocks.GOLD_BLOCK.defaultBlockState(), 2);
            });
            server.runCommand("execute as @a at @s run asterion minotaur attack rubble_throw");
            context.waitTicks(30);
            server.runOnServer(mc -> check(debugBoss.get().debugStatus().contains("attack=RUBBLE_THROW"), "Forced overworld attack did not run"));
            context.takeScreenshot("overworld-debug-rubble");
            context.runOnClient(client -> {
                try {
                    var field = PhysicsDebrisSystem.class.getDeclaredField("PIECES"); field.setAccessible(true);
                    boolean blocks = false;
                    for (Object piece : (java.util.List<?>)field.get(null)) blocks |= read(piece, "blockVisual") != null;
                    check(blocks, "Spinning block models were not restored to rubble throws");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            context.waitTicks(45);
            server.runCommand("execute as @a at @s run asterion minotaur attack greek_fire_laser");
            context.waitTicks(30);
            server.runOnServer(mc -> {
                check(debugBoss.get().debugStatus().contains("attack=GREEK_FIRE_LASER"), "Overworld laser required arena braziers");
                check(mc.overworld().getBlockState(new BlockPos(0, 37, 0)).is(Blocks.GOLD_BLOCK), "Debug encounter edited the arena coordinates in the overworld");
            });
            server.runCommand("execute as @a at @s run asterion minotaur stop");
            server.runOnServer(mc -> check(debugBoss.get().isRemoved(), "Stop command left the debug boss alive"));
            Asterion.LOGGER.info("PASS: overworld debug spawn, unsaved boss, forced rubble/laser attacks, spinning block visuals, local coordinates and stop command");

            server.runCommand("execute as @a at @s run asterion minotaur debug");
            server.runCommand("execute as @a at @s run asterion minotaur pause");
            server.runOnServer(mc -> {
                for (var entity : mc.overworld().getAllEntities()) if (entity instanceof MinotaurEntity boss && boss.isDebugMinotaur()) debugBoss.set(boss);
                var player = mc.getPlayerList().getPlayers().getFirst();
                var boss = debugBoss.get();
                player.teleportTo(boss.getX() + 2, boss.getY() + 2, boss.getZ());
            });
            server.runCommand("execute as @a at @s run asterion minotaur attack grab");
            context.waitTicks(20);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(debugBoss.get().heldPlayerId() == player.getId(), "Close airborne player was not grabbed");
                check(MinotaurEntity.controlsPlayer(player), "Grab did not lock player movement");
            });
            context.takeScreenshot("minotaur-hand-grab");
            context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
            context.waitTicks(2);
            context.takeScreenshot("minotaur-hand-grab-third-person");
            context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            var throwStart = new java.util.concurrent.atomic.AtomicReference<Vec3>();
            server.runOnServer(mc -> throwStart.set(mc.getPlayerList().getPlayers().getFirst().position()));
            context.waitTicks(60);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(debugBoss.get().heldPlayerId() == -1, "Throw left player attached to hand");
                double distance = player.position().subtract(throwStart.get()).horizontalDistance();
                check(distance >= 50 && distance <= 80, "Throw range outside 50–80 blocks: " + distance);
            });
            context.waitTicks(35);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                var boss = debugBoss.get();
                // Clear the earlier door fixture out of this charge lane.
                for (int x = -6; x < 18; x++) for (int y = 121; y < 136; y++) for (int z = -8; z <= 8; z++)
                    mc.overworld().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                boss.setPos(0, 121, 0);
                player.teleportTo(2, 123, 0);
                player.resetFallDistance();
                boss.beginDebug(player);
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                player.setHealth(100);
                player.getFoodData().setFoodLevel(0);
                player.getFoodData().setSaturation(0);
                for (int y = 121; y < 160; y++) for (int z = -80; z <= 80; z++)
                    mc.overworld().setBlock(new BlockPos(18, y, z), Blocks.STONE.defaultBlockState(), 2);
            });
            server.runCommand("execute as @a at @s run asterion minotaur attack grab");
            context.waitTicks(35);
            server.runOnServer(mc -> check(mc.getPlayerList().getPlayers().getFirst().getHealth() == 100, "Player took damage before throw release"));
            context.waitTicks(25);
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(player.getX() < 18, "Throw tunnelled through the wall");
                check(player.getHealth() <= 80 && player.getHealth() > 75, "Expected 10 release + 10 wall impact damage; health=" + player.getHealth());
            });
            context.waitTicks(15);
            server.runOnServer(mc -> {
                var boss = debugBoss.get();
                check(boss.debugStatus().contains("attack=CHARGE"), "Throw did not prioritize charge: " + boss.debugStatus());
                try {
                    var finish = MinotaurEntity.class.getDeclaredMethod("finishBossAttack", int.class);
                    finish.setAccessible(true); finish.invoke(boss, 0);
                    var pending = MinotaurEntity.class.getDeclaredField("throwPursuitPending");
                    pending.setAccessible(true); pending.setBoolean(boss, true);
                    var combos = MinotaurEntity.class.getDeclaredMethod("tickPendingCombos", net.minecraft.server.level.ServerLevel.class);
                    combos.setAccessible(true); combos.invoke(boss, mc.overworld());
                    check(boss.debugStatus().contains("attack=CHAIN_GRAPPLE"), "Charge cooldown did not fall back to chain grapple");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            server.runCommand("execute as @a at @s run asterion minotaur stop");
            server.runOnServer(mc -> check(!MinotaurEntity.controlsPlayer(mc.getPlayerList().getPlayers().getFirst()), "Stop left grab controls locked"));
            Asterion.LOGGER.info("PASS: airborne grab, hand attachment state, movement lock, 50–80 block throw and cleanup");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.teleportTo(8, 121, 0);
                var boss = Asterion.MINOTAUR.create(mc.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0, 121, 0); boss.beginDebug(player); mc.overworld().addFreshEntity(boss); debugBoss.set(boss);
                check(boss.forceDebugAttack(player, "sword_combo"), "Cannot force sword attack");
            });
            context.runOnClient(client -> { client.player.setYRot(90); client.player.setXRot(-18); });
            context.waitTicks(10);
            context.takeScreenshot("minotaur-pull-swords-out");
            context.waitTicks(MinotaurEntity.DRAW_SWORD_TICKS - 10 + 5);
            server.runOnServer(mc -> check(debugBoss.get().weaponMode() == 2, "Swords were not drawn"));
            context.takeScreenshot("minotaur-swords-drawn");
            server.runOnServer(mc -> {
                finishWeaponTestAttack(debugBoss.get());
                debugBoss.get().forceDebugAttack(mc.getPlayerList().getPlayers().getFirst(), "axe_throw");
            });
            context.waitTicks(8);
            server.runOnServer(mc -> check(!debugBoss.get().axeInWorld() && debugBoss.get().weaponSwapTicks() > 0, "Axe attack skipped sheathing swords"));
            context.waitTicks(60);
            server.runOnServer(mc -> {
                check(debugBoss.get().axeInWorld(), "Axe was not thrown into the world");
                finishWeaponTestAttack(debugBoss.get());
                debugBoss.get().forceDebugAttack(mc.getPlayerList().getPlayers().getFirst(), "cleave");
                check(debugBoss.get().debugStatus().contains("RETRIEVE_AXE"), "Cleave bypassed missing axe");
                finishWeaponTestAttack(debugBoss.get());
                debugBoss.get().forceDebugAttack(mc.getPlayerList().getPlayers().getFirst(), "sword_combo");
            });
            context.waitTicks(MinotaurEntity.DRAW_SWORD_TICKS + 5);
            server.runOnServer(mc -> {
                var boss = debugBoss.get();
                check(boss.weaponMode() == 2 && boss.axeInWorld(), "Swords unavailable while axe was in world");
                finishWeaponTestAttack(boss);
                try {
                    var id = (java.util.UUID)read(boss, "thrownAxe");
                    var axe = mc.overworld().getEntity(id);
                    check(axe != null, "Thrown axe disappeared");
                    check(axe instanceof net.krodark.asterion.entity.MinotaurAxeEntity, "Axe still uses dropped-item physics");
                    boss.setPos(axe.position().add(-1, 0, 0));
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                boss.forceDebugAttack(mc.getPlayerList().getPlayers().getFirst(), "retrieve_axe");
            });
            context.waitTicks(25);
            server.runOnServer(mc -> {
                var boss = debugBoss.get(); var player = mc.getPlayerList().getPlayers().getFirst();
                check(!boss.axeInWorld() && boss.weaponMode() == 1, "Axe was not retrieved");
                finishWeaponTestAttack(boss);
                boss.setPos(13, 121, 0); player.teleportTo(17, 121, 0);
                for (String removed : new String[]{"wall_shove", "red_lightning_charge", "arena_sweep"}) {
                    check(!MinotaurEntity.debugAttackNames().contains(removed), "Removed attack still suggested: " + removed);
                    check(!boss.forceDebugAttack(player, removed), "Removed attack still executable: " + removed);
                }
                try {
                    var rage = MinotaurEntity.class.getDeclaredMethod("setRage", int.class); rage.setAccessible(true);
                    rage.invoke(boss, 0); double calm = boss.rageCooldownMultiplier();
                    rage.invoke(boss, 12); check(boss.rageCooldownMultiplier() < calm * .5, "Rage did not reduce cooldowns");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                boss.stopDebug();
            });
            Asterion.LOGGER.info("PASS: weapon draw/sheath, physical axe throw/retrieval, missing-axe gating, removed attack rejection and rage cooldown scaling");
            server.runOnServer(mc -> {
                var level = mc.overworld();
                // A spinning body must hit a one-block wall, settle above the floor and remain retrievable.
                for (int y = 121; y <= 140; y++) for (int z = -26; z <= -14; z++)
                    level.setBlock(new BlockPos(12, y, z), Blocks.STONE.defaultBlockState(), 2);
                var axe = new net.krodark.asterion.entity.MinotaurAxeEntity(Asterion.MINOTAUR_AXE, level);
                axe.launch(new Vec3(0, 131, -20), new Vec3(1.7, .25, 0), 90);
                level.addFreshEntity(axe);
                var initial = axe.renderRotation(1);
                for (int tick = 0; tick < 360; tick++) {
                    axe.tick();
                    check(Double.isFinite(axe.position().lengthSqr()) && axe.renderRotation(1).isFinite(), "Axe physics became non-finite");
                    check(axe.getX() < 12.1, "Axe tunneled through a wall");
                    check(axe.getBoundingBox().minY >= 120.98, "Axe clipped below floor");
                    if (tick == 4) check(Math.abs(initial.dot(axe.renderRotation(1))) < .99, "Thrown axe did not spin");
                }
                check(axe.sleeping(), "Axe never settled");
                check(!axe.isRemoved(), "Resting axe vanished before retrieval");
                Vec3 restingPosition = axe.position();
                Vec3 restingVelocity = axe.getDeltaMovement();
                WorldGenerator.explodeBossRubble(level, restingPosition);
                level.explode(null, axe.getX() + 1, axe.getY() + 1, axe.getZ(), 6,
                        net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                check(axe.getDeltaMovement().equals(restingVelocity), "Explosion accelerated the recoverable axe");
                axe.tick();
                check(axe.sleeping() && axe.position().equals(restingPosition) && !axe.isRemoved(),
                        "Phase-two/explosion disturbed the settled axe");
                axe.discard();
            });
            Asterion.LOGGER.info("PASS: axe spin, swept wall/floor collision, stable settling, persistence and explosion immunity");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.teleportTo(3, 121, -30); player.resetFallDistance(); player.setHealth(100);
                player.getFoodData().setFoodLevel(10); player.getFoodData().setSaturation(0);
                var boss = Asterion.MINOTAUR.create(mc.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0, 121, -30); boss.beginDebug(player); boss.setDebugRunning(false);
                mc.overworld().addFreshEntity(boss); debugBoss.set(boss);
                // The trigger counts actual health loss, after the Minotaur's armor.
                boss.hurtServer(mc.overworld(), player.damageSources().playerAttack(player), 24);
                try {
                    var trigger = MinotaurEntity.class.getDeclaredMethod("shouldHornRam", net.minecraft.server.level.ServerPlayer.class);
                    trigger.setAccessible(true);
                    check((boolean)trigger.invoke(boss, player), "Close damage burst did not prioritize horns");
                    player.teleportTo(14, 121, -30);
                    check(!(boolean)trigger.invoke(boss, player), "Distant target triggered defensive horn ram");
                    player.teleportTo(3, 121, -30);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                boss.forceDebugAttack(player, "horn_ram");
            });
            context.waitTicks(55);
            server.runOnServer(mc -> {
                var boss = debugBoss.get(); var player = mc.getPlayerList().getPlayers().getFirst();
                check(player.getHealth() == 93, "Horn ram did not deal exactly 7 base damage: " + player.getHealth());
                try {
                    double travel = (double)read(boss, "hornTravel");
                    check(travel >= 7 && travel <= 10.001, "Horn knockback outside 7–10 blocks: " + travel);
                    check((int)read(boss, "wallComboWindow") == 0 && (int)read(boss, "airborneCatchWindow") == 0,
                            "Horn ram scheduled a follow-up combo");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                check(!MinotaurEntity.controlsPlayer(player), "Horn ram left movement locked");
                boss.stopDebug();
            });
            Asterion.LOGGER.info("PASS: close burst horn trigger, range gate, 7 damage, 7–10 block ragdoll knockback, no scheduled combo and control release");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.CREATIVE); player.teleportTo(0, 121, -12);
                var boss = Asterion.MINOTAUR.create(mc.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0, 121, 0); boss.beginDebug(player); boss.setDebugRunning(false);
                mc.overworld().addFreshEntity(boss); debugBoss.set(boss);
            });
            context.runOnClient(client -> { client.player.setYRot(0); client.player.setXRot(-18); });
            context.waitTicks(10);
            context.takeScreenshot("minotaur-custom-axe-back");
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(10, 121, -5));
            context.runOnClient(client -> { client.player.setYRot(63.5F); client.player.setXRot(-18); });
            context.waitTicks(4);
            context.takeScreenshot("minotaur-custom-swords-hips");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst(); player.teleportTo(0, 121, 12);
                debugBoss.get().forceDebugAttack(player, "cleave");
            });
            context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(-18); });
            context.waitTicks(12);
            context.takeScreenshot("minotaur-pull-axe-from-back");
            context.waitTicks(MinotaurEntity.DRAW_AXE_TICKS - 12 + 5);
            context.takeScreenshot("minotaur-custom-axe-hand");
            context.runOnClient(client -> check(net.krodark.asterion.client.render.BossGroundTelegraphRenderer.activeCount() > 0,
                    "Weapon wind-up did not send a ground damage warning"));
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(0, 132, 15));
            context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(40); });
            context.waitTicks(2);
            context.takeScreenshot("minotaur-ground-cleave-warning");
            server.runOnServer(mc -> {
                mc.getPlayerList().getPlayers().getFirst().teleportTo(0, 121, 12);
                finishWeaponTestAttack(debugBoss.get());
                debugBoss.get().forceDebugAttack(mc.getPlayerList().getPlayers().getFirst(), "axe_throw");
            });
            context.waitTicks(19);
            context.takeScreenshot("minotaur-custom-axe-flight");
            server.runOnServer(mc -> debugBoss.get().stopDebug());
            context.waitTicks(5);
            context.runOnClient(client -> check(net.krodark.asterion.client.render.BossGroundTelegraphRenderer.activeCount() == 0,
                    "Cancelled/dead boss left a damage warning behind"));
            server.runOnServer(mc -> mc.getPlayerList().getPlayers().getFirst().teleportTo(0, 132, 15));
            context.runOnClient(client -> {
                client.player.setYRot(180); client.player.setXRot(40);
                net.krodark.asterion.client.render.BossGroundTelegraphRenderer.receive(new net.krodark.asterion.network.BossTelegraphPayload(
                        new Vec3(0, 121, 0), new Vec3(0, 0, 1), 8, 12,
                        net.krodark.asterion.network.BossTelegraphPayload.TARGET_CIRCLE, client.player.getId(), (float)(Math.PI * 2), 0, 0));
            });
            context.waitTicks(2);
            context.takeScreenshot("minotaur-ground-circle-warning");
            context.runOnClient(client -> net.krodark.asterion.client.render.BossGroundTelegraphRenderer.receive(
                    new net.krodark.asterion.network.BossTelegraphPayload(new Vec3(0, 121, -10), new Vec3(0, 0, 1), 24, 8,
                            net.krodark.asterion.network.BossTelegraphPayload.CHARGE_LANE, client.player.getId(), 0, 2, 0)));
            context.waitTicks(2);
            context.takeScreenshot("minotaur-ground-charge-warning");
            context.waitTicks(10);
            context.runOnClient(client -> check(net.krodark.asterion.client.render.BossGroundTelegraphRenderer.activeCount() == 0,
                    "Expired ground warning was not removed"));
            Asterion.LOGGER.info("PASS: weapon ground warning delivery, cancellation, circle/lane rendering and expiry");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(0, 121, 5); player.setGameMode(GameType.SURVIVAL);
                player.setHealth(100); player.invulnerableTime = 0;
                var boss = Asterion.MINOTAUR.create(mc.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0, 121, 0); boss.beginDebug(player); mc.overworld().addFreshEntity(boss); debugBoss.set(boss);
                check(boss.forceDebugAttack(player, "axe_chop"), "Overhead chop not available in debug commands");
                boss.setDebugRunning(false);
                tickWeaponAttack(boss, player, MinotaurEntity.DRAW_AXE_TICKS + 17);
                check(boss.bossAttackAnimationTicks() == 17 && player.getHealth() == 100, "Chop dealt damage during wind-up");
                player.setGameMode(GameType.CREATIVE); player.teleportTo(10, 124, 12);
            });
            context.runOnClient(client -> { client.player.setYRot(140); client.player.setXRot(-12); });
            context.waitTicks(7);
            context.takeScreenshot("minotaur-axe-overhead-windup");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(0, 121, 5); player.setGameMode(GameType.SURVIVAL); player.invulnerableTime = 0;
                tickWeaponAttack(debugBoss.get(), player, MinotaurEntity.AXE_CHOP_HIT_TICK - 18);
                check(player.getHealth() == 100, "Chop hit before its downswing contact");
                tickWeaponAttack(debugBoss.get(), player, 1);
                check(player.getHealth() == 82, "Chop did not hit the forward lane at its downswing contact");
                player.setGameMode(GameType.CREATIVE); player.teleportTo(10, 124, 12);
            });
            context.waitTicks(7);
            context.takeScreenshot("minotaur-axe-overhead-impact");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.teleportTo(3, 121, 5); player.setHealth(100); player.invulnerableTime = 0;
                try {
                    var chop = MinotaurEntity.class.getDeclaredMethod("performAxeChop", net.minecraft.server.level.ServerLevel.class);
                    chop.setAccessible(true); chop.invoke(debugBoss.get(), mc.overworld());
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                check(player.getHealth() == 100, "Sidestepping the chop lane still took damage");
                debugBoss.get().stopDebug();
            });
            Asterion.LOGGER.info("PASS: overhead axe chop wind-up, authored downswing impact, 18 damage and safe sidestep lane");
            server.runOnServer(mc -> {
                var level = mc.overworld(); var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.setHealth(100); player.invulnerableTime = 0;
                player.teleportTo(0, 125, -20); player.setDeltaMovement(Vec3.ZERO);
                var axe = new net.krodark.asterion.entity.MinotaurAxeEntity(Asterion.MINOTAUR_AXE, level);
                axe.launch(new Vec3(0, 124, -24), new Vec3(0, .08, 1.6), 0);
                level.addFreshEntity(axe);
                for (int tick = 0; tick < 8; tick++) axe.tick();
                check(player.getHealth() == 80, "Swept axe blade did not deal 20 damage: " + player.getHealth());
                player.invulnerableTime = 0;
                for (int tick = 0; tick < 8; tick++) axe.tick();
                check(player.getHealth() == 80, "Same throw damaged player more than once");
                axe.discard();
                player.setHealth(100); player.invulnerableTime = 0; player.teleportTo(0, 121, -20);
                player.setDeltaMovement(Vec3.ZERO);
                var aimed = new net.krodark.asterion.entity.MinotaurAxeEntity(Asterion.MINOTAUR_AXE, level);
                aimed.launchAimed(new Vec3(0, 127, -40), new Vec3(0, 121 + 3.15 * aimed.modelScale(), -20), 0, 14);
                level.addFreshEntity(aimed);
                for (int tick = 0; tick < 24; tick++) {
                    aimed.tick();
                    if (tick >= 9 && tick <= 17) Asterion.LOGGER.info("Axe flight check tick={} position={} velocity={} bounds={} health={}", tick,
                            aimed.position(), aimed.getDeltaMovement(), aimed.getBoundingBox(), player.getHealth());
                }
                check(player.getHealth() < 100, "Aimed physical axe missed a stationary grounded player");
                aimed.discard(); player.setGameMode(GameType.CREATIVE); player.setHealth(100);
                player.teleportTo(10, 125, 12); player.setYRot(140); player.setXRot(8);
                var boss = Asterion.MINOTAUR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0, 121, 0); boss.beginDebug(player); boss.setDebugRunning(false); boss.setNoAi(true);
                level.addFreshEntity(boss); debugBoss.set(boss);
                try {
                    var begin = MinotaurEntity.class.getDeclaredMethod("beginCollapse", net.minecraft.server.level.ServerLevel.class);
                    begin.setAccessible(true); begin.invoke(boss, level);
                    var tick = MinotaurEntity.class.getDeclaredMethod("tickCollapse", net.minecraft.server.level.ServerLevel.class, net.minecraft.server.level.ServerPlayer.class);
                    tick.setAccessible(true); for (int i = 0; i < 90; i++) tick.invoke(boss, level, player);
                    check(boss.collapseAnimationTicks() == 90 && boss.getY() >= 121, "Collapse did not hold grounded pose");
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            context.waitTicks(8);
            context.takeScreenshot("minotaur-collapsed-rubble");
            context.runOnClient(client -> { PhysicsDebrisSystem.clear(); client.particleEngine.clearParticles(); });
            context.takeScreenshot("minotaur-authored-dies-pose");
            server.runOnServer(mc -> debugBoss.get().stopDebug());
            Asterion.LOGGER.info("PASS: swept physical axe blade damage, single hit per throw and grounded collapse pose");

            server.runOnServer(mc -> {
                var level = mc.overworld(); var player = mc.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL); player.setHealth(100); player.invulnerableTime = 0;
                player.teleportTo(0, 121, 12); player.setDeltaMovement(Vec3.ZERO);
                var boss = Asterion.MINOTAUR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0, 121, 0); boss.setYRot(0); boss.setYHeadRot(0); boss.beginDebug(player);
                level.addFreshEntity(boss); debugBoss.set(boss);
                check(boss.forceDebugAttack(player, "chain_grapple"), "Chain debug attack unavailable");
                boss.setDebugRunning(false);
                tickWeaponAttack(boss, player, 24);
                check(boss.bossAttackAnimationTicks() == 24, "Chain clock included a weapon swap");
                check(player.getDeltaMovement().equals(Vec3.ZERO), "Chain pulled before frame 30");
                check(boss.grabTargetEntityId() == player.getId() && boss.isChainGrappleActive(), "Chain endpoint not synchronized");
            });
            context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(-12); });
            context.waitTicks(4);
            context.runOnClient(client -> {
                var boss = client.level.getEntitiesOfClass(MinotaurEntity.class, client.player.getBoundingBox().inflate(32)).getFirst();
                var model = new net.krodark.asterion.client.render.entity.MinotaurGeoModel();
                for (String clip : new String[]{"roar", "roar_start", "walk", "run", "charge_start", "run charge attack",
                        "leep", "chain_grapple", "punch_single", "punch combo", "swing_swords_combo",
                        "swing_axe_horizontal", "swing_axe_vertical", "axe_throw", "pull_sword_out", "pull_axe_from_back",
                        "rubble_throw", "dies", "asterion_sheathe_swords", "asterion_sheathe_axe", "asterion_revive", "asterion_smoke_belch"})
                    check(model.getBakedAnimation(boss, clip) != null, "Missing baked Minotaur animation: " + clip);
            });
            context.takeScreenshot("minotaur-mazesteel-chain-frame29");
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst(); var boss = debugBoss.get();
                player.setDeltaMovement(Vec3.ZERO);
                tickWeaponAttack(boss, player, 1);
                Vec3 yank = player.getDeltaMovement();
                check(yank.z < -2 && yank.y > .1, "Frame 30 did not strongly yank the player toward the Minotaur");
                tickWeaponAttack(boss, player, 5);
                check(player.getDeltaMovement().equals(yank), "Grapple repeatedly pulled after its one yank");
                try {
                    var flight = MinotaurEntity.class.getDeclaredMethod("tickThrownPlayer", net.minecraft.server.level.ServerLevel.class);
                    flight.setAccessible(true);
                    for (int i = 0; i < 10; i++) flight.invoke(boss, mc.overworld());
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                double gap = player.position().subtract(boss.position()).horizontalDistance();
                check(gap >= 2.8 && gap <= 3.2, "Yank did not arrive within the boss's catch range: " + gap);
                check(!MinotaurEntity.controlsPlayer(player), "Yank did not release movement at arrival");
                // Resolve landing, then transfer the single yank into the hand without another impulse.
                player.teleportTo(player.getX(), 121, player.getZ()); player.setDeltaMovement(Vec3.ZERO);
                tickWeaponAttack(boss, player, 6);
                check(boss.debugStatus().contains("attack=GRAB"), "Close grapple did not combo into grab");
                check(boss.heldPlayerId() == player.getId() && !boss.isChainGrappleActive(), "Yank did not transfer to the hand");
                tickWeaponAttack(boss, player, 39);
                check(player.getHealth() == 100, "Grab damaged the player before release");
                check(boss.heldPlayerId() == player.getId(), "Combo lost its held player");
                player.invulnerableTime = 0;
                tickWeaponAttack(boss, player, 1);
                check(player.getHealth() == 90 && boss.heldPlayerId() == -1,
                        "Combo throw failed to release/damage player: " + player.getHealth());
                check(player.getDeltaMovement().horizontalDistance() >= 5.4, "Combo throw lost its launch impulse");
                boss.stopDebug(); player.setGameMode(GameType.CREATIVE);
                verifyLocomotion(mc.overworld(), player, MinotaurEntity.BehaviorPhase.ROAMING, 4);
                verifyLocomotion(mc.overworld(), player, MinotaurEntity.BehaviorPhase.CHASING, 7);
            });
            Asterion.LOGGER.info("PASS: custom chain endpoint, frame-30 single yank, grab/throw combo and measured walk/run speed");

            for (String laneAttack : new String[]{"charge", "horn_ram", "stampede"}) {
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst();
                    player.setGameMode(GameType.CREATIVE); player.teleportTo(0, 121, 18); player.setDeltaMovement(Vec3.ZERO);
                    var boss = Asterion.MINOTAUR.create(mc.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    boss.setPos(0, 121, 0); boss.beginDebug(player); mc.overworld().addFreshEntity(boss); debugBoss.set(boss);
                    check(boss.forceDebugAttack(player, laneAttack), "Charge variant unavailable: " + laneAttack);
                });
                context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(6); });
                context.waitTicks(8);
                context.runOnClient(client -> check(net.krodark.asterion.client.render.BossGroundTelegraphRenderer.activeCount() > 0,
                        "Charge variant did not deliver its ground lane: " + laneAttack));
                if (laneAttack.equals("charge")) {
                    var clips = new java.util.HashSet<String>();
                    for (int frame = 0; frame < 40; frame++) {
                        context.waitTicks(1);
                        context.runOnClient(client -> clips.add(assertActiveAnimation(client, debugBoss.get().getId())));
                    }
                    check(clips.contains("charge_start") && clips.contains("run charge attack"),
                            "Charge did not hand off from windup to the authored run: " + clips);
                    context.takeScreenshot("minotaur-charge-door-smoke");
                }
                server.runOnServer(mc -> debugBoss.get().stopDebug());
                context.waitTicks(4);
            }
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                player.teleportTo(0, 121, 18); player.setDeltaMovement(Vec3.ZERO);
                var boss = Asterion.MINOTAUR.create(mc.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                boss.setPos(0, 121, 0); boss.beginDebug(player); mc.overworld().addFreshEntity(boss); debugBoss.set(boss);
                check(boss.forceDebugAttack(player, "smoke_belch"), "Smoke belch unavailable");
            });
            context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(-5); });
            context.waitTicks(32);
            context.takeScreenshot("minotaur-smoke-belch");
            context.waitTicks(125);
            context.takeScreenshot("minotaur-smoke-ignited");
            server.runOnServer(mc -> debugBoss.get().stopDebug());
            Asterion.LOGGER.info("PASS: all charge lane payloads, charge billows, live mouth animation and delayed smoke ignition");

            for (String heldAttack : new String[]{"axe_throw", "sword_combo", "rubble_throw"}) {
                server.runOnServer(mc -> {
                    var player = mc.getPlayerList().getPlayers().getFirst(); player.teleportTo(0, 121, 18);
                    var boss = Asterion.MINOTAUR.create(mc.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    boss.setPos(0, 121, 0); boss.beginDebug(player); mc.overworld().addFreshEntity(boss); debugBoss.set(boss);
                    check(boss.forceDebugAttack(player, heldAttack), "Held-pose test attack unavailable");
                    tickWeaponAttack(boss, player, heldAttack.equals("axe_throw") ? MinotaurEntity.DRAW_AXE_TICKS + 16
                            : heldAttack.equals("sword_combo") ? MinotaurEntity.DRAW_SWORD_TICKS + 20 : 30);
                    boss.setDebugRunning(false);
                    if (heldAttack.equals("axe_throw")) check(boss.axeInWorld(), "Release frame did not spawn a physical axe");
                });
                context.runOnClient(client -> { client.player.setYRot(180); client.player.setXRot(-8); });
                context.waitTicks(6);
                for (int frame = 0; frame < 20; frame++) {
                    context.waitTicks(4);
                    context.runOnClient(client -> assertActiveAnimation(client, debugBoss.get().getId()));
                }
                context.takeScreenshot("minotaur-held-" + heldAttack);
                server.runOnServer(mc -> debugBoss.get().stopDebug());
            }
            Asterion.LOGGER.info("PASS: frame-by-frame charge handoff and expired axe/sword/rubble clips hold their poses without resetting");

        }
    }
    private static String assertActiveAnimation(net.minecraft.client.Minecraft client, int id) {
        var boss = (MinotaurEntity)client.level.getEntity(id);
        check(boss != null, "Animated boss disappeared");
        var controller = boss.getAnimatableInstanceCache().getManagerForId(id).getAnimationControllers().get("movement");
        check(controller != null && controller.isAnimatingBones(), "Controller dropped the active pose");
        check(controller.getCurrentTimelineTime() < controller.getTimeline().lastAnimationEndTime(),
                "Controller entered its T-pose/reset tail: " + controller.getCurrentRawAnimation());
        return controller.getCurrentRawAnimation().getAnimationStages().getFirst().animationName();
    }

    private static void verifyLocomotion(net.minecraft.server.level.ServerLevel level,
            net.minecraft.server.level.ServerPlayer player, MinotaurEntity.BehaviorPhase phase, double expected) {
        var boss = Asterion.MINOTAUR.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        boss.setPos(-20, 121, -50); boss.setYRot(0); boss.setOnGround(true);
        boss.beginDebug(player); boss.setDebugRunning(false);
        try {
            var setPhase = MinotaurEntity.class.getDeclaredMethod("setBehaviorPhase", MinotaurEntity.BehaviorPhase.class);
            setPhase.setAccessible(true); setPhase.invoke(boss, phase);
            double start = 0;
            for (int tick = 0; tick < 60; tick++) {
                boss.getMoveControl().setWantedPosition(-20, 121, 20, 1.4);
                boss.getMoveControl().tick();
                // Vanilla applies input damping immediately before travel; exercise native collisions and drag.
                boss.travel(new Vec3(0, 0, boss.zza * .98F));
                if (tick == 19) start = boss.getZ();
            }
            double measured = (boss.getZ() - start) / 2;
            check(Math.abs(measured - expected) < .08, phase + " speed should be " + expected + " blocks/s, got " + measured);
            Asterion.LOGGER.info("Measured Minotaur {} speed: {} blocks/s", phase, measured);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { boss.discard(); }
    }
    private static int bossBarCount(net.minecraft.client.Minecraft client) {
        try { return ((java.util.Map<?, ?>)read(client.gui.getBossOverlay(), "events")).size(); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static boolean gateExists(net.minecraft.server.level.ServerLevel level, Direction facing) {
        BlockPos center = facing == Direction.NORTH
                ? MinotaurArenaEntrances.AUTHORED_BOSS_GATE
                : MinotaurArenaEntrances.gate(facing).below();
        for (int row = 0; row < 7; row++) for (int side = -3; side <= 3; side++)
            if (level.getBlockState(center.relative(facing.getClockWise(), side).above(row)).is(Asterion.MAZESTEEL_GATE)) return true;
        return false;
    }
    private static boolean gateIsOpen(net.minecraft.server.level.ServerLevel level, Direction facing) {
        BlockPos center = facing == Direction.NORTH
                ? MinotaurArenaEntrances.AUTHORED_BOSS_GATE
                : MinotaurArenaEntrances.gate(facing).below();
        boolean found = false;
        for (int row = 0; row < 7; row++) for (int side = -3; side <= 3; side++) {
            var state = level.getBlockState(center.relative(facing.getClockWise(), side).above(row));
            if (!state.is(Asterion.MAZESTEEL_GATE)) continue;
            found = true;
            if (!state.getValue(DirectionalGateBlock.OPEN)) return false;
        }
        return found;
    }
    private static void tickWeaponAttack(MinotaurEntity boss, net.minecraft.server.level.ServerPlayer player, int ticks) {
        try {
            var tick = MinotaurEntity.class.getDeclaredMethod("tickBossAttack", net.minecraft.server.level.ServerLevel.class, net.minecraft.server.level.ServerPlayer.class);
            tick.setAccessible(true);
            for (int i = 0; i < ticks; i++) tick.invoke(boss, player.level(), player);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void finishWeaponTestAttack(MinotaurEntity boss) {
        try {
            var finish = MinotaurEntity.class.getDeclaredMethod("finishBossAttack", int.class);
            finish.setAccessible(true); finish.invoke(boss, 0);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private record DoorSample(Vec3 position, Vec3 velocity, boolean sleeping) { }
    private static java.util.List<DoorSample> doorSamples() {
        try {
            var field = PhysicsDebrisSystem.class.getDeclaredField("PIECES");
            field.setAccessible(true);
            var result = new java.util.ArrayList<DoorSample>();
            for (Object piece : (java.util.List<?>)field.get(null))
                if ((int)read(piece, "variant") == 7)
                    result.add(new DoorSample((Vec3)read(piece, "position"), (Vec3)read(piece, "velocity"), (boolean)read(piece, "sleeping")));
            return result;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static Object read(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
