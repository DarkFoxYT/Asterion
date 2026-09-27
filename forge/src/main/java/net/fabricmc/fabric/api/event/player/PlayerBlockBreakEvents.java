package net.fabricmc.fabric.api.event.player;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Forge block-break adapter, running post-break callbacks after the server tick. */
public final class PlayerBlockBreakEvents {
    public static final Before BEFORE = new Before();
    public static final After AFTER = new After();
    private static final Queue<Runnable> POST_BREAK = new ConcurrentLinkedQueue<>();

    static {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> {
            Runnable callback;
            while ((callback = POST_BREAK.poll()) != null) callback.run();
        });
    }

    private PlayerBlockBreakEvents() {}

    @FunctionalInterface public interface BeforeCallback {
        boolean before(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity);
    }
    @FunctionalInterface public interface AfterCallback {
        void after(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity);
    }

    public static final class Before {
        public void register(BeforeCallback callback) {
            BlockEvent.BreakEvent.BUS.addListener(event -> event.getLevel() instanceof Level level
                    && !callback.before(level, event.getPlayer(), event.getPos(), event.getState(),
                    level.getBlockEntity(event.getPos())));
        }
    }
    public static final class After {
        public void register(AfterCallback callback) {
            BlockEvent.BreakEvent.BUS.addListener((event, cancelled) -> {
                if (!cancelled && event.getLevel() instanceof Level level) {
                    BlockPos pos = event.getPos().immutable();
                    BlockState state = event.getState();
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    POST_BREAK.add(() -> callback.after(level, event.getPlayer(), pos, state, blockEntity));
                }
            });
        }
    }
}
