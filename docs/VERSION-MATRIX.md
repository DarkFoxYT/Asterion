# Asterion version and loader matrix

The `underworld` branch is the consolidated workspace. Its root project is Asterion 2.0.0 for Minecraft 26.1.2, with Fabric as the default Stonecutter target. The complete Asterion 1.5 workspace lives in `ports/1.5`; its Minecraft 26.1.2 project lives in `ports/1.5/ports/26.1.2`.

| Release | Minecraft | Fabric | Quilt | Forge | NeoForge |
| --- | --- | --- | --- | --- | --- |
| 1.5 | 1.20.1 | Production client smoke passes | Fabric-compatible jar; game tests pass | Builds; dev client fails in dependency mixin | Builds; dev client fails in dependency mixin |
| 1.5 | 1.21.1 | Production client smoke passes | Fabric-compatible jar; game tests pass | Dev client opens; gameplay unverified | Client smoke passes |
| 1.5 | 26.1.2 | Opens integrated world | Opens integrated world | Opens integrated world | Opens integrated world |
| 2.0.0 | 1.20.1 | Underworld backport pending | Underworld backport pending | Underworld backport pending | Underworld backport pending |
| 2.0.0 | 1.21.1 | Underworld backport pending | Underworld backport pending | Underworld backport pending | Underworld backport pending |
| 2.0.0 | 26.1.2 | Builds, default | Builds | Builds | Builds |

"Builds" means the Gradle build succeeded; it does not imply a complete gameplay playthrough. The 1.20.1 NeoForge jar builds but its dev client currently fails in `fabric-screen-api-v1.mixins.json:MinecraftClientMixin`, so it is not a working release. Do not relabel a 1.5 jar as 2.0: the older 2.0 builds require a real Underworld Java and asset backport to the older Minecraft APIs.

The 1.20.1 and 1.21.1 Fabric production smoke runs enter a Labyrinth world and exercise rendering, OpenGL error checks, physics, forging, the Cursed Brazier encounter, and cutscenes. The 1.21.1 NeoForge development client passed the same scenario. The Fabric Amnetic jars contain the vertex-array state restoration from Amnetic commit `908be63`; the 1.5 emissive Geo queue also binds a vertex array for its deferred vanilla flush. Forge 1.20.1 and NeoForge 1.20.1 development clients fail while applying mixins from the bundled Forgified Fabric API; the Forge 1.20.1 development client also exposes mapping problems in other bundled dependencies if the Fabric API mixins are remapped. All four 26.1.2 development clients entered an integrated world, but their full gameplay scenarios were not tested.

Minecraft 26.1.2 Fabric, Quilt, NeoForge, and Forge build from the repository root. The 1.5 projects build independently from `ports/1.5` and `ports/1.5/ports/26.1.2`.
