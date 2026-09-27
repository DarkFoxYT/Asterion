# Amnetic port status

The Fabric and Quilt distributions now use standalone Amnetic jars built from
`dark/forge-port` at Amnetic commit `d52db1d`. The matching jars for Minecraft
1.20.1, 1.21.1, and 26.1.2 are in `libs/` and `ports/26.1.2/libs/`.
They are installed beside Asterion rather than nested in it.

Labyrinth's 1.21.1 and 26.1.2 NeoForge targets still use their previous
NeoForge-specific Amnetic adapters. The new Amnetic Forge jars target Minecraft
Forge and cannot replace those NeoForge adapters.

Minecraft Forge 1.20.1 is staged as a compatibility pack by `stageForge1201`.
It uses Forge 47.1.3, native Forge Amnetic, Connector, Forgified Fabric API, and
the shared Asterion jar. The task pins and verifies SHA-512 hashes for downloaded
runtime dependencies.

Minecraft 1.21.1 and 26.1.2 remain native NeoForge targets. Connector and
Forgified Fabric API moved to NeoForge after 1.20.1, so calling those jars Forge
builds would be inaccurate. Supporting the separate Minecraft Forge loader on
those versions requires replacing the shared Fabric API use with Forge-native
platform adapters; the current source tree has 87 loader-dependent files.
