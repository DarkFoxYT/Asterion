# Bloom and emissive rendering

Bloom keeps the normal full-resolution emissive surfaces and renders their soft halos into
quarter-, three-eighths-, or half-resolution buffers for Low, Medium, and High respectively.
Compared with the previous default High scale of 0.95, the half-resolution target contains
about 72% fewer pixels. This is a buffer-area reduction, not a measured FPS improvement.

Whole-scene brightness extraction is now opt-in via `sceneBloom: true` in
`config/asterion-emissive.json`. Explicit emissive meshes, particles and populated emissive
G-buffers still contribute. Low quality continues to omit whole-scene extraction. Existing
emission strengths, occlusion and depth-format corrections are preserved.

The renderer skips an unused scene-colour copy when the prefilter reads the emissive G-buffer.
It also skips Asterion CPU batches already proven empty in the current frame and treats the
bone source as idle when it has no submissions. Unknown and third-party sources are retained.
Bone emission visits only the current frame's active batches and retains pooled instance data.

Instance VBOs retain their capacity and identity. Uploads use invalidating mapped storage, allowing
the driver to supply fresh backing storage while earlier draws finish. No unsynchronized writes
or blocking GPU fences are introduced. Failed mappings fall back to orphaning and subdata upload.
The original instance-buffer lifecycle still owns allocation growth and deletion.

Run the real-driver checks with:

```powershell
.\gradlew.bat -I tools/underworld-validation.gradle checkEmissiveUploads checkBloomDepth checkUnderworldShaders
```

The upload probe verifies byte-for-byte contents, nonzero source offsets, empty batches,
growth/shrink cycles, retained capacity and GL errors. The depth probe checks occlusion copies
at reduced resolutions. These do not substitute for an in-game performance capture: compare
frame times in the same scene, resolution, shader pack and camera position with bloom off/on,
and inspect glows through walls, particle effects, resize, resource reload and world changes.
