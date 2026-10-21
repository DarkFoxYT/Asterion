# Asterion 1.5.4 — Forge and NeoForge fixes

- Updated the Minecraft 1.20.1 Forge build to Forge 47.4.28.
- Updated the Minecraft 1.21.1 Forge build to Forge 52.1.20.
- Updated the Minecraft 26.1.2 Forge build to Forge 64.1.5.
- Updated the required Forge versions in the mod metadata to match the builds.

- Fixed the NeoForge 1.21.1 startup crash during entity attribute registration on NeoForge 21.1.264. Entity attributes now use NeoForge's registration event instead of writing to an immutable Fabric registry. Fixes [#1: Crash on start](https://github.com/DarkFoxYT/Asterion/issues/1).
- Retained the dedicated-server-safe Assimp packaging fix from 1.5.3.
- Fixed startup compatibility with Wither: Reincarnated on NeoForge 1.21.1. Verified both mods together in packaged client and dedicated-server checks.

The NeoForge 1.21.1 build requires NeoForge 21.1.264 or newer and Java 21.
