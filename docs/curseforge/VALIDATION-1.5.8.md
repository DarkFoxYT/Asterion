# Asterion 1.5.8 zoom patch validation

This patch targets the shared rendering paths used by zoom mods, with no optional-mod dependency or changes to their keybinds and settings.

Validated on 23 October 2026. The 16 required game tests also pass on both Minecraft 1.20.1 and 1.21.1.

All twelve Fabric, Quilt, Forge and NeoForge distributions build and pass `ports/1.5/gradle/verify-release.py`. The audit checks version metadata, required Mixins and their classes, native dependencies, supported Java versions, nested renderer repairs and absence of test classes. SHA-256 values are saved in `ports/1.5/build/release-1.5.8-manifest.json`; local JARs are in `modbuilds`. These builds have not been uploaded to CurseForge.

- Reproduced the shader-cache failure using the published 1.5.7 Fabric/Quilt 1.21.1 JAR (CurseForge file 9293111, SHA-256 `667738c9ccbb3da7d0dfdd7cc78f330213733524e673d07b1dae7dc063f025d2`). After an Amnetic fullscreen pass, reapplying vanilla's previously used shader leaves the GL program unbound when hand cleanup is skipped. The regression fails on that original artifact and passes with the patch.
- Packaged 1.21.1 client with Just Zoom 3.0.1 and Konkrete 1.9.9: the real Just Zoom key mapping activates zoom. Repeated FOV changes and hand cancellation run for 120 world frames; 63 interrupted hand layers recover, subsequent frames retain no layer redirect, vanilla's shader rebinds, and no GL debug errors occur.
- The final 1.5.8 packaged 1.21.1 client also passes that scenario with Iris, Sodium and Complementary Unbound r5.9.3 enabled alongside Just Zoom. The log confirms the selected shader pack loaded and the real zoom activated.
- Packaged 1.20.1 client: the equivalent 120-frame FOV/hidden-hand scenario passes, with 63 interrupted layers recovered and successful shader rebinding. This run uses synthetic zoom callbacks rather than installing a zoom mod.
- Packaged 26.1.2 client: 120 zoom frames retain finite projection and inverse matrices, demonstrate actual projection-scale changes, and recover 64 interrupted hand layers. This run changes the camera lens through Amnetic's API and simulates a zoom mod cancelling the hand renderer.

[Essential's implementation](https://github.com/SparkUniverse/Essential-Mod/blob/main/src/main/java/gg/essential/mixins/transformers/client/renderer/MixinEntityRenderer_Zoom.java) was inspected to check its FOV modification path, but its binary was not directly exercised. The report did not identify the original Minecraft version, loader or zoom mod, so these results establish the tested failure and shared-path recovery rather than reproducing every reported visual symptom.

Runtime logs (workspace-relative):

- `ports/1.5/build/zoom-baseline-1.21.1.log` (expected regression failure on the original release)
- `ports/1.5/build/zoom-justzoom-and-legacy-runtime.log`
- `ports/1.5/build/zoom-shaders-and-release-build.log`
- `ports/1.5/ports/26.1.2/build/zoom-compat-26.log`
- `ports/1.5/ports/26.1.2/build/zoom-release-build.log`
- `ports/1.5/build/release-1.5.8-audit.log`

The test Mixins and helper classes belong to opt-in test source sets and must not ship in release JARs. The release audit verifies that exclusion and requires the correct zoom and shader-cache Mixins for every supported distribution.
