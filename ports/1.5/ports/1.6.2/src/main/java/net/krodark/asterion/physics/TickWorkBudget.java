package net.krodark.asterion.physics;

import java.util.function.LongSupplier;

/** Cooperative tick work: stop between operations on either a count or elapsed-time limit. */
public final class TickWorkBudget {
    private final LongSupplier clock;
    private final long started,nanos;
    private int remaining;
    public TickWorkBudget(int operations,long nanos) {this(operations,nanos,System::nanoTime);}
    public TickWorkBudget(int operations,long nanos,LongSupplier clock) {
        this.remaining=operations;this.nanos=nanos;this.clock=clock;this.started=clock.getAsLong();
    }
    public boolean hasTime() {return remaining>0 && clock.getAsLong()-started<nanos;}
    public boolean tryStep() {if(!hasTime())return false;remaining--;return true;}
}
