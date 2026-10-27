package net.krodark.asterion.physics;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Per-world explicit selection; respawned players cannot inherit a previous body's grip. */
public final class ChainGripSelection<W,P,C> {
    private record Grip<P,C>(WeakReference<P> player, WeakReference<C> chain) { }
    private final Map<W,Map<UUID,Grip<P,C>>> worlds=Collections.synchronizedMap(new WeakHashMap<>());
    public void select(W world, UUID id, P player, C chain) {
        worlds.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>())
                .put(id,new Grip<>(new WeakReference<>(player),new WeakReference<>(chain)));
    }
    public void toggle(W world, UUID id, P player, C chain) {
        var selections=worlds.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
        Grip<P,C> existing=selections.get(id);
        if (existing!=null && existing.player.get()==player && existing.chain.get()==chain) selections.remove(id);
        else selections.put(id,new Grip<>(new WeakReference<>(player),new WeakReference<>(chain)));
    }
    public C target(W world, UUID id, P player) {
        var selections=worlds.get(world);
        if (selections==null) return null;
        var grip=selections.get(id);
        if (grip==null) return null;
        C chain=grip.chain.get();
        if (grip.player.get()!=player || chain==null) { selections.remove(id); return null; }
        return chain;
    }
    public void release(W world, UUID id) {
        var selections=worlds.get(world);
        if (selections!=null) selections.remove(id);
    }
}
