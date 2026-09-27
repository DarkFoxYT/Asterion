package net.krodark.asterion;

/** Keeps Forge-only startup choices out of the other loader builds. */
public final class ForgeRuntime {
    private static final boolean FORGE = detectForge();

    private ForgeRuntime() {}

    public static boolean isForge() { return FORGE; }

    public static <T extends net.minecraft.world.entity.Mob> void registerSpawnPlacement(
            net.minecraft.world.entity.EntityType<T> type,
            net.minecraft.world.entity.SpawnPlacementType placement,
            net.minecraft.world.level.levelgen.Heightmap.Types heightmap,
            net.minecraft.world.entity.SpawnPlacements.SpawnPredicate<T> predicate) {
        try {
            var method = net.minecraft.world.entity.SpawnPlacements.class.getDeclaredMethod("register",
                    net.minecraft.world.entity.EntityType.class,
                    net.minecraft.world.entity.SpawnPlacementType.class,
                    net.minecraft.world.level.levelgen.Heightmap.Types.class,
                    net.minecraft.world.entity.SpawnPlacements.SpawnPredicate.class);
            method.setAccessible(true);
            method.invoke(null, type, placement, heightmap, predicate);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not register spawn placement for " + type, error);
        }
    }

    private static boolean detectForge() {
        try {
            Class.forName("net.minecraftforge.fml.common.Mod", false, ForgeRuntime.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
