# M1 design: the two fixtures inside Horizon

Plan for [issue #2](https://github.com/Dynamitecrown/horizon/issues/2), built on the
[M0 spike](m0-spike.md). Nothing here is implemented yet.

## What the fixtures need from the game

`tools/bridge_surface.py` lists every class, method and field a jar's constant pools name outside
the JDK and the mod's own packages, with exact descriptors. It reads the jar with the JDK's
`javap`; nothing is decompiled.

```powershell
python tools/bridge_surface.py --jdk $env:JAVA_HOME --jar .local/bridge-fixture/horizon-bridge-fixture-0.1.0.jar
```

Both fixtures need **the same surface: 38 classes, 53 members**. The pulse fixture's list is
identical to the counter's, so one adapter serves both, and a fixture-specific shortcut would show up
as a failing fixture.

| Area | Classes | What the fixture does with them |
| --- | --- | --- |
| Loader (`net/neoforged`) | `@Mod`, `IEventBus`, `NeoForge.EVENT_BUS`, `DeferredRegister` (+ `Blocks`, `Items`), `DeferredBlock`, `RegisterCommandsEvent` | Construct the mod with an event bus; register one block, its item and one block entity type; listen for command registration |
| Blocks (`net/minecraft/world/level/block`) | `Block`, `EntityBlock`, `BlockBehaviour$Properties` (`strength`), `state/BlockState` | Subclass `Block`; override `newBlockEntity`, `getTicker`, `useWithoutItem` |
| Block entities | `BlockEntity` (constructor, `saveAdditional`, `loadAdditional`, `loadStatic`, `saveWithFullMetadata`, `getBlockPos`, `getBlockState`, `setChanged`), `BlockEntityType` (+ `BlockEntitySupplier`), `BlockEntityTicker` | Hold the state, tick it, save it |
| Saving (`net/minecraft/world/level/storage`) | `ValueInput` (`getIntOr`, `getLongOr`, `getBooleanOr`), `ValueOutput` (`putInt`, `putLong`, `putBoolean`) | The saved fields |
| World and players | `ServerLevel` (`getBlockEntity`, `registryAccess`), `Level`, `BlockPos`, `Player.sendSystemMessage`, `Component.literal`, `InteractionResult.SUCCESS`, `BlockHitResult` | Find the entity, reply to the player |
| Commands | Brigadier (`com/mojang/brigadier`), `Commands`, `CommandSourceStack`, `BlockPosArgument` | The `/bridgefixture` and `/bridgepulse` test commands |
| Logging | `LogUtils`, `org/slf4j/Logger` | Trace lines |

## Approach: our own stand-ins, mapped onto Horizon

The bridge supplies its own Java classes for exactly this surface, written from Horizon's behaviour
and the descriptors above, never from Minecraft's or NeoForge's implementations. Each member does
the Horizon thing through a `native` method implemented in Rust (as `HostProbe` does in the spike).

Where they live: **our classes stay under `dev/horizonlab/bridge/host/`** (the only place
`tools/check_public_tree.py` allows our Java). At preparation time, the mod's own class files get
their references renamed from the game's names to ours (constant-pool class names only; method
bodies, and so the gameplay algorithm, are untouched). That renaming is the "generated adapter
transformation" issue #2 allows. It is recorded with its own hash next to the unchanged input jar's hash.

Brigadier and SLF4J are open-source libraries under MIT, so the bridge can load the real ones
instead of writing stand-ins. Check their licences when adding them.

## Where it plugs into Horizon

All three ways to host a world (single player's server, LAN host, dedicated server) run the same
`Game` in `horizon-client`, so one integration covers them.

| Need | Horizon today | Change |
| --- | --- | --- |
| Register the block and item | Content mods (API 0.2) declare blocks from vanilla templates in `data/<id>/horizon/block/*.json`; `horizon-port-pack` already writes those from a jar plus the oracle's facts | Reuse: prepare the fixture jar as a content mod; take the template and properties from the oracle export. `DeferredRegister` stand-ins then only check that registrations match. |
| Block entity data | `BlockEntity` is a closed enum of vanilla kinds (`horizon-client/src/chunk/block_entity.rs`) | Add a `Bridged` variant: mod id, type id, and the Java object's handle |
| Ticking | `tick_block_entities` matches on that enum (`horizon-client/src/game/block_entity_ticks.rs`) | A `Bridged` branch calls the ticker the mod's `getTicker` returned |
| Saving and loading | `horizon-client-core/src/block_entity_nbt.rs` | `saveAdditional`/`loadAdditional` through `ValueOutput`/`ValueInput` stand-ins that read and write Horizon's own NBT `Compound` |
| Right-click | The block use path in `game.rs` (`use_item_on`) | Call the block's `useWithoutItem` for bridged blocks |
| Commands | Server mods' commands join players' command trees (`game/server_mods.rs`) | Later: the fixtures' commands. Until then the trace is read in Rust, and is compared with the NeoForge trace the same way. |
| Off by default | Mods are behind the `mods` feature | A `bridge` cargo feature; no JVM starts unless a bridged mod is enabled |

## Order of work

Each step has its check:

1. **Loader stand-ins**: construct the fixture's `@Mod` class with our event bus, and collect the
   registrations. Check that both fixtures register one block, one item and one block entity type, with the expected ids.
2. **Block entity, outside the game**: create, tick, save and load a fixture entity through the
   stand-ins from a Rust test (the spike's harness, moved to the game's classes). Check against
   both fixtures' NeoForge traces.
3. **In `Game`**: the `Bridged` variant, ticking, saving and the right-click path, behind the
   `bridge` feature. Check the issue #2 scenario: place, 3 interactions, 100 ticks, save, quit,
   reopen, continue. Use scratch homes (`AGENTS.md`).
4. **Failures**: an unsupported member throws a named error, and the save stays intact. Without the
   feature, the game is unchanged (run `tools/pr_ready.py`).

## Open questions

- Loading a world saved by NeoForge with these blocks: how does Horizon treat an unknown block
  entity id today? `block_entity_nbt::overlay` suggests it keeps unknown data; check before step 3.
- Thread ownership: is the JVM attached to the server thread only, or also to the in-frame path
  (`docs/mods.md` mentions "in-frame" single player)? The spike showed a second thread works.
- Pinning: does the JVM need to be bundled, or is the JDK 25 that `horizon-prepare` already
  requires enough for players?
