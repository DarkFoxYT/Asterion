package net.krodark.asterion.game;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.krodark.asterion.Asterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

public final class FlamethrowerItem extends Item implements GeoItem {
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public FlamethrowerItem(Properties properties) {
        super(properties);
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<FlamethrowerItem> renderer;

            @Override
            public GeoItemRenderer<FlamethrowerItem> getGeoItemRenderer() {
                if (renderer == null) {
                    var model = new DefaultedItemGeoModel<FlamethrowerItem>(Asterion.id("flamethrower"))
                            .withAltTexture(Asterion.id("beetle_flamethrower"));
                    renderer = new GeoItemRenderer<>(model);
                }
                return renderer;
            }
        });
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return animationCache; }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return net.minecraft.world.InteractionResultHolder.consume(player.getItemInHand(hand));
    }
    @Override public int
//? if >=1.20.5 {
getUseDuration(ItemStack stack, LivingEntity user)
//?} else {
/*getUseDuration(ItemStack stack)*/
//?}
 { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public void onUseTick(Level world, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(world instanceof ServerLevel level) || remaining % 4 != 0 || entity.isSpectator() || entity.isInWater()) return;
        var direction = entity.getLookAngle();
        GasClouds.emitFlamethrower(level, entity.getEyePosition().add(direction.scale(.6)), direction.scale(.4), entity.getUUID());
        if (remaining % 20 == 0) net.krodark.asterion.port.compat.EntityCompat.hurtAndBreak(stack, 1, entity, entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
    }
    public static void ignite(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (!held.is(GameplayContent.FLAMETHROWER) || player.isSpectator() || !player.isAlive()
                || player.getCooldowns().isOnCooldown(held.getItem())) return;
        player.stopUsingItem();
        GasClouds.ignite(player.serverLevel(), player.getEyePosition(), player.getUUID());
        player.getCooldowns().addCooldown(held.getItem(), 10);
    }
}
