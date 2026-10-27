package net.krodark.asterion.item;

import net.krodark.asterion.entity.PhysicsChainEntity;
import net.krodark.asterion.game.ChainLiftContent;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class PhysicsChainItem extends Item {
    public PhysicsChainItem(Properties properties) {
        super(properties.component(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(java.util.List.of(
                        net.minecraft.network.chat.Component.translatable("tooltip.asterion.physics_chain")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        net.minecraft.network.chat.Component.translatable("tooltip.asterion.physics_chain.climb")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        net.minecraft.network.chat.Component.translatable("tooltip.asterion.physics_chain.hold")
                                .withStyle(net.minecraft.ChatFormatting.GRAY),
                        net.minecraft.network.chat.Component.translatable("tooltip.asterion.physics_chain.retrieve")
                                .withStyle(net.minecraft.ChatFormatting.GRAY)))));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() == Direction.UP || context.getPlayer() == null) return InteractionResult.FAIL;
        var level = context.getLevel();
        var support = context.getClickedPos();
        if (!context.getPlayer().mayUseItemAt(support.below(), Direction.DOWN, context.getItemInHand())) return InteractionResult.FAIL;
        Vec3 anchor = context.getClickedFace()==Direction.DOWN?Vec3.atBottomCenterOf(support).add(0,-.03,0)
                :Vec3.atCenterOf(support).add(context.getClickedFace().getUnitVec3().scale(.58));
        if(level.getBlockState(support).getCollisionShape(level,support).isEmpty())return InteractionResult.FAIL;
        int length = 1;
        for (int i = 1; i <= length; i++) {
            var link = net.minecraft.core.BlockPos.containing(anchor.add(0,-i+.05,0));
            if (!level.getBlockState(link).getCollisionShape(level, link).isEmpty()) {
                length = i - 1; break;
            }
        }
        if (length < 1 || !level.getEntitiesOfClass(PhysicsChainEntity.class,
                new net.minecraft.world.phys.AABB(anchor, anchor.add(0, -length, 0)).inflate(.4),
                existing->existing.position().distanceToSqr(anchor)<.0625).isEmpty()) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            var chain = new PhysicsChainEntity(ChainLiftContent.PHYSICS_CHAIN, level);
            chain.configure(support, anchor, length);
            if (!level.addFreshEntity(chain)) return InteractionResult.FAIL;
            if (!context.getPlayer().isCreative()) context.getItemInHand().shrink(1);
            level.playSound(null, support, net.minecraft.sounds.SoundEvents.CHAIN_PLACE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1, .8F);
        }
        return InteractionResult.SUCCESS;
    }
}
