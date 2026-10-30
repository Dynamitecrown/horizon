import struct
import unittest

from tools import bridge_remap

# Access flags, this/super class, and empty interfaces, fields, methods and attributes.
TAIL = b'\x00\x21\x00\x02\x00\x00' + b'\x00\x00' * 4


def utf8(text):
    data = text.encode()
    return b'\x01' + struct.pack('>H', len(data)) + data


def class_file(pool, tail=TAIL):
    """A class file: header, the given constants (a long or double takes two slots), then `tail`."""
    slots = sum(2 if entry[0] in (5, 6) else 1 for entry in pool)
    return b'\xca\xfe\xba\xbe\x00\x00\x00\x45' + struct.pack('>H', slots + 1) + b''.join(pool) + tail


class BridgeRemapTest(unittest.TestCase):
    def test_renames_names_and_descriptors_but_not_literals(self):
        pool = [
            utf8('net/minecraft/world/level/block/Block'),               # 1, a class name
            b'\x07' + struct.pack('>H', 1),                              # 2, Class -> 1
            utf8('(Lnet/minecraft/core/BlockPos;I)Lorg/slf4j/Logger;'),  # 3, a descriptor
            utf8('net/minecraft/core/BlockPos'),                         # 4, a string literal
            b'\x08' + struct.pack('>H', 4),                              # 5, String -> 4
            utf8('[Lnet/minecraft/world/level/block/Block;'),             # 6, an array class name
            b'\x07' + struct.pack('>H', 6),                              # 7, Class -> 6
            b'\x05' + struct.pack('>q', 7),                              # 8 and 9, a long
        ]
        mapping = {}
        out = bridge_remap.remap_class(class_file(pool), mapping)
        self.assertIn(utf8('dev/horizonlab/bridge/host/mc/Block') + b'\x07', out)
        self.assertIn(utf8('(Ldev/horizonlab/bridge/host/mc/BlockPos;I)Ldev/horizonlab/bridge/host/slf4j/Logger;'), out)
        self.assertIn(utf8('[Ldev/horizonlab/bridge/host/mc/Block;'), out)
        self.assertIn(utf8('net/minecraft/core/BlockPos') + b'\x08', out)
        self.assertTrue(out.endswith(b'\x05' + struct.pack('>q', 7) + TAIL))
        self.assertEqual(mapping['dev/horizonlab/bridge/host/mc/Block'], 'net/minecraft/world/level/block/Block')

    def test_refuses_two_classes_with_one_stand_in(self):
        pool = [utf8('(Lnet/minecraft/a/Thing;Lnet/minecraft/b/Thing;)V')]
        with self.assertRaises(SystemExit):
            bridge_remap.remap_class(class_file(pool), {})


if __name__ == '__main__':
    unittest.main()
