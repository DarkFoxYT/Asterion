# Dead Sun events

The event system is split into server gameplay and client presentation so every player sees the same seeded event.

## Add a server event

Register a `DeadSunEventSystem.Definition` during mod initialization. A definition supplies its ID, random-selection weight, duration range, intensity, and optional `onStart`, `onTick`, and `onEnd` gameplay hooks.

```java
DeadSunEventSystem.register(new DeadSunEventSystem.Definition() {
    public Identifier id() { return Labyrinth.id("your_event"); }
    public int weight() { return 4; }
    public int minDurationTicks() { return 80; }
    public int maxDurationTicks() { return 160; }
    public float intensity(RandomSource random) { return 0.8F; }

    public void onStart(ServerLevel level, long seed, int duration, float intensity) {
        // Spawn or prepare event gameplay here.
    }

    public void onTick(ServerLevel level, int elapsedTicks) {
        // Run lightweight server-authoritative gameplay here.
    }

    public void onEnd(ServerLevel level) {
        // Clean up temporary gameplay state here.
    }
});
```

Events can also be started by encounters or bosses with:

```java
DeadSunEventSystem.trigger(level, Labyrinth.id("your_event"));
```

## Add its client effect

Register the same ID with `DeadSunClientEvents.register`. Its factory receives the shared seed, synchronized start tick, duration, and intensity and returns an `ActiveEffect`. This can provide camera and Sun offsets or drive another renderer. Unknown IDs are ignored safely, and players entering midway receive the correct elapsed phase.

The included `labyrinth:rumble` implementation is the reference example.
