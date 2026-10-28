import unittest

from tools import bridge_surface

JAVAP = """\
   #7 = Methodref          #8.#9          // net/minecraft/world/level/block/entity/BlockEntity."<init>":(Lnet/minecraft/core/BlockPos;)V
   #8 = Class              #10            // net/minecraft/world/level/block/entity/BlockEntity
  #12 = InterfaceMethodref #13.#14        // net/neoforged/bus/api/IEventBus.addListener:(Ljava/util/function/Consumer;)V
  #15 = Fieldref           #16.#17        // dev/horizonlab/bridge/BridgeFixture.LOGGER:Lorg/slf4j/Logger;
  #18 = Methodref          #19.#20        // java/lang/Math.addExact:(II)I
  #21 = Class              #22            // "[Lnet/minecraft/world/level/block/Block;"
  #23 = Utf8               net/minecraft/NotAReference
"""


class BridgeSurfaceTest(unittest.TestCase):
    def test_keeps_only_external_references(self):
        found = bridge_surface.parse(JAVAP, ('dev/horizonlab',))
        self.assertEqual(found['classes'], {'net/minecraft/world/level/block/entity/BlockEntity'})
        self.assertEqual(found['members'], {
            'net/minecraft/world/level/block/entity/BlockEntity.<init>:(Lnet/minecraft/core/BlockPos;)V',
            'net/neoforged/bus/api/IEventBus.addListener:(Ljava/util/function/Consumer;)V',
        })


if __name__ == '__main__':
    unittest.main()
