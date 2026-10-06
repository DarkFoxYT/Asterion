import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.krodark.asterion.Asterion;

/** Disposable world: exercises native GPU shaders, particles, depth and both dimensions. */
public final class NativeRenderingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            for (String dimension : new String[]{"asterion:labyrinth", "asterion:limbo"}) {
                world.getServer().runCommand("execute as @a in " + dimension + " run tp @s 0 100 0 0 0");
                context.waitFor(client -> client.level != null && client.level.dimension().identifier().toString().equals(dimension), 1200);
                world.getConnection().waitForChunksRender(false, 1200);
                // Room and wall keep the camera/test particles clear of generated terrain.
                String command = "execute in " + dimension + " run ";
                world.getServer().runCommand(command + "fill -6 99 -6 6 110 12 minecraft:air");
                world.getServer().runCommand(command + "fill -6 99 -6 6 99 12 minecraft:stone");
                world.getServer().runCommand(command + "fill -4 100 4 4 107 4 minecraft:stone");
                context.waitTicks(20);
                context.runOnClient(client -> {
                    client.player.setYRot(0);client.player.setXRot(0);
                    for(int i=0;i<12;i++)client.level.addParticle(Asterion.GREEK_FIRE,
                            (i%4-1.5)*0.6, 101+(i%3)*0.35, 6, 0, 0, 0);
                });
                context.waitTicks(5);
                context.takeScreenshot(dimension.replace(':','-') + "-occluded");
                world.getServer().runCommand(command + "fill -4 100 4 4 107 4 minecraft:air");
                context.runOnClient(client -> {
                    for(int i=0;i<12;i++)client.level.addParticle(Asterion.GREEK_FIRE,
                            (i%4-1.5)*0.6, 101+(i%3)*0.35, 6, 0, 0, 0);
                });
                context.waitTicks(5);
                context.takeScreenshot(dimension.replace(':','-') + "-visible");
                context.waitTicks(30);
            }
        }
    }
}
