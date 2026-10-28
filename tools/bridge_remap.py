#!/usr/bin/env python3
"""Point a mod jar's references at the bridge's own stand-ins (docs/bridge/m1-design.md).

    bridge_remap.py --jar MOD.jar --out REMAPPED.jar

Every class the mod names from the game, its loader or their libraries (`GROUPS`) is renamed to
`dev/horizonlab/bridge/host/<group>/<Outer>[$Inner]`. Only constant-pool names and descriptors
change: method bodies, and so the mod's own logic, are copied byte for byte. String literals are
left alone, so code that names game classes in strings (reflection) is not remapped. Prints the
mapping and both jars' SHA-256.
"""
import argparse
import hashlib
from pathlib import Path
import re
import struct
import zipfile

HOST = 'dev/horizonlab/bridge/host/'
GROUPS = [('net/minecraft/', 'mc'), ('net/neoforged/', 'neo'), ('com/mojang/brigadier/', 'brig'),
          ('com/mojang/', 'mojang'), ('org/slf4j/', 'slf4j')]
DESCRIPTOR_CLASS = re.compile(r'L([^;<>]+)([;<])')
# Entry sizes after the tag byte, for the constants that are not Utf8.
SIZES = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}


def target(name, mapping):
    """The stand-in for a game class name, or None for a name that stays."""
    for prefix, group in GROUPS:
        if name.startswith(prefix):
            new = HOST + group + '/' + name.rsplit('/', 1)[1]
            if mapping.setdefault(new, name) != name:
                raise SystemExit(f'two classes map to {new}: {mapping[new]} and {name}')
            return new
    return None


def rename_descriptor(text, mapping):
    return DESCRIPTOR_CLASS.sub(lambda m: 'L' + (target(m.group(1), mapping) or m.group(1)) + m.group(2), text)


def remap_class(data, mapping):
    """The class file with its game references renamed; everything after the constant pool unchanged."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        raise SystemExit('not a class file')
    count = struct.unpack('>H', data[8:10])[0]
    entries, offset, index = {}, 10, 1
    while index < count:
        tag = data[offset]
        if tag == 1:
            length = struct.unpack('>H', data[offset + 1:offset + 3])[0]
            entries[index] = (tag, data[offset + 3:offset + 3 + length])
            offset += 3 + length
        elif tag in SIZES:
            entries[index] = (tag, data[offset + 1:offset + 1 + SIZES[tag]])
            offset += 1 + SIZES[tag]
        else:
            raise SystemExit(f'unknown constant tag {tag}')
        index += 2 if tag in (5, 6) else 1
    class_names = {struct.unpack('>H', body[:2])[0] for tag, body in entries.values() if tag == 7}
    literals = {struct.unpack('>H', body[:2])[0] for tag, body in entries.values() if tag == 8}
    pool = bytearray()
    for i in sorted(entries):
        tag, body = entries[i]
        if tag == 1 and i not in literals:
            text = body.decode('utf-8', 'surrogatepass')
            if i in class_names and not text.startswith('['):
                new = target(text, mapping) or text
            else:
                new = rename_descriptor(text, mapping)
            if new != text:
                body = new.encode('utf-8', 'surrogatepass')
        elif tag == 1 and i in class_names:
            raise SystemExit('a constant is both a string literal and a class name')
        pool += bytes([tag]) + (struct.pack('>H', len(body)) if tag == 1 else b'') + body
    return data[:10] + bytes(pool) + data[offset:]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    mapping = {}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.jar) as source, zipfile.ZipFile(args.out, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in source.infolist():
            data = source.read(info)
            if info.filename.endswith('.class'):
                data = remap_class(data, mapping)
            copy = zipfile.ZipInfo(info.filename, info.date_time)
            copy.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(copy, data)
    for new, old in sorted(mapping.items(), key=lambda item: item[1]):
        print(f'{old} -> {new}')
    for path in (args.jar, args.out):
        print(f'SHA-256 {path.name}: {hashlib.sha256(path.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
