"""Remap SRG member names inside a local mod jar, including nested jars.

Only development runs use this output. Release jars keep their original bytes.
"""

import io
import re
import struct
import sys
import zipfile
from pathlib import Path


def read_names(mapping_path: Path) -> dict[str, str]:
    names = {}
    for line in mapping_path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if parts and parts[0] in {"FD:", "MD:"}:
            source = parts[1].rsplit("/", 1)[-1]
            target = parts[2 if parts[0] == "FD:" else 3].rsplit("/", 1)[-1]
            if source.startswith(("f_", "m_")):
                names[source] = target
    return names


def remap_class(data: bytes, names: dict[str, str]) -> tuple[bytes, int]:
    if data[:4] != b"\xca\xfe\xba\xbe":
        return data, 0
    count = struct.unpack_from(">H", data, 8)[0]
    output = bytearray(data[:10])
    index = 10
    entry = 1
    changes = 0
    pattern = re.compile(r"(?<![A-Za-z0-9_$])(?:f_|m_)\d+_(?![A-Za-z0-9_$])")
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4,
             10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while entry < count:
        tag = data[index]
        output.append(tag)
        index += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, index)[0]
            raw = data[index + 2:index + 2 + length]
            try:
                value = raw.decode("utf-8")
                changed, n = pattern.subn(lambda match: names.get(match.group(), match.group()), value)
                encoded = changed.encode("utf-8")
                changes += n if changed != value else 0
            except UnicodeError:
                encoded = raw
            output.extend(struct.pack(">H", len(encoded)))
            output.extend(encoded)
            index += 2 + length
        else:
            size = sizes[tag]
            output.extend(data[index:index + size])
            index += size
            if tag in {5, 6}:
                entry += 1
        entry += 1
    output.extend(data[index:])
    return bytes(output), changes


def remap_zip(data: bytes, names: dict[str, str]) -> tuple[bytes, int]:
    source = io.BytesIO(data)
    target = io.BytesIO()
    changes = 0
    with zipfile.ZipFile(source) as input_zip, zipfile.ZipFile(target, "w") as output_zip:
        for entry in input_zip.infolist():
            content = input_zip.read(entry)
            if entry.filename.endswith(".class"):
                content, count = remap_class(content, names)
                changes += count
            elif entry.filename.endswith(".jar") and entry.filename.startswith("META-INF/jarjar/"):
                content, count = remap_zip(content, names)
                changes += count
            output_zip.writestr(entry, content)
    return target.getvalue(), changes


def main() -> None:
    source, mapping, destination = map(Path, sys.argv[1:])
    result, changes = remap_zip(source.read_bytes(), read_names(mapping))
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(result)
    print(f"Remapped {changes} SRG member references in {source.name}")


if __name__ == "__main__":
    main()
