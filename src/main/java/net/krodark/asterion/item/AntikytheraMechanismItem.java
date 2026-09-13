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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.List;

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
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (level.dimension().equals(Asterion.ASTERION_LEVEL)) {
            Vec3 look = player.getLookAngle();
            Vec3 direction = new Vec3(look.x, 0.0D, look.z);
            if (direction.lengthSqr() < 1.0E-6D) direction = new Vec3(0.0D, 0.0D, 1.0D);
            direction = direction.normalize();
            BlockPos bearing = BlockPos.containing(player.position().add(direction.scale(1_000_000.0D)));
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                    Optional.of(GlobalPos.of(Asterion.ASTERION_LEVEL, bearing)), false));
            net.krodark.asterion.game.PlayerNotices.show(serverPlayer, Component.translatable("message.asterion.mechanism_bearing_locked"));
            return net.minecraft.world.InteractionResultHolder.success(stack);
        }
        boolean wasDormant = stack.get(DataComponents.LODESTONE_TRACKER) == null;
        bindToGateway(stack, serverLevel);
        if (wasDormant) {
            net.krodark.asterion.game.PlayerNotices.show(serverPlayer, Component.translatable("message.asterion.mechanism_awakened"));
        } else {
            net.krodark.asterion.game.PlayerNotices.show(serverPlayer, Component.translatable("message.asterion.mechanism_points"));
        }
        return net.minecraft.world.InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel serverLevel && !level.dimension().equals(Asterion.ASTERION_LEVEL))
            bindToGateway(stack, serverLevel);
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
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.get(DataComponents.LODESTONE_TRACKER) == null
                ? "tooltip.asterion.antikythera_mechanism.dormant"
                : "tooltip.asterion.antikythera_mechanism.bound"));
        tooltip.add(Component.translatable("tooltip.asterion.antikythera_mechanism.maze_bearing"));
    }
}
