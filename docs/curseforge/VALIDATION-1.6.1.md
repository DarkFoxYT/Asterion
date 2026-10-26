# Asterion 1.6.1 validation

- Release scope: the existing Minecraft 1.20.1, 1.21.1 and 26.1.2 branches, on Fabric, Quilt, Forge and NeoForge. No 2.0 content was added.
- All 12 packaged JARs passed `ports/1.5/gradle/verify-release.py --version 1.6.1`. Checksums are recorded in `ports/1.5/build/release-1.6.1-manifest.json`.
- Both older server suites passed all 16 required checks, 32 total. Builds also checked assets and camera clearance.
- Packaged Fabric clients on 1.20.1, 1.21.1 and 26.1.2 passed the dedicated floor/ragdoll scenario: underground eclipse rejection, recipient removal/re-entry, client-side late-packet rejection, sound cleanup, reciprocal Forge stair connections across 128 seeds and a 9-by-9 district sample excluding reserved rooms, and independent placement of both Minotaur gate halves.
- Equipped ancient skeleton fixtures fall from rest and retain armor and both held items without their source entity. Accelerated age checks verify survival past the previous 30-second limit and cleanup at two minutes. These are client corpses, not persistent saved entities; ordinary burning/cremation behavior remains active.
- Reproduced a 26.1.2 OpenGL error during custom-feature/hand drawing: the vanilla command encoder reused its cached shader after raw renderer passes had unbound the GL program. Invalidating its program/pipeline caches after those passes restored normal binding. The final equipped-corpse test reported zero OpenGL errors, without lowering visual settings.
- Existing Forge access repairs use the existing one-chunk-per-tick repair queue and a revision marker. The Catacombs backbone is preserved; the fix adds the required reciprocal east connection rather than changing parent routes.
- Tests use an 8 GiB heap, VSync off and Minecraft's unlimited FPS setting. Test classes and profiler fixtures are excluded from release JARs.

Evidence: `build/release-1.6.1-legacy-final.log`, `build/release-1.6.1-modern-final.log`, `build/release-1.6.1-audit-final.log`. The failing graphics reproduction is `build/floor-hotfix-26-graphics-trace.log`; its fixed rerun is `build/floor-hotfix-26-graphics-fix.log`.

The files are built locally. This patch has not been uploaded to CurseForge.
