package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.game.ArmorContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.concurrent.CompletableFuture;

/** Separate disposable render fixture; exercises texture loading across a real pack reload. */
public final class TextureLoadingRegression {
    private static int ticks, afterReload;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static CompletableFuture<Void> reload;

    public static boolean tick(Minecraft client, boolean legacyArmor) throws Exception {
        client.options.hideGui = true;
        if (failure != null) throw new AssertionError("Texture fixture failed", failure);
        if (++ticks == 20) client.getSingleplayerServer().execute(() -> {
            try {
                var server = client.getSingleplayerServer();
                var level = server.overworld();
                for (int x = -6; x <= 6; x++) for (int z = -3; z <= 8; z++)
                    level.setBlock(new BlockPos(x, 299, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                var slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                for (int index = 0; index < ArmorContent.SETS.size(); index++) {
                    var stand = new ArmorStand(level, -3 + index * 2, 300, 0.5);
                    stand.setNoGravity(true);
                    for (int slot = 0; slot < slots.length; slot++)
                        stand.setItemSlot(slots[slot], new ItemStack(ArmorContent.SETS.get(index).pieces().get(slot)));
                    level.addFreshEntity(stand);
                }
                for (int y = 300; y <= 303; y++)
                    level.setBlock(new BlockPos(-5, y, 2), Asterion.MAZESTEEL_CHAIN.defaultBlockState(), 3);
                for (int x = -1; x <= 1; x++)
                    level.setBlock(new BlockPos(x, 303, 2), Asterion.MAZESTEEL_CHAIN.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS, Direction.Axis.X), 3);
                for (int z = 1; z <= 3; z++)
                    level.setBlock(new BlockPos(5, 302, z), Asterion.MAZESTEEL_CHAIN.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS, Direction.Axis.Z), 3);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set day");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp @a 0.5 300 7.5 180 10");
                prepared = true;
            } catch (Throwable thrown) { failure = thrown; }
        });
        if (!prepared || ticks < 120) return false;
        if (reload == null) {
            verify(client, legacyArmor);
            Asterion.LOGGER.info("ASTERION_TEXTURES before reload PASS; reloading resource packs");
            reload = client.reloadResourcePacks();
        }
        if (!reload.isDone()) return false;
        reload.join();
        if (++afterReload < 80) return false;
        verify(client, legacyArmor);
        Asterion.LOGGER.info("ASTERION_TEXTURES after reload PASS: three chain axes and all four armor sets");
        return true;
    }

    private static void verify(Minecraft client, boolean legacyArmor) throws Exception {
        String root = "textures/";
        for (String material : new String[]{"tarnished_gold", "celestial_bronze", "celestial_gold", "celestial_steel"})
            for (int layer = 1; layer <= 2; layer++) {
                String path = legacyArmor ? root + "models/armor/" + material + "_layer_" + layer + ".png"
                        : root + "entity/equipment/" + (layer == 1 ? "humanoid/" : "humanoid_leggings/") + material + ".png";
                read(client, path);
            }
        var image = read(client, root + "block/mazesteel_chain.png");
        boolean transparent = false, visible = false;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int alpha = image.getRGB(x, y) >>> 24;
            transparent |= alpha == 0;
            visible |= alpha > 0;
        }
        if (!transparent || !visible) throw new AssertionError("Chain lost its cutout texture");
    }

    private static java.awt.image.BufferedImage read(Minecraft client, String path) throws Exception {
        var id = Asterion.id(path);
        try (var stream = client.getResourceManager().getResource(id).orElseThrow().open()) {
            var image = javax.imageio.ImageIO.read(stream);
            if (image == null) throw new AssertionError("Unreadable texture " + id);
            return image;
        }
    }
}
