package net.krodark.asterion.port.legacy.item;
import java.util.List;
import net.minecraft.network.chat.Component;
public record ItemLore(List<Component> lines) { public ItemLore {lines=List.copyOf(lines);} }
