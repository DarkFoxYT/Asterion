package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;

import java.util.concurrent.CopyOnWriteArrayList;

public final class PlayerBlockBreakEvents {
    private static final CopyOnWriteArrayList<Before> BEFORE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<After> AFTER_LISTENERS = new CopyOnWriteArrayList<>();
    public static final Event<Before> BEFORE = BEFORE_LISTENERS::add;
    public static final Event<After> AFTER = AFTER_LISTENERS::add;

    static {
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (!(event.getLevel() instanceof Level level)) return;
            BlockPos pos = event.getPos();
            BlockState state = event.getState();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            Player player = event.getPlayer();
            for (Before listener : BEFORE_LISTENERS) {
                if (!listener.beforeBlockBreak(level, player, pos, state, blockEntity)) {
                    event.setCanceled(true);
                    return;
                }
            }
            AFTER_LISTENERS.forEach(listener -> listener.afterBlockBreak(level, player, pos, state, blockEntity));
        });
    }

    private PlayerBlockBreakEvents() {}
    @FunctionalInterface public interface Before { boolean beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity); }
    @FunctionalInterface public interface After { void afterBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity); }
}
