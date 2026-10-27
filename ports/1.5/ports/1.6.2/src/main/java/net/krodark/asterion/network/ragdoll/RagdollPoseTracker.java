package net.krodark.asterion.network.ragdoll;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only pose ordering and bounded per-connection relay state. */
public final class RagdollPoseTracker {
    private static final int MAX_ENTITIES_PER_SENDER = 64;
    private static final int IDLE_TICKS = 200;
    private final Map<UUID, Map<Integer, Stamp>> senders = new HashMap<>();

    public boolean accept(UUID sender, int entity, int sequence, long serverTick) {
        Map<Integer, Stamp> poses = senders.computeIfAbsent(sender, ignored -> new HashMap<>());
        Stamp previous = poses.get(entity);
        if (previous != null && serverTick - previous.tick < IDLE_TICKS) {
            // Subtraction also handles the signed sequence counter wrapping.
            if (serverTick <= previous.tick || sequence - previous.sequence <= 0) return false;
        }
        if (previous == null && poses.size() >= MAX_ENTITIES_PER_SENDER) {
            poses.values().removeIf(stamp -> serverTick - stamp.tick >= IDLE_TICKS);
            if (poses.size() >= MAX_ENTITIES_PER_SENDER) return false;
        }
        poses.put(entity, new Stamp(sequence, serverTick));
        return true;
    }

    public void remove(UUID sender) { senders.remove(sender); }
    public void clear() { senders.clear(); }

    private record Stamp(int sequence, long tick) { }
}
