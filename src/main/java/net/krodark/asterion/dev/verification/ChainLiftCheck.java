package net.krodark.asterion.dev.verification;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.ChainLiftBlockEntity;
import net.krodark.asterion.entity.ChainLiftEntity;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;

final class ChainLiftCheck {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var clock = (ServerLevelData)level.getLevelData();
        long now = level.getGameTime();
        var connection = server.getPlayerList().getPlayers().getFirst().connection;
        var first = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "LiftFirst"), ClientInformation.createDefault());
        var second = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "LiftSecond"), ClientInformation.createDefault());
        first.connection = connection; second.connection = connection;
        BlockPos base = new BlockPos(0, 210, 0);
        level.getChunkAt(base);
        for (BlockPos p : BlockPos.betweenClosed(-1,-49,-1,1,-41,1)) level.setBlock(p, Blocks.AIR.defaultBlockState(),18);
        level.setBlock(new BlockPos(0,-40,0), Blocks.STONE.defaultBlockState(),18);
        check(ChainLiftBlockEntity.findCeiling(level, new BlockPos(0,-50,0)) == -40, "Lift rejected a ceiling below sea level");
        var state = ChainLiftContent.ANCHOR.defaultBlockState();
        level.setBlock(base, state, 18);
        for (BlockPos p : BlockPos.betweenClosed(-1, 221, -1, 1, 221, 1)) level.setBlock(p, Blocks.STONE.defaultBlockState(), 18);
        var anchor = (ChainLiftBlockEntity)level.getBlockEntity(base);
        ChainLiftEntity lift = null;
        try {
            clock.setGameTime(now - now % 20);
            ChainLiftBlockEntity.tick(level, base, state, anchor);
            var area = new AABB(-2,209,-2,3,223,3);
            var lifts = level.getEntitiesOfClass(ChainLiftEntity.class, area);
            check(lifts.size() == 1, "Lift anchor failed to spawn"); lift = lifts.getFirst();
            var restoredAnchor = (ChainLiftBlockEntity)BlockEntity.loadStatic(base, state, anchor.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
            ChainLiftBlockEntity.tick(level, base, state, restoredAnchor);
            check(level.getEntitiesOfClass(ChainLiftEntity.class, area).size() == 1, "Reloaded anchor duplicated lift");
            check(Math.abs(lift.getY() - lift.topY()) < .001, "Lift did not spawn at its upper home stop");
            first.setPos(0.05, lift.topY() + .5, .5); second.setPos(0.95, lift.topY() + .5, .5);
            level.addNewPlayer(first); level.addNewPlayer(second);
            long start = level.getGameTime();
            for (int tick = 0; tick < 180; tick++) {
                clock.setGameTime(start + tick);
                 
                double x = 0.05 + .15 * Math.sin(tick * .03);
                first.setPos(x, first.getY(), first.getZ());
                lift.tick();
                check(Math.abs(first.getY() - lift.getY() - .5) < .001 && Math.abs(second.getY() - lift.getY() - .5) < .001,
                        "Lift failed to carry two players");
                check(first.getX() == x && !first.isPassenger() && !second.isPassenger(), "Lift locked horizontal movement");
                if (tick < 100) check(lift.getY() == lift.topY(), "Lift started before the boarding delay");
            }
            check(lift.getY() < lift.topY() - 2, "Lift never descended after players boarded");
            var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            lift.saveWithoutId(saved);
            var restored = ChainLiftContent.LIFT.create(level, EntitySpawnReason.LOAD);
            restored.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            check(restored.anchor().equals(base) && Math.abs(restored.getY()-lift.getY()) < .0001 && restored.topY() == 218,
                    "Lift journey or anchor lost on reload");
            BlockPos obstacle = new BlockPos(0, (int)Math.ceil(lift.getY()+3), 0);
            level.setBlock(obstacle, Blocks.STONE.defaultBlockState(), 18);
            for (int tick = 180; tick < 260; tick++) { clock.setGameTime(start + tick); lift.tick(); }
            check(lift.getY()+3 <= obstacle.getY()+.001 && level.getBlockState(obstacle).is(Blocks.STONE), "Lift cut through an obstruction");
            level.setBlock(base, Blocks.AIR.defaultBlockState(), 18); lift.tick();
            check(lift.isRemoved(), "Breaking the base left a floating lift");
            Asterion.LOGGER.info("PASS: lift starts up, waits for two players, descends in sync, saves position, avoids duplicates and survives obstruction/base removal checks");
        } finally {
            if (lift != null) lift.discard();
            level.removePlayerImmediately(first, Entity.RemovalReason.DISCARDED);
            level.removePlayerImmediately(second, Entity.RemovalReason.DISCARDED);
            clock.setGameTime(now);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
