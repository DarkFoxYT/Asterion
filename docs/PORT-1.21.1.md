# Asterion 1.21.1 port

This branch targets Minecraft 1.21.1 and Java 21 on both Fabric and NeoForge.

## Runtime dependencies

### Fabric

- Fabric Loader 0.18.4
- Fabric API 0.116.8+1.21.1
- GeckoLib 4.9.2
- Veil 4.5.0

### NeoForge

- NeoForge 21.1.248
- Forgified Fabric API 0.116.15+2.3.5+1.21.1
- GeckoLib 4.9.2
- Veil 4.5.0

## Build and run

Use a Java 21 JDK.

```powershell
.\gradlew.bat buildAllLoaders
.\gradlew.bat runClient
.\gradlew.bat :neoforge:runClient
```

The loader jars are written to `build/libs` and `neoforge/build/libs`.

## Compatibility notes

- The shared gameplay, registration, world-generation, entity, NBT, recipe, and mixin code uses the Minecraft 1.21.1 APIs.
- Amnetic is no longer a runtime or build dependency. Veil supplies the 1.21.1 dynamic-light path for held Asterion flame lights on both loaders.
- NeoForge has loader-specific lifecycle and fluid compatibility bridges, while the main content implementation stays shared.
- The original post-1.21 client renderer suite is excluded on this branch because it is based on the newer render-state API. The port registers GeckoLib 4 entity renderers and safe particle fallbacks; bespoke block-entity, HUD, cinematic, ragdoll, and advanced entity effects need individual 1.21.1 ports for full visual parity.
- Broad heavy-water injection into vanilla waterloggable blocks is disabled because the 1.21.1 block-state bootstrap is not compatible with it. Asterion-owned heavy-water-capable blocks remain supported.
