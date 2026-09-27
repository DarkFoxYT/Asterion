# Asterion mod builds

This folder contains **Asterion jars only**. Names include the Asterion release,
loader, and Minecraft version. **Amnetic is embedded in every Asterion jar**;
do not install it separately. Other dependencies, including Fabric API and
GeckoLib, still need matching external versions.

From the repository root, run
`powershell -ExecutionPolicy Bypass -File tools/build-all-mods.ps1` to build
every configured target. `-Offline` uses cached dependencies only. Each individual
Gradle build also copies its finished Asterion jar here automatically.

The `1.5` jars are built from `ports/1.5`; the `2.0.0` jars are built from the
Underworld source at the repository root. The older Minecraft 1.20.1 and 1.21.1
Quilt jars use the Fabric-compatible code but carry Quilt-labelled filenames.

Compilation alone does not confirm that a loader starts or that every mechanic
works. In particular, the 1.20.1 Forge and NeoForge ports still have known
development-client startup failures.
