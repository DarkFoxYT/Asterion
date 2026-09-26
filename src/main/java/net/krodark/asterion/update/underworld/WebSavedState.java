package net.krodark.asterion.update.underworld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Spun strands and severed links survive chunk unloads, reconnects, and restarts. */
public final class WebSavedState extends SavedData {
    private static final Codec<WebPatch> PATCH=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.fieldOf("key").forGetter(WebPatch::key),
            Vec3.CODEC.listOf().fieldOf("anchors").forGetter(WebPatch::anchors),
            Vec3.CODEC.listOf().fieldOf("normals").forGetter(WebPatch::normals)
    ).apply(i,(key,anchors,normals)->new WebPatch(key,anchors,normals,List.of(new WebPatch.Edge(0,1)))));
    private static final Codec<WebSavedState> CODEC=RecordCodecBuilder.create(i->i.group(
            PATCH.listOf().optionalFieldOf("strands",List.of()).forGetter(s->List.copyOf(s.spun.values())),
            Codec.unboundedMap(Codec.STRING,Codec.INT.listOf()).optionalFieldOf("cuts",Map.of()).forGetter(WebSavedState::savedCuts)
    ).apply(i,WebSavedState::new));
    private static final SavedDataType<WebSavedState> TYPE=new SavedDataType<>(Identifier.fromNamespaceAndPath("asterion","limbo_silk"),WebSavedState::new,CODEC,null);
    final Map<Long,WebPatch> spun=new LinkedHashMap<>();
    final Map<Long,BitSet> cuts=new HashMap<>();
    public WebSavedState(){this(List.of(),Map.of());}
    private WebSavedState(List<WebPatch> strands,Map<String,List<Integer>> severed){
        for(WebPatch patch:strands)if(patch.anchors().size()==2 && patch.normals().size()==2)spun.put(patch.key(),patch);
        severed.forEach((key,links)->{
            try { BitSet bits=new BitSet();for(int link:links)if(link>=0 && link<4096)bits.set(link);cuts.put(Long.parseLong(key),bits); }
            catch(NumberFormatException ignored){ /* Ignore malformed saved keys. */ }
        });
    }
    private Map<String,List<Integer>> savedCuts(){
        Map<String,List<Integer>> result=new HashMap<>();
        cuts.forEach((key,bits)->result.put(Long.toString(key),bits.stream().boxed().toList()));
        return result;
    }
    public static WebSavedState get(ServerLevel level){return level.getDataStorage().computeIfAbsent(TYPE);}
}
