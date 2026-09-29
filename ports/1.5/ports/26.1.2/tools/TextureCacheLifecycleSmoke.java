import net.krodark.asterion.client.render.*;

public final class TextureCacheLifecycleSmoke {
    private static final class Owner implements TextureCacheOwner {
        Runnable cleanup;
        long lastUse;
        int released;
        public void asterion$setFrameCleanup(Runnable value) { cleanup = value; TextureFrameCaches.track(this); }
        public void asterion$markUsed() { lastUse = System.nanoTime(); }
        public long asterion$lastUse() { return lastUse; }
        public void asterion$releaseFrameCache() {
            if (cleanup != null) { cleanup.run(); cleanup = null; released++; }
            TextureFrameCaches.forget(this);
        }
    }
    private static void populate(Owner owner) {
        if (!TextureFrameBudget.reserve(4096)) throw new AssertionError("Leaked frame budget");
        owner.asterion$setFrameCleanup(() -> TextureFrameBudget.release(4096));
    }
    public static void main(String[] args) {
        Owner owner = new Owner();
        for (int world = 0; world < 50; world++) {
            populate(owner);
            TextureFrameCaches.releaseAll();
            TextureFrameCaches.releaseAll();
            if (TextureFrameBudget.usedBytes() != 0) throw new AssertionError("World transition retained frames");
        }
        long now = 20_000_000_000L;
        Owner idle = new Owner(), active = new Owner();
        idle.lastUse = 0; active.lastUse = now - 1;
        populate(idle); populate(active);
        TextureFrameCaches.trimIdle(now);
        if (idle.released != 1 || active.released != 0 || TextureFrameBudget.usedBytes() != 4096)
            throw new AssertionError("Idle sweep leaked old frames or evicted an active animation");
        TextureFrameCaches.trimIdle(now);
        if (idle.released != 1) throw new AssertionError("Double release");
        populate(idle); idle.lastUse = now;
        TextureFrameCaches.trimIdle(now + 1);
        if (TextureFrameBudget.usedBytes() != 8192) throw new AssertionError("Repopulated cache not retained");
        TextureFrameCaches.releaseAll();
        if (TextureFrameBudget.usedBytes() != 0) throw new AssertionError("Final cleanup leaked budget");
        if (!SodiumVisibility.lightVisible(net.minecraft.world.phys.Vec3.ZERO, 8))
            throw new AssertionError("Absent Sodium must keep lights visible");
        System.out.println("PASS 50 world transitions, idle-only eviction, repopulation and idempotent native-budget release");
    }
}
