import net.krodark.asterion.client.render.*;

public final class TextureCacheLifecycleSmoke {
    private static final class Owner implements TextureCacheOwner {
        Runnable cleanup;
        public void asterion$setFrameCleanup(Runnable value) { cleanup = value; TextureFrameCaches.track(this); }
        public void asterion$markUsed() { }
        public void asterion$releaseFrameCache() { if (cleanup != null) { cleanup.run(); cleanup = null; } }
    }
    public static void main(String[] args) {
        Owner owner = new Owner();
        for (int world = 0; world < 50; world++) {
            if (!TextureFrameBudget.reserve(4096)) throw new AssertionError("Leaked frame budget");
            owner.asterion$setFrameCleanup(() -> TextureFrameBudget.release(4096));
            TextureFrameCaches.releaseAll();
            TextureFrameCaches.releaseAll();
            if (TextureFrameBudget.usedBytes() != 0) throw new AssertionError("World transition retained frames");
        }
        if (!SodiumVisibility.lightVisible(net.minecraft.world.phys.Vec3.ZERO, 8))
            throw new AssertionError("Absent Sodium must keep lights visible");
        System.out.println("PASS 50 cache release/repopulate cycles, idempotent cleanup, and no-Sodium lighting fallback");
    }
}
