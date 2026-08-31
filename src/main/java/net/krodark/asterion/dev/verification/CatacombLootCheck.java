package net.krodark.asterion.dev.verification;

import net.krodark.asterion.Asterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import java.util.HashSet;

/** Uses vanilla lazy container loot generation to check tables, rarity and one-time rolls. */
final class CatacombLootCheck {
    static void run(ServerLevel level) {
        int ordinaryIron=0, ordinaryFood=0, puzzleGold=0;
        for(String table:new String[]{"catacomb_cache","catacomb_puzzle_supplies","catacomb_puzzle_reward"}) {
            var key=ResourceKey.create(Registries.LOOT_TABLE,Asterion.id("chests/"+table));
            var outcomes=new HashSet<String>();
            for(int seed=1;seed<=1024;seed++) {
                BlockPos pos=new BlockPos(0,150,40);
                level.setBlock(pos,(seed%2==0?Blocks.BARREL:Blocks.CHEST).defaultBlockState(),2);
                var container=(RandomizableContainerBlockEntity)level.getBlockEntity(pos);
                container.clearContent();
                container.setLootTable(key);
                container.setLootTableSeed(seed);
                int gold=0,iron=0,total=0;
                StringBuilder signature=new StringBuilder();
                for(int slot=0;slot<container.getContainerSize();slot++) {
                    var item=container.getItem(slot);
                    if(item.isEmpty()) continue;
                    total+=item.getCount();
                    signature.append(item.getItem()).append(':').append(item.getCount()).append(';');
                    if(item.is(Items.GOLD_INGOT)) gold+=item.getCount();
                    if(item.is(Items.IRON_INGOT)) iron+=item.getCount();
                    if(table.equals("catacomb_cache") && (item.is(Items.BREAD)||item.is(Items.POTATO)||item.is(Items.COOKED_COD)||item.is(Items.CARROT)||item.is(Items.DRIED_KELP))) ordinaryFood++;
                    check(!item.is(Items.DIAMOND)&&!item.is(Items.NETHERITE_INGOT),"Unexpected high-tier loot");
                }
                check(total>0,"Empty or missing loot table: "+table);
                check(container.getLootTable()==null,"Loot did not unpack on access");
                int secondTotal=0;
                for(int slot=0;slot<container.getContainerSize();slot++)secondTotal+=container.getItem(slot).getCount();
                check(secondTotal==total,"Opening a container rerolled its contents");
                outcomes.add(signature.toString());
                container.clearContent(); // Do not spill thousands of test items when swapping container blocks.
                if(table.equals("catacomb_cache")){ordinaryIron+=iron;check(gold==0,"Regular loot contains gold ingots");}
                if(table.equals("catacomb_puzzle_supplies"))puzzleGold+=gold;
                if(table.equals("catacomb_puzzle_reward"))check(gold>=2&&gold<=5&&iron>=1&&iron<=3,"Puzzle reward out of bounds");
            }
            check(outcomes.size()>30,"Insufficient loot variation: "+table);
        }
        check(ordinaryIron>0&&ordinaryIron<70,"Regular iron is missing or too common");
        check(ordinaryFood>100&&ordinaryFood<700,"Food rarity out of balance");
        check(puzzleGold>0&&puzzleGold<180,"Puzzle supplies gold out of balance");
        Asterion.LOGGER.info("PASS: 3,072 chest/barrel loot rolls; regular iron={}, food finds={}, puzzle supplies gold={}; reward bounds and one-time opening verified",ordinaryIron,ordinaryFood,puzzleGold);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
