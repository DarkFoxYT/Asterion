package net.krodark.asterion.item;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.worldgen.WorldGenerator;

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
    public Component getName(ItemStack stack) {


        return Component.translatable(getDescriptionId());
    }

    @Override
    public boolean isFoil(ItemStack stack) {


        return stack.isEnchanted();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            if (bindObjective(stack, serverLevel, player.position())) {
                net.krodark.asterion.game.PlayerNotices.show(serverPlayer,
                        Component.translatable("message.asterion.mechanism_objective"));
            } else {
                net.krodark.asterion.game.PlayerNotices.show(serverPlayer,
                        Component.translatable("message.asterion.mechanism_no_destination"));
            }
            return InteractionResult.SUCCESS;
        }
        boolean wasDormant = stack.get(DataComponents.LODESTONE_TRACKER) == null;
        bindToGateway(stack, serverLevel);
        if (wasDormant) {
            net.krodark.asterion.game.PlayerNotices.show(serverPlayer, Component.translatable("message.asterion.mechanism_awakened"));
        } else {
            net.krodark.asterion.game.PlayerNotices.show(serverPlayer, Component.translatable("message.asterion.mechanism_points"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        if (entity.tickCount % 20 != 0) return;
        if (level.dimension().equals(Asterion.ASTERION_LEVEL)) bindObjective(stack, level, entity.position());
        else bindToGateway(stack, level);
    }

    private static boolean bindObjective(ItemStack stack, ServerLevel level, net.minecraft.world.phys.Vec3 position) {
        var progress = net.krodark.asterion.game.SharedObjectiveProgress.get(level);
        BlockPos target = progress.navigationTarget(position);
        if (target == null && (progress.stage() == 0 || progress.stage() == 1))
            target = WorldGenerator.nearestQueenTree(level, position);
        if (target == null) {
            stack.remove(DataComponents.LODESTONE_TRACKER);
            return false;
        }
        GlobalPos expected = GlobalPos.of(Asterion.ASTERION_LEVEL, target);
        LodestoneTracker current = stack.get(DataComponents.LODESTONE_TRACKER);
        if (current == null || current.tracked() || !current.target().equals(Optional.of(expected)))
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(expected), false));
        return true;
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
