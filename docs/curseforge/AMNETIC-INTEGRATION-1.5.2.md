# Fabric/Quilt 1.21.1 replacement for 1.5.2

The moderator identified the nested Amnetic jar and its particle preset editor's
`EffectIO` class. This replacement removes that editor feature, including its
preset save/load/delete API, editor UI, key registration and editor profiling
hook. The game does not use the authoring editor.

The renderer's runtime classes and assets are integrated directly into Asterion
after production remapping. Asterion registers the renderer client initializer
and mixins. There is no nested Amnetic mod jar or external Amnetic dependency.
The renderer's original packages, attribution and license notices are retained;
the integrated jar includes an input SHA-256 and removal report at
`META-INF/asterion/amnetic-integration.txt`. LWJGL Assimp library jars remain
registered as native dependencies.
MixinExtras 0.5.5 is bundled explicitly so Quilt does not select its older 0.5.4
copy, which cannot process the release's mixin annotations.

Build with `:1.21.1:build :1.21.1:quiltCompatibleJar` from `ports/1.5`.
The packaging task rejects surviving editor classes or references. Run
`:1.21.1:runProductionSmokeClient` to exercise the actual release jar without a
separate Amnetic mod. The Quilt-labelled artifact has identical contents.
Add `-PquiltProductionSmoke` to run the same production scenario through Quilt.

Submit this replacement with the 1.5.2 label and manual publication selected.
These changes address the supplied moderation finding; CurseForge decides
whether another review is required.

Validation on 2026-10-17: the camera clearance regression, 14 gameplay tests,
2,181 JSON asset checks and release archive validation passed. Both Fabric
0.19.5 and Quilt 0.30.1 passed the full production client scenario with no
separate Amnetic mod or preset editor, including particles, bloom, physics,
forging, combat, advancements and cutscene control restoration. The two
1.21.1 artifacts are byte-identical (SHA-256
`f02d94bddad2adfafa77b31ca5b6a694a930db3b196dd4c18dfecfa3909dc654`).
