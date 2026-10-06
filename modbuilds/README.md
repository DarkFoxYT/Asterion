# Asterion mod builds

This folder contains **Asterion jars only**. Names include the Asterion release,
loader, and Minecraft version. **Amnetic is embedded in every Asterion jar**;
do not install it separately. Other dependencies, including Fabric API and
GeckoLib, still need matching external versions.

From the repository root, run
`powershell -ExecutionPolicy Bypass -File tools/build-all-mods.ps1` to build
every configured target. `-Offline` uses cached dependencies only. Each individual
Gradle build also copies its finished Asterion jar here automatically.

Use `-Release 1.5` to build only the compatibility release: Minecraft 1.20.1,
1.21.1, and 26.1.2 on Fabric, Quilt, Forge, and NeoForge (12 jars). This also
runs `tools/verify-v15-release.py` with Python 3.11+ to check archive integrity,
loader entrypoints, mixin classes and Java levels, and embedded dependencies.

The `1.5` jars are built from `ports/1.5`; the `2.0.0` jars are built from the
Underworld source at the repository root. The older Minecraft 1.20.1 and 1.21.1
Quilt jars use the Fabric-compatible code but carry Quilt-labelled filenames.

Compilation alone does not confirm that a loader starts or that every mechanic
works. The development clients now start for 1.20.1 Forge and NeoForge and
1.21.1 Forge. Fabric and Quilt client smoke suites passed on both older
Minecraft versions, and the 1.21.1 NeoForge client smoke suite passed. The
packaged Forge jars have not yet had an in-game playthrough.

See [the 1.5 validation report](../ports/1.5/docs/RELEASE-VALIDATION-2026-09-29.md)
for the current build matrix, runtime coverage, fixes, and reproduction commands.

## Asterion 2.0 Amnetic update (2026-10-05)

The four 26.1.2 loader builds use the updated Amnetic checkout with our
composable window hook and preset safety fixes. All four builds, 49 private API
checks, the startup compatibility check, and embedded dependency validation
passed. This does not establish in-game or visual validation.

Rebuild the renderer jars with `tools/update-amnetic.ps1` after building the
adjacent Amnetic checkout. `libs/amnetic-build.json` records upstream and patched
artifact hashes, the source commit, and whether the checkout has local changes.
`tools/verify-amnetic-update.py` checks all four packages against those hashes and
verifies the window and preset fixes are embedded. The 2.0 build script runs it.

The 26.2 and 26.3 Fabric/Quilt targets compile and have automated in-game
rendering coverage in Labyrinth and Limbo. Their native GPU post passes, particle
atlas, instance layouts and reversed scene depth have been repaired. Bloom uses
depth-tested HDR emitters and a compressed final composite. The wall screenshot
test checks that particles appear when visible and disappear behind solid blocks.

Build all eight current 2.0 targets with
`./tools/build-all-mods.ps1 -Release 2.0.0 -Native`.
Native targets remain opt-in with `-PasterionCompatibilityPorts=true` for direct
Gradle commands. Rebuild their embedded renderer using
`./tools/update-amnetic.ps1 -NativeOnly`, then validate all four native packages
with `python tools/verify-amnetic-update.py --native`. The source checkout and
hashes are recorded in `libs/amnetic-native-build.json`.
See [the rendering validation report](../docs/RENDERING-VALIDATION-2026-10-05.md)
for reproduction commands and the limits of this coverage.

26.2/26.3 Forge and NeoForge still need loader ports; the Forgified Fabric API
configured by this project targets 26.1.2. The older
1.20.1/1.21.1 artifacts remain Asterion 1.5 and do not contain the 2.0 Underworld
backport. These unfinished combinations must not be tagged as compatible with
Asterion 2.0.

## CurseForge uploads

`tools/upload-curseforge.ps1` targets CurseForge project **1671346**. It validates
all 12 release jars and reads dependency declarations from the built archives.
Identical Fabric/Quilt jars share one CurseForge listing with both loader tags.
The current matrix therefore needs 10 distinct uploads for 12 build targets.
The 1.5 build restores the Amnetic local-preset safety fix with
`tools/patch-amnetic-preset-safety.py`: validated names, confined paths, rejected
links, bounded JSON reads, and atomic writes. It preserves renderer mixins and
loader-specific compatibility patches. Release validation checks the embedded
safety code and renderer mixin classes in every jar. This repairs a packaging
regression; it does not guarantee automatic CurseForge approval.
It defaults to Release, uses `docs/curseforge/CHANGELOG-1.5.md`, and always holds
approved files for manual release, keeping them unpublished until you release them.
Run it without arguments to preview the matrix; nothing is uploaded by default.
The complete metadata preview is saved in `build/curseforge-upload-plan.json`.

Create an author upload token from the API Tokens link in the
[official upload API documentation](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api).
Store it locally as the environment variable `CURSEFORGE_API_TOKEN`; never put
it in source files or command arguments. Run `tools/set-curseforge-token.ps1` in
your own terminal to save it with hidden input. The PowerShell uploader reads
the Windows user environment automatically; a restart is not required.

Provide a UTF-8 Markdown changelog file and choose the release type:

```powershell
# Preview with the actual release metadata:
.\tools\upload-curseforge.ps1 -Changelog .\changelog.md -ReleaseType release
# Build, validate, then upload:
.\tools\upload-curseforge.ps1 -Build -Upload -Changelog .\changelog.md -ReleaseType release
```

Manual release is always enabled for this project. CurseForge
moderation still applies. The uploader stores successful file IDs under `build/`
and skips the same jar hash on later runs. Keep these receipts to avoid duplicate
uploads. If a request has an unknown outcome, it stops: check the project's Files
page before manually clearing that pending receipt. Changing metadata on an
already uploaded jar requires updating the existing CurseForge file, rather than
uploading the identical jar again.

File uploads use curl with native TLS on Windows and report progress about every
30 seconds. The token is supplied through stdin, never curl's command line or a
configuration file. A slow connection can take as long as needed while it
continues transferring data; a transfer below one byte per second for 15 minutes
stops. Incomplete requests with confirmed byte counts are recorded separately
from requests whose final outcome is unknown. Version tags
are resolved using CurseForge's Minecraft version families, avoiding similarly
named Bedrock/add-on tags.
