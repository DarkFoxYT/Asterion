package net.krodark.asterion.client.light;

import java.util.Comparator;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.GreekBrazierBlock;
import net.krodark.asterion.block.LamenterBlock;
import net.krodark.asterion.util.LoadedBlockSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/** Finds nearby luminous fixtures without walking unrelated block sections. */
public final class BrazierAmneticLights {
    private static final int SCAN_INTERVAL = 40;
    private static final int RADIUS = 16;
    private static final Comparator<BlockPos> SCAN_ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
    private static int cooldown;

    private BrazierAmneticLights() { }

    public static void tick(Minecraft client) {
        if (client.level == null || client.player == null || --cooldown > 0) return;
        cooldown = SCAN_INTERVAL;
        BlockPos center = client.player.blockPosition();
        var fixtures = LoadedBlockSearch.find(client.level,
                center.offset(-RADIUS, -8, -RADIUS), center.offset(RADIUS, 8, RADIUS),
                state -> state.is(Asterion.LAMENTER) || state.is(Asterion.GREEK_BRAZIER));
        fixtures.sort(SCAN_ORDER);

        for (BlockPos pos : fixtures) {
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz > RADIUS * RADIUS) continue;

            var state = client.level.getBlockState(pos);
            if (state.is(Asterion.LAMENTER) && state.getValue(LamenterBlock.CRYING)) {
                LedAmneticLight.updateItemGlowLight(pos, new Vec3(pos.getX() + .5, pos.getY() + .72, pos.getZ() + .5),
                        .18F, .72F, 1F, .72F, 4.25F, false);
            } else if (state.is(Asterion.GREEK_BRAZIER) && GreekBrazierBlock.isRoot(state)
                    && state.getValue(BlockStateProperties.LIT)) {
                LedAmneticLight.updateItemGlowLight(pos, new Vec3(pos.getX() + .5, pos.getY() + 1.35, pos.getZ() + .5),
                        .18F, 1F, .30F, 2.15F, 9F, false);
            }
        }
    }
}
