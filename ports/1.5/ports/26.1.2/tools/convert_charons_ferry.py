"""Convert the authored Blockbench ferry into the GeckoLib runtime asset layout."""

from __future__ import annotations

import base64
import json
import shutil
import sys
from pathlib import Path


def face_uv(face: dict) -> dict:
    uv = face.get("uv", [0, 0, 0, 0])
    return {"uv": uv[:2], "uv_size": [uv[2] - uv[0], uv[3] - uv[1]]}


def convert(source: Path, root: Path) -> None:
    model = json.loads(source.read_text(encoding="utf-8"))
    elements = {element["uuid"]: element for element in model.get("elements", [])}
    bones: list[dict] = []
    used_names: set[str] = set()

    def unique_name(raw: str | None, index: int) -> str:
        base = "".join(ch.lower() if ch.isalnum() else "_" for ch in (raw or f"ferry_{index}"))
        base = base.strip("_") or f"ferry_{index}"
        name = base
        suffix = 2
        while name in used_names:
            name = f"{base}_{suffix}"
            suffix += 1
        used_names.add(name)
        return name

    def cube(element: dict) -> dict:
        start = element.get("from", [0, 0, 0])
        end = element.get("to", [0, 0, 0])
        result = {
            "origin": [-end[0], start[1], start[2]],
            "size": [end[0] - start[0], end[1] - start[1], end[2] - start[2]],
            "uv": {name: face_uv(value) for name, value in element.get("faces", {}).items()
                   if value.get("texture") is not None},
        }
        if element.get("inflate"):
            result["inflate"] = element["inflate"]
        rotation = element.get("rotation", [0, 0, 0])
        if any(rotation):
            origin = element.get("origin", [0, 0, 0])
            result["pivot"] = [-origin[0], origin[1], origin[2]]
            result["rotation"] = [rotation[0], -rotation[1], -rotation[2]]
        return result

    def walk(group: dict, parent: str | None = None) -> None:
        name = unique_name(group.get("name"), len(bones))
        origin = group.get("origin", [0, 0, 0])
        bone = {"name": name, "pivot": [-origin[0], origin[1], origin[2]]}
        if parent:
            bone["parent"] = parent
        rotation = group.get("rotation", [0, 0, 0])
        if any(rotation):
            bone["rotation"] = [rotation[0], -rotation[1], -rotation[2]]
        direct_cubes = [cube(elements[child]) for child in group.get("children", [])
                        if isinstance(child, str) and child in elements]
        if direct_cubes:
            bone["cubes"] = direct_cubes
        bones.append(bone)
        for child in group.get("children", []):
            if isinstance(child, dict):
                walk(child, name)

    for item in model.get("outliner", []):
        if isinstance(item, dict):
            walk(item)

    def referenced_elements(items: list) -> set[str]:
        found: set[str] = set()
        for item in items:
            if isinstance(item, str):
                found.add(item)
            elif isinstance(item, dict):
                found.update(referenced_elements(item.get("children", [])))
        return found

    referenced = referenced_elements(model.get("outliner", []))
    loose = [cube(element) for uid, element in elements.items() if uid not in referenced]
    if loose:
        bones.append({"name": unique_name("unparented", len(bones)), "pivot": [0, 0, 0], "cubes": loose})

    texture = model["textures"][0]
    width = int(texture.get("uv_width") or texture.get("width") or 512)
    height = int(texture.get("uv_height") or texture.get("height") or 512)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.charons_ferry",
                "texture_width": width,
                "texture_height": height,
                "visible_bounds_width": 8,
                "visible_bounds_height": 5,
                "visible_bounds_offset": [0, 1.5, 0],
            },
            "bones": bones,
        }],
    }

    geo_path = root / "src/main/resources/assets/asterion/geckolib/models/underworld/entity/charons_ferry.geo.json"
    texture_path = root / "src/main/resources/assets/asterion/textures/underworld/entity/charons_ferry.png"
    source_copy = root / "docs/underworld/source/charons_ferry.bbmodel"
    geo_path.parent.mkdir(parents=True, exist_ok=True)
    texture_path.parent.mkdir(parents=True, exist_ok=True)
    source_copy.parent.mkdir(parents=True, exist_ok=True)
    geo_path.write_text(json.dumps(geo, indent=2) + "\n", encoding="utf-8")
    encoded = texture["source"].split(",", 1)[1]
    texture_path.write_bytes(base64.b64decode(encoded))
    shutil.copy2(source, source_copy)
    print(f"Converted {len(elements)} cubes into {len(bones)} bones")


if __name__ == "__main__":
    source_arg = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.home() / "Downloads/charons_ferry.bbmodel"
    project_root = Path(__file__).resolve().parents[1]
    convert(source_arg, project_root)
