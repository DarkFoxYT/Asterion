# Asterion 1.6.2 extra ports

Only Minecraft 1.21.11 and 26.3 use version 1.6.2 in this project.
The existing Minecraft versions retain their 1.6.1 hotfix version.

The source baseline is the 1.6.1 release, commit 17f4df94. Preserve its gameplay,
content and visuals. Do not import 2.0 gameplay, content, features or fixes.
Use the custom Amnetic builds for the matching Minecraft version. Limit changes
to the API and loader adaptations required to run this baseline on the extra versions.

Exclude orphan 2.0 block placeholders from the modern source snapshot:
`verdant_tiles`, `bonified_pillar`, `bonifiedblock`, `overgrown_deadstone`,
and `ancient_grass`. These entries have no 1.6 assets and are absent from the
maintained older 1.6 source. Do not copy their 2.0 assets into these ports.

These ports are unfinished until all target builds and runtime checks pass.
Publication is on hold at the user's request. Do not upload these builds until
the user authorizes publishing again; development and testing may continue.
The user also requested no further game launches. Use the non-launch verification
task and record live rendering/world tests as pending until launches are authorized.
Committing and pushing the source changes to GitHub is authorized.
The internal `26.1.2-source` node only identifies the source API version; it
cannot build or publish another 26.1.2 release.
