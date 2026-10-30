#!/usr/bin/env python3
"""List the game and loader API a mod jar calls (docs/bridge/m1-design.md): every class, method and
field its constant pools name outside its own packages, with exact descriptors. Read with the JDK's
javap, from the jar alone; nothing is decompiled.

    bridge_surface.py --jdk JDK --jar MOD.jar [--own dev/horizonlab]
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

REF = re.compile(r'= (Class|Fieldref|Methodref|InterfaceMethodref)\s+\S+\s+// (.+)$')
JDK_PACKAGES = ('java/', 'javax/', 'jdk/', 'sun/')


def surface(javap, jar, own):
    """{'classes': set, 'members': set} of references outside the JDK and the mod's own packages."""
    with tempfile.TemporaryDirectory() as temporary, zipfile.ZipFile(jar) as archive:
        names = [n for n in archive.namelist() if n.endswith('.class')]
        for name in names:
            target = (Path(temporary) / name).resolve()
            if not target.is_relative_to(Path(temporary).resolve()):
                raise SystemExit(f'unsafe path in jar: {name}')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
        if not names:
            return {'classes': set(), 'members': set()}
        out = subprocess.run([str(javap), '-v', '-p', *(str(Path(temporary) / n) for n in names)],
                             capture_output=True, text=True, check=True).stdout
    return parse(out, own)


def parse(javap_output, own):
    """The external references in `javap -v` output."""
    found = {'classes': set(), 'members': set()}
    for line in javap_output.splitlines():
        match = REF.search(line)
        if not match:
            continue
        kind, target = match.groups()
        target = target.strip().replace('"', '')
        owner = target.split('.', 1)[0]
        if owner.startswith('[') or owner.startswith(JDK_PACKAGES) or any(owner.startswith(p) for p in own):
            continue
        found['classes' if kind == 'Class' else 'members'].add(target)
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--own', action='append', default=None,
                        help='package prefix that is the mod\'s own (repeatable; default dev/horizonlab)')
    args = parser.parse_args()
    javap = args.jdk / 'bin' / ('javap.exe' if os.name == 'nt' else 'javap')
    found = surface(javap, args.jar, tuple(args.own or ['dev/horizonlab']))
    for name in sorted(found['classes'] | found['members']):
        print(name)
    by_root = {}
    for name in found['classes']:
        root = '/'.join(name.split('/')[:2])
        by_root[root] = by_root.get(root, 0) + 1
    print(f'# {len(found["classes"])} classes, {len(found["members"])} members: '
          + ', '.join(f'{root} {count}' for root, count in sorted(by_root.items())))


if __name__ == '__main__':
    main()
