#!/usr/bin/env python3
"""Make a bridged mod folder for Horizon's game (docs/bridge/m1-design.md, step 3).

    bridge_mod.py --jar REMAPPED.jar --mod ID --block ID [--block ID ...] --template minecraft:BLOCK
                  [--hardness N] --out DIR

The folder is a content mod (API 0.2, `registries`) declaring each block from a vanilla template,
with the jar's own `assets/<id>/` and `data/<id>/` (its models, item definitions and names), plus `bridge.jar`, the renamed jar (`bridge_remap.py`) whose block entities, tickers, right-clicks
and commands the bridge runs. Pass it to the game with `--dev-mod DIR` or put it in `horizon-mods/`.
"""
import argparse
import json
from pathlib import Path
import shutil
import zipfile


def write(out, mod_id, blocks, template, hardness, jar):
    out = Path(out)
    out.mkdir(parents=True, exist_ok=True)
    (out / 'horizon-mod.toml').write_text(
        f'id = "{mod_id}"\nname = "{mod_id} (bridged)"\nversion = "0.1.0"\napi = "0.2"\n'
        'capabilities = ["registries"]\n', encoding='utf-8')
    for block in blocks:
        namespace, name = block.split(':', 1)
        if namespace != mod_id:
            raise SystemExit(f'{block}: blocks go in the mod\'s own namespace, {mod_id}')
        folder = out / 'data' / mod_id / 'horizon' / 'block'
        folder.mkdir(parents=True, exist_ok=True)
        declaration = {'template': template, 'properties': {'hardness': hardness}, 'item': True}
        (folder / f'{name}.json').write_text(json.dumps(declaration, indent=2) + '\n', encoding='utf-8')
    # The jar's own look and data (blockstates, models, item definitions, names, loot tables), in the
    # mod's namespace only: the content fence (docs/mods.md).
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            parts = entry.split('/')
            if parts[:2] not in (['assets', mod_id], ['data', mod_id]) or entry.endswith('/') or '..' in parts:
                continue
            target = out.joinpath(*parts)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(entry))
    shutil.copyfile(jar, out / 'bridge.jar')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jar', required=True)
    parser.add_argument('--mod', required=True)
    parser.add_argument('--block', action='append', required=True)
    parser.add_argument('--template', required=True)
    parser.add_argument('--hardness', type=float, default=1.5)
    parser.add_argument('--out', required=True)
    a = parser.parse_args()
    write(a.out, a.mod, a.block, a.template, a.hardness, a.jar)
    print(f'Wrote {a.out}')


if __name__ == '__main__':
    main()
