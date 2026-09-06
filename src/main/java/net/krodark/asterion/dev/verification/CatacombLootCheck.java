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

 
final class CatacombLootCheck {
    static void run(ServerLevel level) {
        int ordinaryIron=0, ordinaryFood=0, puzzleGold=0, goldenApples=0, bronzeGear=0;
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
                boolean hasFood=false;
                StringBuilder signature=new StringBuilder();
                for(int slot=0;slot<container.getContainerSize();slot++) {
                    var item=container.getItem(slot);
                    if(item.isEmpty()) continue;
                    total+=item.getCount();
                    signature.append(item.getItem()).append(':').append(item.getCount()).append(';');
                    if(item.is(Items.GOLD_INGOT)||item.is(Items.RAW_GOLD)) gold+=item.getCount();
                    if(item.is(Items.IRON_INGOT)||item.is(Items.RAW_IRON)) iron+=item.getCount();
                    if(table.equals("catacomb_cache")) {
                        if(item.is(Items.BREAD)||item.is(Items.POTATO)||item.is(Items.COOKED_COD)||item.is(Items.CARROT)||item.is(Items.DRIED_KELP)||item.is(Items.COOKED_BEEF)) hasFood=true;
                        if(item.is(Items.GOLDEN_APPLE)) goldenApples++;
                        if(item.is(Asterion.CELESTIAL_BRONZE_SWORD) || net.krodark.asterion.game.ArmorContent.SETS.get(1).pieces().contains(item.getItem())) bronzeGear++;
                    }
                    check(!item.is(Items.DIAMOND)&&!item.is(Items.NETHERITE_INGOT),"Unexpected high-tier loot");
                }
                check(total>0,"Empty or missing loot table: "+table);
                if(hasFood)ordinaryFood++;
                check(container.getLootTable()==null,"Loot did not unpack on access");
                int secondTotal=0;
                for(int slot=0;slot<container.getContainerSize();slot++)secondTotal+=container.getItem(slot).getCount();
                check(secondTotal==total,"Opening a container rerolled its contents");
                outcomes.add(signature.toString());
                container.clearContent();  
                if(table.equals("catacomb_cache")){ordinaryIron+=iron;check(gold==0,"Regular loot contains gold ingots");}
                if(table.equals("catacomb_puzzle_supplies"))puzzleGold+=gold;
                if(table.equals("catacomb_puzzle_reward"))check(gold>=2&&gold<=5&&iron>=1&&iron<=3,"Puzzle reward out of bounds");
            }
            check(outcomes.size()>30,"Insufficient loot variation: "+table);
        }
        check(ordinaryIron>80&&ordinaryIron<400,"Regular iron is missing or too common");
        check(ordinaryFood>650&&ordinaryFood<950,"Food rarity out of balance");
        check(goldenApples>15&&goldenApples<90,"Golden apples should be rare");
        check(bronzeGear>15&&bronzeGear<110,"Bronze gear missing or too common");
        check(puzzleGold>0&&puzzleGold<180,"Puzzle supplies gold out of balance");
        Asterion.LOGGER.info("PASS: 3,072 chest/barrel loot rolls; regular iron={}, food finds={}, puzzle supplies gold={}; reward bounds and one-time opening verified",ordinaryIron,ordinaryFood,puzzleGold);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
