# Asterion maintained extra ports

This project adds Minecraft 1.21.11 and 26.3 to the 1.6 release line.
The current hotfix is 1.6.3. Existing Minecraft versions ship under the same
version, retaining all 1.6.1 and 1.6.2 fixes. The user explicitly authorized
publishing 1.6.3 for chain/armor texture faults and related rendering fixes.

The source baseline is the 1.6.1 release, commit 17f4df94. Preserve its gameplay,
content and visuals. Do not import 2.0 gameplay, content, features or fixes.
Use the custom Amnetic builds for the matching Minecraft version. Limit changes
to the API and loader adaptations required to run this baseline on the extra versions.

Exclude orphan 2.0 block placeholders from the modern source snapshot:
`verdant_tiles`, `bonified_pillar`, `bonifiedblock`, `overgrown_deadstone`,
and `ancient_grass`. These entries have no 1.6 assets and are absent from the
maintained older 1.6 source. Do not copy their 2.0 assets into these ports.

These ports are unfinished until all target builds and runtime checks pass.
On 27 October 2026 the user authorized continuing compatibility work, game
validation and CurseForge publication once the release works. Run final archive
and runtime checks before uploading; record their actual results.
Committing and pushing the source changes to GitHub is authorized.
The internal `26.1.2-source` node only identifies the source API version; it
cannot build or publish another 26.1.2 release.
