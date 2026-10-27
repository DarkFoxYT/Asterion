# Building the 1.6.2 extra ports

This isolated project targets Minecraft 1.21.11 and 26.3 on Fabric, Quilt,
Forge and NeoForge. Existing targets remain on 1.6.1. The gameplay and assets
come from the 1.6.1 baseline; this project adds version and loader adapters.

From the repository root, use JDK 25 for Gradle. Compilation uses Java 21 for
1.21.11 and Java 25 for 26.3. Set ASTERION_PYTHON to a working Python 3.11+
executable, or have python on PATH.

```powershell
.\gradlew.bat -p ports/1.5/ports/1.6.2 verifyExtraPortsWithoutLaunching
```

This task builds all eight production JARs, checks Asterion and Amnetic mixin
bindings, runs the camera geometry regressions, decodes the 26.3 data formats,
and audits the finished archives and their embedded dependencies. It does not
start a Minecraft client, dedicated server or world. These checks cannot prove
rendering or modpack behavior in a live game; see VALIDATION-1.6.2.md.

Finished archives are collected in the repository's modbuilds directory. Each
filename includes its Minecraft version and loader. These files are local only;
publication remains on hold.

Shared editable sources are in src/main. Keep Stonecutter's active version at
26.1.2-source: it identifies the source API and has no build/publication tasks.
The adapters in gradle/port-api.py and gradle/port-api263.py transform generated
copies for each target. Do not edit versions/*/build/generated files.

Custom renderer inputs are in libs; amnetic-build.json records their provenance
and prepared hashes. The 1.21.11 NeoForge bootstrap is in src/amnetic12111.
Native event adapters are isolated into Asterion's private namespace when the
production JARs are packaged.
