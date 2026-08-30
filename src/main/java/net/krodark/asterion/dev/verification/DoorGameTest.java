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
        BlockPos root = new BlockPos(0, 121, 0);
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runOnServer(mc -> {
                check(WorldGenerator.isBossArenaReady(), "Arena was not completed before the player joined");
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(!maze.getBlockState(new BlockPos(0, 61, 0)).isAir(), "Arena still has an open pit instead of a roof");
                for (Direction removed : java.util.List.of(Direction.EAST, Direction.WEST)) {
                    check(!(maze.getBlockEntity(MinotaurArenaEntrances.door(removed)) instanceof MinotaurDoorBlockEntity),
                            "Obsolete side door survived the two-door layout");
                    check(!maze.getBlockState(MinotaurArenaEntrances.gate(removed)).is(Asterion.MAZESTEEL_GATE),
                            "Obsolete side gate survived the two-door layout");
                    for (int side = -3; side <= 3; side++) for (int y = 37; y <= 43; y++) {
                        BlockPos wall = MinotaurArenaEntrances.door(removed).relative(removed.getClockWise(), side).atY(y);
                        check(!maze.getBlockState(wall).getCollisionShape(maze, wall).isEmpty(), "Removed doorway is not sealed");
                    }
                }
                int roomTop = 36 + MinotaurArenaEntrances.gateHeight() + 1;
                for (int radius = 35; radius <= MinotaurArenaEntrances.BOSS_ROOM_BACK; radius++) {
                    for (int side = -4; side <= 4; side++) for (int y = 36; y <= roomTop; y++) {
                        boolean shell = Math.abs(side) == 4 || y == 36 || y == roomTop
                                || radius == MinotaurArenaEntrances.BOSS_ROOM_BACK;
                        BlockPos pos = new BlockPos(side, y, -radius);
                        check(maze.getBlockState(pos).getCollisionShape(maze, pos).isEmpty() != shell,
                                "Boss staging room has a shell gap or obstructed interior at " + pos);
                    }
                }
                var bossDoor = (MinotaurDoorBlockEntity)maze.getBlockEntity(MinotaurArenaEntrances.door(Direction.NORTH));
                bossDoor.interact(mc.getPlayerList().getPlayers().getFirst(), new ItemStack(Asterion.MINOTAUR_KEY));
                check(!bossDoor.allowsArenaEntry(), "The boss door accepted a player key");
                for (Direction facing : MinotaurArenaEntrances.DOORS) {
                    check(maze.getBlockEntity(MinotaurArenaEntrances.door(facing)) instanceof MinotaurDoorBlockEntity,
                            "Missing prebuilt door: " + facing);
                    for (int radius = 1; radius <= 33; radius++) for (int side = -3; side <= 3; side++)
                        for (int y = 37; y <= 42; y++) {
                            BlockPos pos = new BlockPos(facing.getStepX() * radius, y, facing.getStepZ() * radius)
                                    .relative(facing.getClockWise(), side);
                            check(maze.getBlockState(pos).getCollisionShape(maze, pos).isEmpty(), "Obstructed entrance lane at " + pos);
                        }
                }
                for (int count = 4; count <= 16; count++) for (BlockPos pillar : MinotaurArenaEntrances.pillarCenters(count))
                    for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
                        check(!MinotaurArenaEntrances.entranceLane(pillar.getX() + x, pillar.getZ() + z),
                                "Configured pillar base intrudes into an entrance lane");
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
                for (int x = -40; x <= 40; x++) for (int z = -40; z <= 40; z++)
                    level.setBlock(new BlockPos(x, 120, z), Blocks.STONE.defaultBlockState(), 3);
                player.setGameMode(GameType.CREATIVE);
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
                check(key.getCount() == 1, "Reusable key was consumed");
            });
            context.waitTicks(36);
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
            context.waitTicks(36);
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
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 37 0.5 180 0");
            context.waitTicks(20);
            server.runOnServer(mc -> check(!WorldGenerator.isBossEncounterActive(mc.getLevel(Asterion.ASTERION_LEVEL)),
                    "Being in the pit without a keyed entry started the fight"));
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 37 39.5 180 -4");
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
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 37 31 180 -4");
            context.waitTicks(4);
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(!WorldGenerator.isBossEncounterActive(maze), "Crossing a gate opened without a key started the fight");
                ((MinotaurDoorBlockEntity)maze.getBlockEntity(MinotaurArenaEntrances.door(Direction.SOUTH)))
                        .interact(mc.getPlayerList().getPlayers().getFirst(), ItemStack.EMPTY);
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 37 39.5 180 -4");
            context.waitTicks(32);
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                for (Direction facing : MinotaurArenaEntrances.DOORS)
                    check(level.getBlockEntity(MinotaurArenaEntrances.door(facing)) instanceof MinotaurDoorBlockEntity,
                            "Missing arena entrance: " + facing);
                var door = (MinotaurDoorBlockEntity)level.getBlockEntity(MinotaurArenaEntrances.door(Direction.SOUTH));
                door.interact(mc.getPlayerList().getPlayers().getFirst(), new ItemStack(Asterion.MINOTAUR_KEY));
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 37 39.5 180 -4");
            context.waitTicks(36);
            context.takeScreenshot("arena-player-entrance");
            context.runOnClient(client -> {
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                client.options.hideGui = false;
            });
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 37 31 180 -4");
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
                check(player.getZ() < 28, "Player was not moved to the inside of the gate");
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
                check(!maze.getBlockState(MinotaurArenaEntrances.gate(Direction.SOUTH)).getValue(DirectionalGateBlock.OPEN),
                        "The player's gate did not lower");
                check(maze.getBlockState(MinotaurArenaEntrances.gate(Direction.NORTH)).getValue(DirectionalGateBlock.OPEN),
                        "The boss gate closed before the Minotaur could enter");
                check(maze.noCollision(player), "The closing gate crushed the player");
            });
            context.takeScreenshot("arena-cinematic-rattle");
            context.waitTicks(27);
            context.takeScreenshot("arena-boss-kick");
            context.waitTicks(15);
            context.takeScreenshot("arena-boss-breach");
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(!level.getBlockState(MinotaurArenaEntrances.door(Direction.NORTH)).is(Asterion.MINOTAUR_DOOR),
                        "Boss entrance was not broken off");
            });
            context.waitTicks(45);
            server.runOnServer(mc -> {
                var level = mc.getLevel(Asterion.ASTERION_LEVEL);
                var player = mc.getPlayerList().getPlayers().getFirst();
                check(!BossArenaEncounter.isMovementLocked(player) && !player.isInvulnerable() && !player.isNoGravity(),
                        "Cinematic did not restore normal player state");
                for (Direction facing : MinotaurArenaEntrances.DOORS) {
                    check(level.getBlockState(MinotaurArenaEntrances.door(facing)).is(Asterion.MINOTAUR_DOOR), "A door failed to rebuild");
                    check(!level.getBlockState(MinotaurArenaEntrances.gate(facing)).getValue(DirectionalGateBlock.OPEN), "A fight gate remained open");
                }
                check(level.getBlockState(MinotaurArenaEntrances.door(Direction.SOUTH)).is(Asterion.MINOTAUR_DOOR),
                        "Boss broke the player's entrance too");
                boolean entered = false;
                for (var entity : level.getAllEntities()) if (entity instanceof MinotaurEntity boss)
                    entered |= boss.behaviorPhase() == MinotaurEntity.BehaviorPhase.BOSS
                            && boss.doorEntryTicks() == 0 && WorldGenerator.isInsideBossArena(boss.position());
                check(entered, "Boss did not finish entering the arena");
            });
            context.runOnClient(client -> {
                check(!BossEntranceCinematic.isActive() && !CinematicControls.locked(), "Client stayed frozen after the shot");
                check(client.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK
                        && !client.options.hideGui, "Cinematic did not restore the previous camera and HUD");
            });
            context.takeScreenshot("arena-gates-sealed");
            server.runCommand("execute in asterion:asterion_dimension run tp @a 0.5 37 23.5 0 -8");
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
            context.waitTicks(45);
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                check(wildCentipede.get().isRemoved(), "Wild centipede remained inside the fight");
                var beetles = maze.getEntitiesOfClass(net.krodark.asterion.entity.BombadierBeetleEntity.class,
                        new net.minecraft.world.phys.AABB(-44, 35, -44, 44, 62, 44));
                check(!beetles.isEmpty() && beetles.size() <= 4, "Beetle reinforcement wave missing or over cap");
                for (var beetle : beetles) check(maze.noCollision(beetle), "Reinforcement spawned in a block");
            });
            context.runOnClient(client -> PhysicsDebrisSystem.clear());
            server.runOnServer(mc -> {
                var maze = mc.getLevel(Asterion.ASTERION_LEVEL);
                BlockPos pillar = MinotaurArenaEntrances.pillarCenters(net.krodark.asterion.AsterionConfig.INSTANCE.minotaurBossPillarCount).getFirst();
                check(WorldGenerator.breakBossPillar(maze, new net.minecraft.world.phys.AABB(pillar).inflate(3, 12, 3)),
                        "Test pillar did not break");
                check(maze.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                        new net.minecraft.world.phys.AABB(-44, 35, -44, 44, 70, 44)).isEmpty(),
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
                check(WorldGenerator.resetBossEncounterAfterDeath(mc.getPlayerList().getPlayers().getFirst()), "Encounter did not reset");
                check(maze.getEntitiesOfClass(net.krodark.asterion.entity.BombadierBeetleEntity.class,
                        new net.minecraft.world.phys.AABB(-44, 35, -44, 44, 62, 44),
                        beetle -> beetle.entityTags().contains("asterion_arena_beetle")).isEmpty(), "Reset left summoned beetles behind");
                for (Direction facing : MinotaurArenaEntrances.DOORS) {
                    check(maze.getBlockEntity(MinotaurArenaEntrances.door(facing)) instanceof MinotaurDoorBlockEntity, "Wipe did not restore doors");
                    check(maze.getBlockState(MinotaurArenaEntrances.gate(facing)).getValue(DirectionalGateBlock.OPEN), "Wipe left players locked in");
                }
                BlockPos sentinel = new BlockPos(0, 37, 0);
                maze.setBlock(sentinel, Blocks.GOLD_BLOCK.defaultBlockState(), 2);
                WorldGenerator.clearRuntimeState(mc);
                WorldGenerator.prepareBossArenaBeforePlayers(maze);
                check(maze.getBlockState(sentinel).is(Blocks.GOLD_BLOCK), "Reloading arena bookkeeping rebuilt the saved chamber");
                BossArenaEncounter.initialize(maze);
                check(maze.getBlockState(MinotaurArenaEntrances.gate(Direction.NORTH)).getValue(DirectionalGateBlock.OPEN),
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
            context.waitTicks(20);
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
                boss.setPos(0, 121, 0);
                player.teleportTo(2, 123, 0);
                player.resetFallDistance();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                player.setHealth(100);
                player.getFoodData().setFoodLevel(0);
                player.getFoodData().setSaturation(0);
                for (int y = 121; y < 136; y++) for (int z = -15; z <= 15; z++)
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
            server.runOnServer(mc -> check(debugBoss.get().debugStatus().contains("WALL_SHOVE"), "Wall impact did not trigger pin pursuit"));
            server.runCommand("execute as @a at @s run asterion minotaur stop");
            server.runOnServer(mc -> check(!MinotaurEntity.controlsPlayer(mc.getPlayerList().getPlayers().getFirst()), "Stop left grab controls locked"));
            Asterion.LOGGER.info("PASS: airborne grab, hand attachment state, movement lock, 50–80 block throw and cleanup");

        }
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
