package net.krodark.asterion.test;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.RespawnObelisks;
import net.krodark.asterion.block.RuneDoorBlock;
import net.krodark.asterion.block.SanctuaryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;

public final class RetiredContentChecks {
    public static void run(ServerLevel level) {
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
        for (var item : java.util.List.of(Asterion.SICKENED_TWINBLADES, Asterion.RUNE_ZONE_DOOR.asItem(),
                RespawnObelisks.CHARGED_RUNE, RespawnObelisks.ALTAR.asItem())) {
            require(java.util.stream.Stream.of(Asterion.ITEM_GROUP, Asterion.FORGING_ITEM_GROUP, Asterion.RUNE_ITEM_GROUP)
                            .flatMap(tab -> tab.getDisplayItems().stream()).noneMatch(stack -> stack.is(item)),
                    "Retired content is still in the creative tab: " + item);
        }
        require(Asterion.RUNE_ITEM_GROUP.getDisplayItems().stream().anyMatch(stack -> stack.is(RespawnObelisks.OBELISK.asItem())),
                "The active obelisk was accidentally removed");
        require(new ItemStack(Asterion.SICKENED_TWINBLADES).getMaxDamage() == 0, "Legacy Twinblades still have weapon durability");
        for (boolean open : new boolean[]{false, true}) {
            var gate = Asterion.RUNE_ZONE_DOOR.defaultBlockState().setValue(RuneDoorBlock.OPEN, open);
            require(gate.getCollisionShape(level, BlockPos.ZERO).isEmpty() && gate.getShape(level, BlockPos.ZERO).isEmpty()
                            && gate.getRenderShape() == RenderShape.INVISIBLE,
                    "A saved legacy gate can still obstruct or render in the labyrinth");
        }
        var altar = RespawnObelisks.ALTAR.defaultBlockState().setValue(SanctuaryBlock.CHARGE, 1);
        require(altar.getCollisionShape(level, BlockPos.ZERO).isEmpty() && altar.getLightEmission() == 0
                        && RespawnObelisks.ALTAR.getTicker(level, altar, RespawnObelisks.BLOCK_ENTITY) == null,
                "A saved legacy altar still has collision, lighting or ticking effects");
        Asterion.LOGGER.info("ASTERION_RETIRED_CONTENT PASS: hidden legacy IDs, inert gates/altars, active obelisk retained");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
