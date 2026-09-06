# Full mod audit

Run the Fabric client integration audit with Java 25:

```powershell
./gradlew.bat runCatacombTest -PauditOnly
```

To rerun selected scenarios:

```powershell
./gradlew.bat runCatacombTest -PauditOnly '-PauditTests=ForgeInteractionGameTest,BossMechanicsAuditGameTest,WorldPerformanceGameTest'
```

The runner records each scenario independently in `build/catacomb-gametest/audit-results.tsv`, keeps going after assertion failures, and fails the overall run if any scenario failed. Screenshots are in `build/catacomb-gametest/screenshots`. Copy the TSV and client log before another run, which replaces them. A process exit code alone is not evidence that a scenario passed.

Do not build or refresh resources concurrently with the client audit: each scenario loads a fresh world from the live development resources. The harness disables AFK throttling so long automated runs are not accidentally capped to 10 FPS. It does not change the normal game defaults.

Coverage includes Forge recipes and actual GUI input, equipment rendering, Queen quests and generated sites, Cursed Brazier attacks and rewards, Minotaur combat/targeting/harvesting, doors and lifts, traps and rune puzzles, loot and progression, terrain, flooding, GPU fallbacks, and world/portal preparation. Several scenarios contain additional focused checks.

Also run:

```powershell
./gradlew.bat check emissiveRegression renderPerformanceRegression modDistribution
```

These tests cover reproducible scenarios. They do not prove every seed, third-party mod combination, multiplayer race, or long survival session is bug-free. Multiplayer visual checks include simulated remote entities; they are not a substitute for a real two-client network soak test.

Normal-world startup needs its own test: the default flat test worlds skip vanilla terrain generation and spawn search.

```powershell
./gradlew.bat runCatacombTest -PauditOnly '-PauditTests=NormalWorldCreationGameTest'
```

This creates three fixed-seed Survival worlds using normal generation, including the seed from the reported slow world, joins the player, verifies the unused maze stays unloaded, and records time through the first 60 client ticks. The client launch workaround now uses unprofiled C1 (`TieredStopAtLevel=1`), retaining the C2 crash workaround. For a controlled comparison only, pass `-PasterionSafeJvmTier=3` to restore the previous profiling tier. The default applies on the next Gradle/IDE client launch; replacing the mod JAR alone cannot change a running JVM's compiler flags.
