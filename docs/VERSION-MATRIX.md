# Asterion version and loader matrix

The `underworld` branch is the consolidated workspace. Its root project is Asterion 2.0.0 for Minecraft 26.1.2, with Fabric as the default Stonecutter target. The complete Asterion 1.5 workspace lives in `ports/1.5`; its Minecraft 26.1.2 project lives in `ports/1.5/ports/26.1.2`.

| Release | Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- | --- |
| 1.5 | 1.20.1 | Builds | Fabric-compatible jar; game tests pass | Builds | Builds; client launch fails in Fabric API mixin |
| 1.5 | 1.21.1 | Builds | Fabric-compatible jar; game tests pass | Builds | Builds |
| 1.5 | 26.1.2 | Builds | Builds | Builds | Builds |
| 2.0.0 | 1.20.1 | Underworld backport pending | Underworld backport pending | Underworld backport pending | Underworld backport pending |
| 2.0.0 | 1.21.1 | Underworld backport pending | Underworld backport pending | Underworld backport pending | Underworld backport pending |
| 2.0.0 | 26.1.2 | Builds, default | Builds | Builds | Builds |

"Builds" means the Gradle build succeeded; it does not imply a complete gameplay playthrough. The 1.20.1 NeoForge jar builds but its dev client currently fails in `fabric-screen-api-v1.mixins.json:MinecraftClientMixin`, so it is not a working release. Do not relabel a 1.5 jar as 2.0: the older 2.0 builds require a real Underworld Java and asset backport to the older Minecraft APIs.

Minecraft 26.1.2 Fabric, Quilt, NeoForge, and Forge build from the repository root. The 1.5 projects build independently from `ports/1.5` and `ports/1.5/ports/26.1.2`.
