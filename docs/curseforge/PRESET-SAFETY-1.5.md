# Asterion 1.5 preset safety repair

The original 1.5 packages embedded Amnetic copies without the earlier fix in
`amnetic-preset-safety.patch`. Their `EffectIO` methods used editor-supplied
names in local read/delete paths and did not enforce the earlier file bounds.

`tools/patch-amnetic-preset-safety.py` restores that fix to the renderer inputs
used by the 1.5 build. A small adapter preserves each loader's Minecraft mapping
names while routing the existing file operations through the safety helper.
Load/delete names are validated before path construction. Presets stay under
the game's `amnetic/particles` directory; links and unsafe names are rejected,
JSON is limited to 1 MiB, and saves use atomic replacement rather than truncating
a potentially hard-linked file. Existing renderer mixins remain intact.

The build runs the storage and adapter regressions, then release validation
checks the shipped nested renderer for the helper, guarded file operations,
and valid mixin classes/Java levels. Original renderer input backups and patch
hashes are kept locally under `build/amnetic-preset-safety`.

This is a repair to actual file handling, not a change designed to hide code
from moderation. CurseForge has not supplied a reason tying its current manual
review to this regression, and approval remains under CurseForge's control.
Rebuilding local files does not replace the bytes of previously submitted files.
