# Asterion version and loader matrix

The `underworld` branch is the consolidated workspace. Its root project is Asterion 2.0.0 for Minecraft 26.1.2, with Fabric as the default Stonecutter target. The complete Asterion 1.5 workspace lives in `ports/1.5`; its Minecraft 26.1.2 project lives in `ports/1.5/ports/26.1.2`.

| Release | Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- | --- |
| 1.5 | 1.20.1 | Build target | Fabric-compatible jar | Build target | Port pending |
| 1.5 | 1.21.1 | Build target | Fabric-compatible jar | Build target | Build target |
| 1.5 | 26.1.2 | Build target | Build target | Build target | Build target |
| 2.0.0 | 1.20.1 | Underworld backport pending | Underworld backport pending | Underworld backport pending | Underworld backport pending |
| 2.0.0 | 1.21.1 | Underworld backport pending | Underworld backport pending | Underworld backport pending | Underworld backport pending |
| 2.0.0 | 26.1.2 | Build target, default | Build target | Build target | Build target |

"Build target" means the Gradle project exists; it does not imply a complete gameplay playthrough. Quilt on 1.20.1 and 1.21.1 currently uses the Fabric-compatible jar and needs a separate runtime validation. Do not relabel a 1.5 jar as 2.0: the older 2.0 builds require a real Underworld Java and asset backport to the older Minecraft APIs.

Minecraft 26.1.2 Fabric, Quilt, NeoForge, and Forge build from the repository root. The 1.5 projects build independently from `ports/1.5` and `ports/1.5/ports/26.1.2`.
