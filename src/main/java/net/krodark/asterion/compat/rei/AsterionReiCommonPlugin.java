package net.krodark.asterion.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.EntryComparator;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import net.krodark.asterion.Asterion;

 
public final class AsterionReiCommonPlugin implements REICommonPlugin {
    @Override public void registerItemComparators(ItemComparatorRegistry registry) {
        registry.register(EntryComparator.itemComponents().onlyExact(),
                Asterion.FORGED_INGOT,
                Asterion.FORGED_SWORD_GUARD,
                Asterion.FORGED_SWORD_POMMEL,
                Asterion.FORGED_SWORD_BLADE,
                Asterion.FORGED_AXE_HEAD,
                Asterion.FORGED_SWORD);
    }
}
