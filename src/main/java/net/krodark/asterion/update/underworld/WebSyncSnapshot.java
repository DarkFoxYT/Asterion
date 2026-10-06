package net.krodark.asterion.update.underworld;

import java.util.BitSet;

/** Per-viewer state for reliable web packets. Never shared across connections/worlds. */
public final class WebSyncSnapshot {
    private boolean geometrySent;
    private final BitSet sentCuts = new BitSet();

    public boolean needsGeometry() { return !geometrySent; }
    public void geometrySent() { geometrySent = true; }

    public int nextPendingCut(BitSet current, int from) {
        int link = current.nextSetBit(from);
        while (link >= 0 && sentCuts.get(link)) link = current.nextSetBit(link + 1);
        return link;
    }

    public void cutSent(int link) { sentCuts.set(link); }
}
