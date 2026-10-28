import json
from pathlib import Path
import tempfile
import unittest

from tools import bridge_mod


class BridgeModTest(unittest.TestCase):
    def test_writes_content_mod_and_jar(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = Path(tmp) / 'pulse.jar'
            jar.write_bytes(b'PK')
            out = Path(tmp) / 'mod'
            bridge_mod.write(out, 'horizon_bridge_pulse', ['horizon_bridge_pulse:pulse'], 'minecraft:stone', 1.5, jar)
            manifest = (out / 'horizon-mod.toml').read_text()
            self.assertIn('id = "horizon_bridge_pulse"', manifest)
            self.assertIn('capabilities = ["registries"]', manifest)
            block = json.loads((out / 'data/horizon_bridge_pulse/horizon/block/pulse.json').read_text())
            self.assertEqual(block, {'template': 'minecraft:stone', 'properties': {'hardness': 1.5}, 'item': True})
            self.assertEqual((out / 'bridge.jar').read_bytes(), b'PK')

    def test_refuses_another_namespace(self):
        with tempfile.TemporaryDirectory() as tmp, self.assertRaises(SystemExit):
            bridge_mod.write(Path(tmp) / 'mod', 'a', ['b:block'], 'minecraft:stone', 1.5, Path(tmp) / 'none.jar')


if __name__ == '__main__':
    unittest.main()
