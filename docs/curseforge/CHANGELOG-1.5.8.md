# Asterion 1.5.8

- Added shared zoom compatibility for mods such as Just Zoom and Essential: Amnetic starts each world frame with fresh camera matrices, so replaced projection setup cannot reuse a previous zoom level.
- Finish any interrupted Amnetic hand layer at the end of world rendering. Zoom mods hiding the hands can no longer leave subsequent rendering redirected into the hand framebuffer.
- On Minecraft 1.20.1 and 1.21.1, invalidate vanilla's cached shader and vertex bindings after Amnetic fullscreen rendering. Lighting and post effects no longer depend on visible hands to restore those caches.
