package net.krodark.asterion.entity;

import java.util.List;
import net.krodark.asterion.Asterion;
import net.krodark.asterion.block.RespawnObelisks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** Append requests to preserve saved indices. Every request is a separate, one-time bargain. */
public final class QueenBeetleQuests {
    public record Quest(String id, ItemLike item, int count, ItemLike reward, int rewardCount) {
        public String key(String suffix) { return "quest.asterion.queen_beetle." + id + "." + suffix; }
    }
    public static final List<Quest> ALL = List.of(
            new Quest("petals", Asterion.TAINTED_PETALS, 8, RespawnObelisks.CHARGED_RUNE, 1),
            new Quest("nursery_bedding", Asterion.ANCIENT_MOSS, 12, Asterion.POPPED_ANCIENT_VINES, 6),
            new Quest("living_threads", Asterion.LABYRINTH_VINE, 16, Items.EMERALD, 3),
            new Quest("royal_feast", Asterion.POPPED_ANCIENT_VINES, 12, RespawnObelisks.CHARGED_RUNE, 1),
            new Quest("fallen_timber", Asterion.DEAD_WOOD, 8, Items.IRON_INGOT, 3),
            new Quest("nursery_frames", Asterion.DEAD_WOOD_PLANKS, 24, Items.EMERALD, 4),
            new Quest("leaf_canopies", Asterion.ANCIENT_LEAVES, 8, Items.SHEARS, 1),
            new Quest("silken_cradles", Items.STRING, 12, Asterion.POPPED_ANCIENT_VINES, 8),
            new Quest("bone_supports", Items.BONE, 16, Items.EMERALD, 5),
            new Quest("compost_keepers", Items.ROTTEN_FLESH, 16, RespawnObelisks.CHARGED_RUNE, 1),
            new Quest("fungal_gardens", Items.BROWN_MUSHROOM, 8, Items.GOLDEN_CARROT, 4),
            new Quest("light_the_paths", Items.TORCH, 16, Items.IRON_INGOT, 4),
            new Quest("iron_braces", Items.IRON_INGOT, 8, Items.EMERALD, 6),
            new Quest("copper_channels", Items.COPPER_INGOT, 12, Asterion.POPPED_ANCIENT_VINES, 12),
            new Quest("gilded_shells", Items.GOLD_INGOT, 4, RespawnObelisks.CHARGED_RUNE, 1),
            new Quest("restore_the_court", Asterion.ANCIENT_BRICKS, 8, Items.EMERALD, 5),
            new Quest("nursery_boundary", Asterion.ANCIENT_STONE_WALL, 6, Items.GOLDEN_CARROT, 6),
            new Quest("green_watchfires", Items.LANTERN, 6, RespawnObelisks.CHARGED_RUNE, 1),
            new Quest("forgotten_gold", Asterion.TARNISHED_GOLD_INGOT, 3, Items.EMERALD, 8),
            new Quest("bronze_promise", Asterion.CELESTIAL_BRONZE_INGOT, 2, RespawnObelisks.CHARGED_RUNE, 1),
            new Quest("queens_covenant", Asterion.CELESTIAL_GOLD_INGOT, 1, RespawnObelisks.CHARGED_RUNE, 2)
    );
    public static Quest get(int index) { return ALL.get(Math.clamp(index, 0, ALL.size() - 1)); }
    private QueenBeetleQuests() { }
}
