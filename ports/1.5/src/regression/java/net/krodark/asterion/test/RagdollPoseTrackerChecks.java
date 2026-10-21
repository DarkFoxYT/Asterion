package net.krodark.asterion.test;

import java.util.UUID;
import net.krodark.asterion.network.ragdoll.RagdollPoseTracker;

/** Runs with the Java 17, 21 and 25 release toolchains; no game/client classes. */
public final class RagdollPoseTrackerChecks {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var poses = new RagdollPoseTracker();
        UUID first = new UUID(0, 1), second = new UUID(0, 2);
        require(poses.accept(first, 1, 10, 1), "Initial pose must relay");
        require(!poses.accept(first, 1, 11, 1), "Duplicate tick must not relay");
        require(!poses.accept(first, 1, 9, 2), "Out-of-order pose must not relay");
        require(poses.accept(first, 1, 11, 2), "Next tick must relay");
        require(poses.accept(second, 1, 1, 2), "Connections must have independent counters");
        poses.remove(first);
        require(poses.accept(first, 1, 0, 2), "Reconnect must reset pose ordering");
        poses.clear();
        require(poses.accept(first, 1, Integer.MAX_VALUE, 1), "Sequence setup");
        require(poses.accept(first, 1, Integer.MIN_VALUE, 2), "Sequence wrap must relay");
        require(!poses.accept(first, 1, Integer.MAX_VALUE, 3), "Pre-wrap stale pose must be rejected");
        poses.clear();
        for (int entity = 0; entity < 64; entity++)
            require(poses.accept(first, entity, 1, 1), "Normal crowded-scene pose must relay");
        require(!poses.accept(first, 64, 1, 1), "Per-connection history must be bounded");
        require(poses.accept(first, 64, 0, 201), "Idle entity IDs must be reclaimed");
        require(poses.accept(first, 64, -10, 401), "Idle/reused entities must reset sequence history");

        UUID[] players = new UUID[128];
        for (int i = 0; i < players.length; i++) players[i] = new UUID(1, i);
        poses.clear();
        long start = System.nanoTime();
        long accepted = 0;
        for (int tick = 0; tick < 200; tick++)
            for (UUID player : players)
                for (int body = 0; body < 32; body++)
                    if (poses.accept(player, body, tick, tick)) accepted++;
        require(accepted == 819_200, "Crowded relay workload must not lose legitimate moving poses");
        System.out.println("RAGDOLL_RELAY PASS: ordering, sequence wrap, reconnect, bounded history; "
                + accepted + " accepted poses in " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }
}
