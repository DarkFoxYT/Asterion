# Building with Stonecutter

This branch uses Stonecutter 0.9.8 for the existing Minecraft 26.1.2 Fabric,
Quilt, and NeoForge targets. All three use Amnetic, including the existing
NeoForge-specific Amnetic jar.

Use JDK 25 for Gradle and reload the Gradle project in IntelliJ after updating.
The root build script is now `stonecutter.gradle`; `build.gradle` is the shared
Fabric/Quilt script and `neoforge/build.gradle` is the NeoForge script.

## Commands

Run these from the repository root:

```powershell
# Build all three loader jars
.\gradlew.bat build

# Build one loader
.\gradlew.bat :26.1.2-fabric:build
.\gradlew.bat :26.1.2-quilt:build
.\gradlew.bat :26.1.2-neoforge:build

# Default Fabric/Essential launch aliases (leading colon is required)
.\gradlew.bat :runClient
.\gradlew.bat :runEssentialClient

# Launch another loader
.\gradlew.bat :runClient -Ploader_platform=quilt
.\gradlew.bat :26.1.2-quilt:runClient
.\gradlew.bat :26.1.2-neoforge:runClient

# Stage jars and installation notes for all loaders
.\gradlew.bat modDistribution
```

Jars are in `versions/<minecraft>-<loader>/build/libs`.
Installation bundles are in `versions/<minecraft>-<loader>/build/distribution`.
The old `:neoforge` task prefix is replaced by `:26.1.2-neoforge`.
Build targets select their loader by name; `-Ploader_platform` only selects
the root launch aliases, and no longer selects which jar is built. Always qualify
run tasks with a leading colon: an unqualified runClient selects every loader.
The checked-in IntelliJ run configurations use the qualified aliases.

Edit shared sources in `src/main` and NeoForge bridges in `neoforge/src/main`.
Stonecutter manages generated source copies and build outputs inside `versions`.
The active editing target is `26.1.2-fabric`, configured in `stonecutter.gradle`.
Use the Stonecutter tasks in the Gradle tool window to switch editing targets.

## The existing 1.21.1 branch

The separate `1.21.1` branch remains unchanged. It uses Java 21, Veil 4.5.0,
GeckoLib 4, and its existing Fabric/NeoForge port sources. Those sources have
different Minecraft APIs and renderer implementations from this branch.

This setup does not yet combine that branch into the same Stonecutter source
tree. Adding 1.21.1 requires reconciling those source/resource differences,
selecting Veil for 1.21.1 and Amnetic for 26.1.2, and using the appropriate
Loom/mappings and Java toolchains. Merely adding a version in `settings.gradle`
would not produce a working 1.21.1 build.
