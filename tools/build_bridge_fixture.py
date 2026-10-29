#!/usr/bin/env python3
"""Build/test an original NeoForge 26.3 bridge fixture using the player's installed Prism libraries; no downloads."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
# name: (source folder, jar, logic test main class, class the jar must hold)
FIXTURES = {
    'counter': ('neoforge-fixture', 'horizon-bridge-fixture-0.1.0.jar', 'dev.horizonlab.bridge.CounterStateTest',
                'dev/horizonlab/bridge/CounterState.class'),
    'pulse': ('neoforge-fixture-pulse', 'horizon-bridge-pulse-0.1.0.jar', 'dev.horizonlab.bridge.pulse.PulseStateTest',
              'dev/horizonlab/bridge/pulse/PulseState.class'),
    'fault': ('neoforge-fixture-fault', 'horizon-bridge-fault-0.1.0.jar', 'dev.horizonlab.bridge.fault.FaultStateTest',
              'dev/horizonlab/bridge/fault/FaultState.class'),
}
MINECRAFT, NEOFORGE = '26.3', '26.3.0.99'


def library_path(library):
    artifact = library.get('downloads', {}).get('artifact', {})
    if artifact.get('path'):
        return artifact['path']
    group, name, version, *classifier = library['name'].split(':')
    suffix = '-' + classifier[0] if classifier else ''
    return f'{group.replace(".", "/")}/{name}/{version}/{name}-{version}{suffix}.jar'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prism-root', type=Path, required=True)
    parser.add_argument('--jdk', type=Path, required=True)
    parser.add_argument('--fixture', choices=FIXTURES, default='counter')
    parser.add_argument('--out', type=Path, help='default: .local/bridge-fixture/<jar name of the fixture>')
    args = parser.parse_args()
    folder, jar_name, test_main, required = FIXTURES[args.fixture]
    source = ROOT / 'mods/horizon/src' / folder
    args.out = args.out or ROOT / '.local/bridge-fixture' / jar_name
    exe = '.exe' if os.name == 'nt' else ''
    javac, java = (args.jdk / 'bin' / (name + exe) for name in ('javac', 'java'))
    version = subprocess.check_output([str(javac), '-version'], text=True).strip()
    if version.split()[1].split('.')[0] != '25':
        parser.error(f'JDK 25 required, got {version}')
    libs = args.prism_root / 'libraries'
    jars = [libs / f'net/neoforged/minecraft-client-patched/{NEOFORGE}/minecraft-client-patched-{NEOFORGE}.jar',
            libs / f'net/neoforged/neoforge/{NEOFORGE}/neoforge-{NEOFORGE}-universal.jar']
    for uid, release in [('net.minecraft', MINECRAFT), ('net.neoforged', NEOFORGE)]:
        metadata = json.loads((args.prism_root / 'meta' / uid / f'{release}.json').read_text(encoding='utf-8'))
        for library in metadata.get('libraries', []):
            path = (libs / library_path(library)).resolve()
            if not path.is_relative_to(libs.resolve()):
                parser.error('Library metadata points outside the Prism library directory')
            if path.is_file() and path not in jars:
                jars.append(path)
    if any(not path.is_file() for path in jars[:2]):
        parser.error('Launch Minecraft 26.3 / NeoForge 26.3.0.99 once in Prism first (patched client missing).')
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='build-', dir=args.out.parent) as temporary:
        work = Path(temporary)
        classes, tests = work / 'classes', work / 'tests'
        classes.mkdir()
        tests.mkdir()
        cp = os.pathsep.join(map(str, jars))
        def compile_to(destination, sources, classpath):
            arguments = ['--release', '25', '-proc:none', '-Xlint:all,-processing,-classfile', '-Werror', '-cp', classpath,
                         '-d', str(destination), *map(str, sources)]
            argfile = work / 'javac.args'
            argfile.write_text('\n'.join('"' + str(arg).replace('\\', '/').replace('"', '\\"') + '"'
                                         for arg in arguments), encoding='utf-8')
            subprocess.run([str(javac), '@' + str(argfile)], check=True)
        compile_to(classes, sorted((source / 'java').rglob('*.java')), cp)
        compile_to(tests, sorted((source / 'test/java').rglob('*.java')), str(classes))
        subprocess.run([str(java), '-ea', '-cp', os.pathsep.join(map(str, [classes, tests])),
                        test_main], check=True)
        candidate = work / 'fixture.jar'
        with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as jar:
            for base in [classes, source / 'resources']:
                for file in sorted(path for path in base.rglob('*') if path.is_file()):
                    info = zipfile.ZipInfo(file.relative_to(base).as_posix(), (2026, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    jar.writestr(info, file.read_bytes())
        with zipfile.ZipFile(candidate) as jar:
            assert required in jar.namelist()
            assert not any(name.startswith(('net/', 'com/', 'org/')) or test_main.rsplit('.', 1)[1] in name
                           for name in jar.namelist()), 'Only original fixture code/resources may be packaged'
        candidate.replace(args.out)
    print(f'{args.out}\nSHA-256: {hashlib.sha256(args.out.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    main()
