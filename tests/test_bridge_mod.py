import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from tools import bridge_mod


class BridgeModTest(unittest.TestCase):
    def test_writes_content_mod_and_jar(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'pulse.jar'
            with zipfile.ZipFile(jar, 'w') as archive:
                archive.writestr('assets/horizon_bridge_pulse/items/pulse.json', '{}')
                archive.writestr('data/horizon_bridge_pulse/loot_table/blocks/pulse.json', '{}')
                archive.writestr('assets/minecraft/models/block/stone.json', '{}')
                archive.writestr('dev/horizonlab/bridge/pulse/PulseBlock.class', b'')
            out = Path(tmp) / 'mod'
            bridge_mod.write(out, 'horizon_bridge_pulse', ['horizon_bridge_pulse:pulse'], 'minecraft:stone', 1.5, jar)
            manifest = (out / 'horizon-mod.toml').read_text()
            self.assertIn('id = "horizon_bridge_pulse"', manifest)
            self.assertIn('capabilities = ["registries"]', manifest)
            block = json.loads((out / 'data/horizon_bridge_pulse/horizon/block/pulse.json').read_text())
            self.assertEqual(block, {'template': 'minecraft:stone', 'properties': {'hardness': 1.5}, 'item': True})
            self.assertEqual((out / 'bridge.jar').read_bytes(), jar.read_bytes())
            # The jar's own namespace is copied; another namespace and the classes are not.
            self.assertTrue((out / 'assets/horizon_bridge_pulse/items/pulse.json').is_file())
            self.assertTrue((out / 'data/horizon_bridge_pulse/loot_table/blocks/pulse.json').is_file())
            self.assertFalse((out / 'assets/minecraft').exists())
            self.assertFalse((out / 'dev').exists())

    def test_refuses_another_namespace(self):
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(SystemExit):
            bridge_mod.write(Path(tmp) / 'mod', 'a', ['b:block'], 'minecraft:stone', 1.5, Path(tmp) / 'none.jar')


if __name__ == '__main__':
    unittest.main()
