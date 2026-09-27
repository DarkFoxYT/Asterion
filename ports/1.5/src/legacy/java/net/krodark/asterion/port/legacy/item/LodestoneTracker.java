package net.krodark.asterion.port.legacy.item;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
public record LodestoneTracker(Optional<GlobalPos> target,boolean tracked) {}
