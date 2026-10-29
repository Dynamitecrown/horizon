# M1 design: the two fixtures inside Horizon

Plan for [issue #2](https://github.com/Dynamitecrown/horizon/issues/2), built on the
[M0 spike](m0-spike.md). **Steps 1 and 2 are done** ([results](#results-of-steps-1-and-2-2026-10-28)),
in a host world of the bridge's own. **Steps 3 and 4 are done** too ([step 3](#results-of-step-3-2026-10-28),
[step 4](#results-of-step-4-2026-10-28)): both fixtures run in Horizon's own `Game`, across a real
save, quit and reopen, and a mod that needs a member the bridge lacks fails cleanly.

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

- ~~Loading a world saved by NeoForge with these blocks~~: Horizon keeps a block entity whose id it
  doesn't know as loaded (`ChunkExtras.block_entities`) and writes it back unchanged. Without the
  bridge, such a block is inert data, never dropped.
- ~~Thread ownership~~: whichever thread simulates the world calls in (the server thread, or the
  window's in the in-frame mode), and chunk saving may call from another; each thread attaches
  itself, and every `HostWorld` entry point is `synchronized`.
- Pinning: does the JVM need to be bundled, or is the JDK 25 that `horizon-prepare` already
  requires enough for players?

## Results of steps 1 and 2 (2026-10-28)

Pieces, all ours:

| Piece | Where |
| --- | --- |
| Stand-ins for the 55 names the fixtures use (the 38 classes of the surface, plus types that only appear in descriptors) | `mods/horizon/src/bridge-host/java/dev/horizonlab/bridge/host/{mc,neo,brig,mojang,slf4j}/` |
| Loader: finds the `@Mod` class, constructs it with a mod bus, runs deferred registrations in registry order | `.../host/BridgeLoader.java` |
| Host world for Rust: place, command, tick, right-click, save, restore, log | `.../host/HostWorld.java` |
| Reference rename (constant pool only) | `tools/bridge_remap.py`, tested by `tests/test_bridge_remap.py` |
| Rust driver | `crates/horizon-bridge-spike/src/bin/world.rs` (`horizon-bridge-world`) |

Every tick is driven from Rust. The JVM runs with `-Xverify:all`. The Rust driver holds no
fixture logic: each fixture's expected values come in as arguments.

| Check | Counter | Pulse |
| --- | --- | --- |
| Renamed jar SHA-256 (input jar unchanged) | `f9a90d8c…7926` (from `106bd413…`) | `c4451ea6…d5b0` (from `1708431c…`) |
| Gameplay class unchanged by the rename | `CounterState.class` byte-identical | (same rule) |
| Registrations | block, item, block entity type `horizon_bridge_fixture:counter` | same for `horizon_bridge_pulse:pulse` |
| 3 interactions by command, then 100 ticks | 100 trace lines, **identical to the NeoForge reference** | 100 trace lines, **identical to the NeoForge reference** |
| Pause, 20 ticks | 19 / 100, paused | 210 / 100, paused |
| Save to Horizon's form, restore | `{"id":"horizon_bridge_fixture:counter","fixture_schema":1,"value":19,"ticks":100L,...}` restored equal | equal |
| The mod's own `verify` (its save/load codecs) | passes | passes |
| Resume, 20 ticks | 21 / 120 | 255 / 120 |
| Right-click (`useWithoutItem`) | reply shows 1 interaction | same |
| Bad command | `CommandSyntaxException: Unknown or incomplete command` | same |
| Cost | load + start 118–530 ms (first run after a build is slowest); 74–88 µs per tick (100 interpreted ticks, `-Xverify:all`) | |

Run it:

```powershell
python tools/bridge_remap.py --jar .local/bridge-fixture/horizon-bridge-fixture-0.1.0.jar --out .local/bridge-remap/counter.jar
cargo run --release --manifest-path crates/horizon-bridge-spike/Cargo.toml --bin horizon-bridge-world -- `
  --jar .local/bridge-remap/counter.jar --mod horizon_bridge_fixture --block horizon_bridge_fixture:counter `
  --command bridgefixture --reference <fixture-reference-trace.jsonl> `
  --expect-100 '{"schema":1,"value":19,"ticks":100,"interactions":3,"paused":false}' `
  --expect-paused '{"schema":1,"value":19,"ticks":100,"interactions":3,"paused":true}' `
  --expect-resumed '{"schema":1,"value":21,"ticks":120,"interactions":3,"paused":false}'
```

For the pulse fixture use `pulse.jar`, `horizon_bridge_pulse`, `horizon_bridge_pulse:pulse`,
`bridgepulse` and the values in its README. Set `JAVA_HOME`.

**Shortcuts** (each marked `shortcut:` in the code): every command source is an operator, every
position counts as loaded, and every game-bus listener gets every event. Relative coordinates are not parsed.

Step 3 below puts the block in Horizon's world, chunks and saves.

## Results of step 3 (2026-10-28)

The fixtures run in Horizon's game behind the `bridge` cargo feature (off by default; without it,
`horizon_client::bridge` starts nothing and no block is bridged).

| Piece | Where |
| --- | --- |
| A bridged mod: a content mod folder (its blocks, from a vanilla template) plus `bridge.jar`, the renamed jar | `tools/bridge_mod.py`, tested by `tests/test_bridge_mod.py` |
| JVM start (once, with every bridged mod's jar), the saved form, sync, tick, right-click, commands | `crates/horizon-client/src/bridge.rs` |
| `BlockEntity::Bridged`: block, type id, and where its state is (fresh, saved, live in the JVM) | `crates/horizon-client/src/chunk/block_entity.rs` |
| Ticks, empty-hand right-clicks, the mods' commands, chunks marked changed | `crates/horizon-client/src/game/bridged.rs`, hooked in `block_entity_ticks.rs`, `game.rs` (`use_hand`), `commands.rs` (`run_command`) |
| Saving and loading: the JVM's own fields in the chunk's block entity compound | `save.rs` (`encode_block_entity`, `decode_block_entity`) |
| Position-keyed entry points, all `synchronized` | `HostWorld.java` (`typeOf`, `load`, `tickAt`, `handles`) |

Only the world that simulates calls into the JVM; a client's copy of a bridged entity stays as decoded,
so an update packet never overwrites the server's state.

Scenario (issue #2), driven with `horizon-ctl` in scratch homes, two processes, Windows x64, JDK 25.0.5:
`/tick freeze`, `/setblock` beside the player, `reset`, **three real empty-hand right-clicks**
(`horizon-ctl interact`), `trace ... 100`, `/tick step 100`, pause, 20 ticks, `verify`, quit; reopen,
`state`, resume, 20 ticks, `verify`.

| Check | Counter | Pulse |
| --- | --- | --- |
| Registered at start | block, item, block entity type | same |
| 3 right-clicks | 9 / 0 / 3 | 0 / 0 / 3, charged |
| 100-tick trace against NeoForge's | 100 of 100 identical | 100 of 100 identical |
| Paused 20 ticks, `verify` | 19 / 100 | 210 / 100 |
| After quit and reopen | 19 / 100, paused | 210 / 100, paused |
| Resume, 20 ticks, `verify` | 21 / 120 | 255 / 120 |

**Shortcuts** (marked `shortcut:` in the code): the JVM keys block entities by position only (one
dimension); every bridged block counts as ticking and marks its chunk changed each tick; booleans are
the only bytes in the saved form; a failed JVM save loses that entity's fields from the save.
**Not yet:** a broken bridged block leaves its JVM entity until the position is reused; the in-frame mode,
LAN and dedicated servers are not tested.

`horizon-ctl launch --game` runs the game in the current folder, so these worlds were saved under the
checkout's `saves/` (ignored), not the scratch `HORIZON_HOME`, which only held logs and settings. The
passing pulse run reused the world of a first attempt whose right-clicks missed (the block was out of
reach); `reset` and a fresh placement keep its numbers independent of that attempt.

## Results of step 4 (2026-10-28)

A third fixture, [fault](../../mods/horizon/src/neoforge-fixture-fault/README.md), built against
NeoForge 26.3.0.99 like the others, calls `Level.getGameTime()` on its fifth tick; the stand-in `Level`
has no such member, so the JVM throws `NoSuchMethodError` inside the mod's own ticker.

| What fails | What the bridge does |
| --- | --- |
| A tick, right-click or load throws (any Java error) | That entity stops (`BridgedSync::Failed`): no more JVM calls for it; one error in the log, naming the member when the bridge lacks one (`not supported by the bridge yet: ... 'long mc.Level.getGameTime()'`) |
| Its save | Keeps the form the JVM saved right after the failure (or the form it was loaded with) |
| Reopening the world | It loads from that form and runs again |
| The mod's own `saveAdditional` throws | Shortcut: that entity's fields are left out of the save; the chunk and other entities are kept |

In the game (horizon-ctl, fresh world `BridgeStep4b`): a pulse and a fault block side by side, 20 ticks:
one error, logged once; the pulse block goes on (2 / 20); quit and reopen: no error, the fault block
resumes from its saved 5 ticks (it and the pulse both gain the same 62 ticks before the freeze) with
`game_time` -1, and its right-click reply reaches the player's chat. Unit tests: `describe`, a failed
entity's save, the saved-form round trip.

Found on the way: in single player the world runs on its server thread, so a bridged right-click's
reply must go to the acting player as a packet (`guest_packets`), as vanilla's `sendSystemMessage`
does; step 3 had sent it to the server's own chat, which nobody sees.

Without the `bridge` feature the game is unchanged: no JVM, `bridge::for_block` and `decode` return
None, so no `Bridged` entity exists (`pr_ready.py`, below).
