"""Give Asterion's native adapters private identities, including renderer links.

Only constant-pool UTF8 entries are changed; bytecode and stack maps are intact.
This avoids split packages with optional mods that bundle actual Fabric APIs.
"""
from pathlib import Path
import struct
import sys
import zipfile

OLD = b"net/fabricmc/"
NEW = b"net/krodark/asterion/nativeapi/"


def remap(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        return data
    output = bytearray(data[:10])
    pos, index = 10, 1
    count = int.from_bytes(data[8:10], "big")
    while index < count:
        tag = data[pos]
        output.append(tag)
        pos += 1
        if tag == 1:
            size = int.from_bytes(data[pos:pos + 2], "big")
            value = data[pos + 2:pos + 2 + size]
            value = value.replace(OLD, NEW).replace(OLD.replace(b"/", b"."), NEW.replace(b"/", b"."))
            output.extend(struct.pack(">H", len(value)))
            output.extend(value)
            pos += size + 2
        else:
            size = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
                    12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}[tag]
            output.extend(data[pos:pos + size])
            pos += size
            if tag in (5, 6):
                index += 1
        index += 1
    output.extend(data[pos:])
    return bytes(output)


def isolate(path):
    temporary = path.with_suffix(".isolated.jar")
    with zipfile.ZipFile(path) as source, zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED) as target:
        for entry in source.infolist():
            data = source.read(entry)
            if entry.filename.endswith(".class"):
                data = remap(data)
            entry.filename = entry.filename.replace(OLD.decode(), NEW.decode())
            target.writestr(entry, data)
    temporary.replace(path)


if __name__ == "__main__":
    for argument in sys.argv[1:]:
        isolate(Path(argument))
