package net.krodark.asterion;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.function.Consumer;

public final class AntikytheraMechanismItem extends CompassItem {
    public AntikytheraMechanismItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            Vec3 look = player.getLookAngle();
            Vec3 direction = new Vec3(look.x, 0.0D, look.z);
            if (direction.lengthSqr() < 1.0E-6D) direction = new Vec3(0.0D, 0.0D, 1.0D);
            direction = direction.normalize();
            // A deliberately remote target behaves like a locked bearing instead of bending
            // toward a nearby waypoint as the player walks through the maze.
            BlockPos bearing = BlockPos.containing(player.position().add(direction.scale(1_000_000.0D)));
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                    Optional.of(GlobalPos.of(Asterion.ASTERION_LEVEL, bearing)), false));
            serverPlayer.sendSystemMessage(Component.translatable("message.asterion.mechanism_bearing_locked"));
            return InteractionResult.SUCCESS;
        }
        boolean wasDormant = stack.get(DataComponents.LODESTONE_TRACKER) == null;
        bindToGateway(stack, serverLevel);
        if (wasDormant) {
            serverPlayer.sendSystemMessage(Component.translatable("message.asterion.mechanism_awakened"));
        } else {
            serverPlayer.sendSystemMessage(Component.translatable("message.asterion.mechanism_points"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (!level.dimension().equals(Asterion.ASTERION_LEVEL)) bindToGateway(stack, level);
    }

    private static void bindToGateway(ItemStack stack, ServerLevel level) {
        BlockPos target = WorldGenerator.gatewayPosition(level.getServer().overworld().getSeed());
        GlobalPos expected = GlobalPos.of(Level.OVERWORLD, target);
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (current == null || current.tracked() || current.target().isEmpty()
                || !current.target().get().equals(expected)) {
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(expected), false));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(stack.get(DataComponents.LODESTONE_TRACKER) == null
                ? "tooltip.asterion.antikythera_mechanism.dormant"
                : "tooltip.asterion.antikythera_mechanism.bound"));
        tooltip.accept(Component.translatable("tooltip.asterion.antikythera_mechanism.maze_bearing"));
    }
}
