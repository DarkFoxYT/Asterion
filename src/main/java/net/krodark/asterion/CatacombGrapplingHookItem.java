package net.krodark.asterion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CatacombGrapplingHookItem extends Item {
    private static final Map<UUID, Pull> PULLS = new HashMap<>();

    public CatacombGrapplingHookItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (player.isSpectator() || player.isPassenger() || !level.dimension().equals(Asterion.ASTERION_LEVEL))
            return InteractionResult.PASS;
        Vec3 eye = player.getEyePosition();
        var hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(32)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        var target = level.getBlockState(hit.getBlockPos());
        if (hit.getType() != HitResult.Type.BLOCK || (!target.is(Asterion.MAZESTEEL_BLOCK)
                && !target.is(Asterion.MAZESTEEL_CHAIN))) {
            serverPlayer.sendSystemMessage(Component.translatable("message.asterion.grapple_anchor"), true);
            return InteractionResult.FAIL;
        }
        PULLS.put(player.getUUID(), new Pull(hit.getBlockPos().immutable(), hit.getLocation(), 28, 0));
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 12);
        return InteractionResult.SUCCESS;
    }

    public static void tick(MinecraftServer server) {
        PULLS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || player.isSpectator() || player.isPassenger()
                    || !player.level().dimension().equals(Asterion.ASTERION_LEVEL)) return true;
            Pull pull = entry.getValue();
            if (pull.grace > 0) {
                player.resetFallDistance();
                entry.setValue(new Pull(pull.block, pull.point, 0, pull.grace - 1));
                return pull.grace <= 1 || player.onGround() || player.isInWater();
            }
            var level = player.level();
            if (!level.getChunkSource().hasChunk(pull.block.getX() >> 4, pull.block.getZ() >> 4)) return true;
            var anchor = level.getBlockState(pull.block);
            if (!anchor.is(Asterion.MAZESTEEL_BLOCK) && !anchor.is(Asterion.MAZESTEEL_CHAIN)) return true;
            Vec3 delta = pull.point.subtract(player.getEyePosition());
            if (pull.ticks <= 0 || delta.length() < 1.7 || player.isShiftKeyDown()) {
                entry.setValue(new Pull(pull.block, pull.point, 0, 35));
                return false;
            }
            var obstruction = level.clip(new ClipContext(player.getEyePosition(), pull.point,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (obstruction.getType() == HitResult.Type.BLOCK && !obstruction.getBlockPos().equals(pull.block)) return true;
            // Apply a bounded pull through normal movement/collision, never teleport through walls.
            Vec3 velocity = player.getDeltaMovement().scale(0.35).add(delta.normalize().scale(0.65));
            player.setDeltaMovement(velocity);
            player.hurtMarked = true;
            player.resetFallDistance();
            if ((pull.ticks & 3) == 0) for (int i = 1; i <= 10; i++) {
                Vec3 point = player.getEyePosition().lerp(pull.point, i / 10.0);
                player.level().sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
            entry.setValue(new Pull(pull.block, pull.point, pull.ticks - 1, 0));
            return false;
        });
    }

    public static boolean protects(UUID id) { return PULLS.containsKey(id); }
    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.asterion.catacomb_grappling_hook"));
    }

    public static void clear() { PULLS.clear(); }
    private record Pull(BlockPos block, Vec3 point, int ticks, int grace) { }
}
