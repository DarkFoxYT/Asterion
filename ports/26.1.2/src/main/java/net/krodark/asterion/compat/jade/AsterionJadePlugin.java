package net.krodark.asterion.compat.jade;

import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.CrucibleBlock;
import net.krodark.asterion.block.CrucibleBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.JadeUI;

@WailaPlugin
public final class AsterionJadePlugin implements IWailaPlugin {
    private static final Identifier CRUCIBLE_STATE = Asterion.id("crucible_state");

    @Override public void register(IWailaCommonRegistration registration) {
         
        registration.registerBlockDataProvider(CrucibleDataProvider.INSTANCE, CrucibleBlock.class);
    }

    @Override public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CrucibleComponentProvider.INSTANCE, CrucibleBlock.class);
    }

    private static final class CrucibleDataProvider
            implements StreamServerDataProvider<BlockAccessor, CrucibleState> {
        private static final CrucibleDataProvider INSTANCE = new CrucibleDataProvider();

        @Override public @Nullable CrucibleState streamData(BlockAccessor accessor) {
            BlockPos root = CrucibleBlock.root(accessor.getPosition(), accessor.getBlockState());
            if (!(accessor.getLevel().getBlockEntity(root) instanceof CrucibleBlockEntity crucible)) return null;
            return new CrucibleState(crucible.temperature(), crucible.targetTemperature(), crucible.heatControl(),
                    crucible.fuelTicks(), crucible.selectedMoldIndex(), crucible.materialUnits(),
                    crucible.autoPourProgress(), crucible.hasUnsmeltedIngredients(), crucible.metalSequence());
        }

        @Override public StreamCodec<RegistryFriendlyByteBuf, CrucibleState> streamCodec() {
            return CrucibleState.STREAM_CODEC;
        }

        @Override public Identifier getUid() { return CRUCIBLE_STATE; }
    }

    private static final class CrucibleComponentProvider implements IBlockComponentProvider {
        private static final CrucibleComponentProvider INSTANCE = new CrucibleComponentProvider();

        @Override public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CrucibleState state = CrucibleDataProvider.INSTANCE.decodeFromData(accessor).orElse(null);
            if (state == null) return;

            if (state.mold() >= 0 && state.mold() < CrucibleBlockEntity.Mold.values().length) {
                ItemStack mold = new ItemStack(CrucibleBlockEntity.moldItem(state.mold()));
                tooltip.add(JadeUI.smallItem(mold));
                tooltip.append(Component.translatable("jade.asterion.crucible.mold", mold.getHoverName()));
            }

            tooltip.add(Component.translatable("jade.asterion.crucible.temperature",
                    state.temperature(), state.target(), signed(state.heatControl()))
                    .withStyle(temperatureColor(state)));

            if (!state.metals().isEmpty()) {
                boolean first = true;
                for (int index = 0; index < state.metals().length(); index++) {
                    int metal = state.metals().charAt(index) - '0';
                    ItemStack stack = CrucibleBlockEntity.returnedMetal(metal);
                    if (first) {
                        tooltip.add(JadeUI.smallItem(stack));
                        first = false;
                    } else tooltip.append(JadeUI.smallItem(stack));
                }
                tooltip.append(Component.translatable("jade.asterion.crucible.metal_count", state.units(), 4));
            } else {
                tooltip.add(Component.translatable("jade.asterion.crucible.metal_count", 0, 4)
                        .withStyle(ChatFormatting.GRAY));
            }

            tooltip.add(Component.translatable(state.fuelTicks() > 0
                            ? "jade.asterion.crucible.heat_source" : "jade.asterion.crucible.no_heat_source")
                    .withStyle(state.fuelTicks() > 0 ? ChatFormatting.GOLD : ChatFormatting.RED));
            tooltip.add(status(state));

            if (state.autoPour() > 0) {
                float progress = Math.clamp(state.autoPour() / (float) CrucibleBlockEntity.AUTO_POUR_TICKS, 0F, 1F);
                tooltip.add(JadeUI.progressArrow(progress));
                tooltip.append(Component.translatable("jade.asterion.crucible.pouring", Math.round(progress * 100F))
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        private static String signed(int value) { return value > 0 ? "+" + value : Integer.toString(value); }

        private static ChatFormatting temperatureColor(CrucibleState state) {
            if (state.mold() < 0) return ChatFormatting.WHITE;
            if (state.temperature() < state.target() - CrucibleBlockEntity.TOLERANCE) return ChatFormatting.AQUA;
            if (state.temperature() > state.target() + CrucibleBlockEntity.TOLERANCE) return ChatFormatting.RED;
            return ChatFormatting.GREEN;
        }

        private static Component status(CrucibleState state) {
            String key;
            ChatFormatting color;
            if (state.mold() < 0) { key = "no_mold"; color = ChatFormatting.GRAY; }
            else if (state.units() == 0) { key = "waiting_metal"; color = ChatFormatting.GRAY; }
            else if (state.unsmelted()) { key = "smelting"; color = ChatFormatting.GOLD; }
            else if (state.fuelTicks() <= 0) { key = "no_heat_source"; color = ChatFormatting.RED; }
            else if (state.temperature() < state.target() - CrucibleBlockEntity.TOLERANCE) {
                key = "too_cold"; color = ChatFormatting.AQUA;
            } else if (state.temperature() > state.target() + CrucibleBlockEntity.TOLERANCE) {
                key = "too_hot"; color = ChatFormatting.RED;
            } else { key = "calibrated"; color = ChatFormatting.GREEN; }
            return Component.translatable("jade.asterion.crucible.status." + key).withStyle(color);
        }

        @Override public Identifier getUid() { return CRUCIBLE_STATE; }
    }

    private record CrucibleState(int temperature, int target, int heatControl, int fuelTicks,
                                 int mold, int units, int autoPour, boolean unsmelted, String metals) {
        private static final StreamCodec<RegistryFriendlyByteBuf, CrucibleState> STREAM_CODEC = StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeVarInt(value.temperature);
                    buffer.writeVarInt(value.target);
                    buffer.writeVarInt(value.heatControl);
                    buffer.writeVarInt(value.fuelTicks);
                    buffer.writeVarInt(value.mold + 1);
                    buffer.writeVarInt(value.units);
                    buffer.writeVarInt(value.autoPour);
                    buffer.writeBoolean(value.unsmelted);
                    buffer.writeUtf(value.metals, 4);
                },
                buffer -> new CrucibleState(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt(), buffer.readVarInt() - 1, buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readBoolean(), buffer.readUtf(4)));
    }
}
