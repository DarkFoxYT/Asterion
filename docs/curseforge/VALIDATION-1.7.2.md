# Asterion 1.7.2 — Embeddium checks

Tested on Windows with an NVIDIA RTX 5070 on 2026-11-11.

- Minecraft 1.20.1, Forge 47.4.28, Embeddium 0.3.31: the packaged release reached the title screen and passed client-mixin and entity-attribute checks.
- Minecraft 1.20.1, NeoForge 47.1.106, Embeddium 0.3.31: the packaged release passed the same startup checks.
- Minecraft 1.21.1, NeoForge 21.1.248, Embeddium 1.0.15: the packaged release passed startup, texture-hook exclusion, fullscreen/windowed transitions and framebuffer checks. A separate world run passed Overworld and Labyrinth entry, chunk rendering, ragdoll recovery and the OpenGL error check.
- Minecraft 1.21.1, NeoForge 21.1.248 without a renderer mod: startup passed and Asterion's vanilla texture-animation cache remained active.

The report supplied no Minecraft version, loader, crash log or rendering symptoms. These checks establish compatibility for these combinations, but cannot identify the reporter's specific failure. The texture-hook detection now checks class resources without requesting renderer-mixin bytecode transformation, and covers the older Sodium package used by Embeddium 0.3.31.

To run the NeoForge 1.21.1 regression from `ports/1.5`, supply the path to an official Embeddium 1.0.15 jar:

```powershell
./gradlew.bat :neoforge:runPackagedSmokeClient -PembeddiumSmoke '-PembeddiumJar=C:/path/to/embeddium-1.0.15+mc1.21.1.jar'
```

Add `-PstartupSmoke` to check startup and fullscreen transitions instead of the world scenario. The Embeddium profile uses its own test directory.
