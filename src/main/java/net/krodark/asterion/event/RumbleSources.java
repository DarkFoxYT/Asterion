package net.krodark.asterion.event;

import java.util.random.RandomGenerator;
import net.krodark.asterion.AsterionConfig;
import net.krodark.asterion.worldgen.CatacombLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Bounded surface probes shared by server sounds and client debris. No sky fallback or chunk loading. */
public final class RumbleSources {
    public record Source(Vec3 position, Vec3 normal, BlockPos block) { }
    private RumbleSources() { }
    public static @Nullable Source find(Level level, Vec3 observer, RandomGenerator random) {
        boolean catacomb = observer.y >= 3 && observer.y <= CatacombLayout.ROOF_Y;
        boolean aboveWalls = observer.y >= 49 + AsterionConfig.INSTANCE.wallHeight - 1;
        for (int attempt = 0; attempt < 6; attempt++) {
            Vec3 start, end;
            if (catacomb || aboveWalls) {
                start = observer.add((random.nextDouble() - .5) * 8, .5, (random.nextDouble() - .5) * 8);
                end = start.add(0, catacomb ? Math.max(1, CatacombLayout.ROOF_Y + 1 - start.y) : -8, 0);
            } else {
                start = observer.add(0, 2 + random.nextDouble() * 4, 0);
                double angle = random.nextDouble() * Math.PI * 2;
                end = start.add(Math.cos(angle) * 14, 0, Math.sin(angle) * 14);
            }
            Source source = trace(level, start, end);
            if (source != null) return source;
        }
        return null;
    }
    public static @Nullable Source trace(Level level, Vec3 start, Vec3 end) {
        int steps = Math.max(1, (int)Math.ceil(start.distanceTo(end)));
        for (int i = 0; i <= steps; i++)
            if (!level.hasChunkAt(BlockPos.containing(start.lerp(end, i / (double)steps)))) return null;
        var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()) return null;
        Vec3 normal = hit.getDirection().getUnitVec3();
        return new Source(hit.getLocation().add(normal.scale(.24)), normal, hit.getBlockPos());
    }
}
